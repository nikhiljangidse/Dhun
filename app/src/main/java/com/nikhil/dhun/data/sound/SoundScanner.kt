package com.nikhil.dhun.data.sound

import android.content.Context
import com.nikhil.dhun.data.DuplicateGroup
import com.nikhil.dhun.data.Duplicates
import com.nikhil.dhun.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

sealed interface ScanState {
    data object Idle : ScanState
    data class Listening(val done: Int, val total: Int) : ScanState
    data class Matching(val progress: Float) : ScanState
    data class Done(val groups: List<DuplicateGroup>, val scanned: Int, val skipped: Int) : ScanState
}

/** Runs the "by sound" duplicate scan in the background (survives leaving the screen). */
object SoundScanner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    val isRunning: Boolean get() = job?.isActive == true

    fun start(context: Context, songs: List<Song>) {
        if (isRunning) return
        val app = context.applicationContext
        job = scope.launch {
            val cache = FingerprintStore.load(app)
            val result = ConcurrentHashMap<Long, IntArray>()
            val done = AtomicInteger(0)
            val skipped = AtomicInteger(0)
            val total = songs.size
            _state.value = ScanState.Listening(0, total)
            try {
                // Songs we already know go first (instant), new ones after.
                val ordered = songs.sortedBy { if (cache[it.id]?.size == it.sizeBytes) 0 else 1 }
                val workers = 3
                val parts = List(workers) { w -> ordered.filterIndexed { i, _ -> i % workers == w } }
                coroutineScope {
                    parts.forEach { part ->
                        launch {
                            for (s in part) {
                                ensureActive()
                                val cached = cache[s.id]
                                val fp = if (cached != null && cached.size == s.sizeBytes) {
                                    cached.frames
                                } else {
                                    Fingerprinter.fromSong(app, s)
                                }
                                if (fp != null) result[s.id] = fp else skipped.incrementAndGet()
                                val d = done.incrementAndGet()
                                if (d % 3 == 0 || d == total) _state.value = ScanState.Listening(d, total)
                            }
                        }
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    val bySong = songs.associateBy { it.id }
                    val merged = HashMap<Long, FingerprintStore.Entry>()
                    cache.forEach { (id, e) -> if (bySong[id]?.sizeBytes == e.size) merged[id] = e }
                    result.forEach { (id, fp) -> bySong[id]?.let { merged[id] = FingerprintStore.Entry(it.sizeBytes, fp) } }
                    FingerprintStore.save(app, merged)
                }
            }

            _state.value = ScanState.Matching(0f)
            val matches = SoundMatcher.find(result) { p -> _state.value = ScanState.Matching(p) }
            _state.value = ScanState.Done(group(songs, matches), result.size, skipped.get())
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = ScanState.Idle
    }

    private fun group(songs: List<Song>, matches: List<SoundMatcher.Match>): List<DuplicateGroup> {
        val parent = HashMap<Long, Long>()
        fun root(x: Long): Long {
            var r = x
            while (true) {
                val p = parent[r] ?: return r
                if (p == r) return r
                r = p
            }
        }
        val worst = HashMap<Long, Float>()
        for (m in matches) {
            val ra = root(m.a)
            val rb = root(m.b)
            parent.putIfAbsent(m.a, m.a)
            parent.putIfAbsent(m.b, m.b)
            if (ra != rb) parent[rb] = ra
        }
        val byId = songs.associateBy { it.id }
        val clusters = HashMap<Long, MutableList<Song>>()
        for (id in parent.keys) {
            val s = byId[id] ?: continue
            clusters.getOrPut(root(id)) { mutableListOf() }.add(s)
        }
        for (m in matches) {
            val r = root(m.a)
            worst[r] = maxOf(worst[r] ?: 0f, m.ber)
        }
        return clusters.entries
            .filter { it.value.size > 1 }
            .map { (r, list) ->
                val ber = worst[r] ?: 0f
                DuplicateGroup(Duplicates.bestFirst(list), matchPercent = ((1f - ber) * 100).toInt())
            }
            .sortedBy { it.keeper.title.lowercase() }
    }
}

/** Fingerprints saved on the phone so later scans only process new songs. */
object FingerprintStore {
    private const val FILE = "fingerprints_v1.bin"

    class Entry(val size: Long, val frames: IntArray)

    fun load(context: Context): HashMap<Long, Entry> {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return HashMap()
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(f))).use { input ->
                val n = input.readInt()
                val map = HashMap<Long, Entry>(n * 2)
                repeat(n) {
                    val id = input.readLong()
                    val size = input.readLong()
                    val len = input.readInt()
                    val arr = IntArray(len) { input.readInt() }
                    map[id] = Entry(size, arr)
                }
                map
            }
        } catch (e: Exception) {
            HashMap()
        }
    }

    fun save(context: Context, map: Map<Long, Entry>) {
        try {
            val tmp = File(context.filesDir, "$FILE.tmp")
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { out ->
                out.writeInt(map.size)
                map.forEach { (id, e) ->
                    out.writeLong(id)
                    out.writeLong(e.size)
                    out.writeInt(e.frames.size)
                    e.frames.forEach { out.writeInt(it) }
                }
            }
            tmp.renameTo(File(context.filesDir, FILE))
        } catch (e: Exception) {
            // cache is optional
        }
    }
}
