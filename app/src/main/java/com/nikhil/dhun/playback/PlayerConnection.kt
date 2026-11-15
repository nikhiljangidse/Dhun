package com.nikhil.dhun.playback

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.nikhil.dhun.data.MusicLibrary
import com.nikhil.dhun.data.PlaybackPrefs
import com.nikhil.dhun.data.Song
import com.nikhil.dhun.data.Source
import com.nikhil.dhun.data.toMediaItem

/** The UI's handle on the background player, with Compose-friendly state. */
object PlayerConnection {
    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private var restoreAttempted = false

    var connected by mutableStateOf(false)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var currentId by mutableStateOf<Long?>(null)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var queueIds by mutableStateOf<List<Long>>(emptyList())
        private set
    var currentIndex by mutableIntStateOf(0)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set

    val position: Long get() = controller?.currentPosition ?: 0L

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(player, full = events.contains(Player.EVENT_TIMELINE_CHANGED))
        }
    }

    fun connect(context: Context) {
        if (future != null) return
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val f = MediaController.Builder(app, token).buildAsync()
        future = f
        f.addListener({
            val c = try {
                f.get()
            } catch (e: Exception) {
                null
            }
            if (c == null) {
                future = null
                return@addListener
            }
            controller = c
            c.addListener(listener)
            sync(c, full = true)
            connected = true
        }, ContextCompat.getMainExecutor(app))
    }

    fun release() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
        connected = false
    }

    private fun sync(p: Player, full: Boolean) {
        isPlaying = p.isPlaying
        currentId = p.currentMediaItem?.mediaId?.toLongOrNull()
        val d = p.duration
        durationMs = if (d == C.TIME_UNSET || d < 0) 0L else d
        currentIndex = p.currentMediaItemIndex
        repeatMode = p.repeatMode
        if (full) {
            queueIds = (0 until p.mediaItemCount).mapNotNull { p.getMediaItemAt(it).mediaId.toLongOrNull() }
        }
    }

    /** Start playing [songs] from [source]. With shuffle, [start] plays first, then smart-shuffled rest. */
    fun play(context: Context, source: Source, songs: List<Song>, start: Song?, shuffle: Boolean) {
        val c = controller
        if (c == null) {
            connect(context)
            return
        }
        if (songs.isEmpty()) return
        PlaybackPrefs.setShuffle(context, shuffle)
        PlaybackPrefs.setSource(context, source, songs.map { it.id })

        val queue: List<Song>
        val startIndex: Int
        if (shuffle) {
            val rest = SmartShuffle.order(context, songs.filter { it.id != start?.id }, source.key)
            queue = listOfNotNull(start) + rest
            startIndex = 0
        } else {
            queue = songs
            startIndex = if (start == null) 0 else songs.indexOfFirst { it.id == start.id }.coerceAtLeast(0)
        }
        c.setMediaItems(queue.map { it.toMediaItem() }, startIndex, 0L)
        c.prepare()
        c.play()
    }

    /** Flip shuffle and re-order everything after the current song (current keeps playing). */
    fun toggleShuffle(context: Context) {
        val on = !PlaybackPrefs.shuffle
        PlaybackPrefs.setShuffle(context, on)
        val c = controller ?: return
        val src = PlaybackPrefs.source ?: return
        if (c.mediaItemCount == 0) return
        val byId = MusicLibrary.byId()
        val sourceSongs = PlaybackPrefs.sourceSongIds.mapNotNull { byId[it] }
        if (sourceSongs.isEmpty()) return
        val curId = c.currentMediaItem?.mediaId?.toLongOrNull()
        val cur = c.currentMediaItemIndex

        // Keep only the current item, then rebuild around it.
        if (cur + 1 < c.mediaItemCount) c.removeMediaItems(cur + 1, c.mediaItemCount)
        if (cur > 0) c.removeMediaItems(0, cur)

        if (on) {
            val rest = SmartShuffle.order(context, sourceSongs.filter { it.id != curId }, src.key)
            c.addMediaItems(rest.map { it.toMediaItem() })
        } else {
            val i = sourceSongs.indexOfFirst { it.id == curId }
            if (i >= 0) {
                val before = sourceSongs.subList(0, i)
                val after = sourceSongs.subList(i + 1, sourceSongs.size)
                if (after.isNotEmpty()) c.addMediaItems(after.map { it.toMediaItem() })
                if (before.isNotEmpty()) c.addMediaItems(0, before.map { it.toMediaItem() })
            } else {
                c.addMediaItems(sourceSongs.map { it.toMediaItem() })
            }
        }
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(c.currentMediaItemIndex, 0L)
            c.play()
        }
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
    }

    fun seekToIndex(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, 0L)
            c.play()
        }
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            c.setMediaItems(listOf(song.toMediaItem()))
            c.prepare()
            c.play()
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
        }
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            c.setMediaItems(listOf(song.toMediaItem()))
            c.prepare()
            c.play()
        } else {
            c.addMediaItem(song.toMediaItem())
        }
    }

    /** Drop deleted songs from the queue. */
    fun removeIds(ids: Set<Long>) {
        val c = controller ?: return
        for (i in c.mediaItemCount - 1 downTo 0) {
            val id = c.getMediaItemAt(i).mediaId.toLongOrNull()
            if (id != null && id in ids) c.removeMediaItem(i)
        }
    }

    /** After a fresh app start, load the last queue (paused) so the user can continue. */
    fun restoreIfIdle(context: Context) {
        if (restoreAttempted) return
        val c = controller ?: return
        restoreAttempted = true
        if (c.mediaItemCount > 0) return
        val s = PlaybackPrefs.loadSession(context) ?: return
        val byId = MusicLibrary.byId()
        val songs = s.queueIds.mapNotNull { byId[it] }
        if (songs.isEmpty()) return
        val curId = s.queueIds.getOrNull(s.index)
        val idx = songs.indexOfFirst { it.id == curId }.coerceAtLeast(0)
        val pos = if (songs[idx].id == curId) s.positionMs else 0L
        c.setMediaItems(songs.map { it.toMediaItem() }, idx, pos)
        c.prepare()
    }
}
