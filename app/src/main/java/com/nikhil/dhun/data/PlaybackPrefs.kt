package com.nikhil.dhun.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray

data class Session(val queueIds: List<Long>, val index: Int, val positionMs: Long)

/** Shuffle switch, sort order, what is playing from, and the last queue (for resume). */
object PlaybackPrefs {
    private const val PREFS = "dhun_playback"
    private var prefs: SharedPreferences? = null

    var shuffle by mutableStateOf(true)
        private set
    var sort by mutableStateOf(SortMode.TITLE)
        private set
    var source by mutableStateOf<Source?>(null)
        private set
    var sourceSongIds: List<Long> = emptyList()
        private set

    /** Now Playing shows a spinning record instead of the square cover. */
    var vinylMode by mutableStateOf(false)
        private set

    /** Crossfade between songs, in seconds (0 = off). */
    var crossfadeSec by mutableStateOf(5)
        private set

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        shuffle = p.getBoolean("shuffle", true)
        crossfadeSec = p.getInt("crossfade_sec", 5).coerceIn(0, 12)
        vinylMode = p.getBoolean("vinyl", false)
        sort = try {
            SortMode.valueOf(p.getString("sort", null) ?: SortMode.TITLE.name)
        } catch (e: Exception) {
            SortMode.TITLE
        }
        val key = p.getString("source_key", null)
        if (key != null) {
            source = Source(key, p.getString("source_label", "") ?: "", p.getString("source_title", "") ?: "")
        }
        sourceSongIds = parseIds(p.getString("source_ids", null))
    }

    fun setShuffle(context: Context, on: Boolean) {
        init(context)
        shuffle = on
        prefs?.edit()?.putBoolean("shuffle", on)?.apply()
    }

    fun setVinyl(context: Context, on: Boolean) {
        init(context)
        vinylMode = on
        prefs?.edit()?.putBoolean("vinyl", on)?.apply()
    }

    fun setCrossfade(context: Context, seconds: Int) {
        init(context)
        crossfadeSec = seconds.coerceIn(0, 12)
        prefs?.edit()?.putInt("crossfade_sec", crossfadeSec)?.apply()
    }

    fun setSort(context: Context, mode: SortMode) {
        init(context)
        sort = mode
        prefs?.edit()?.putString("sort", mode.name)?.apply()
    }

    fun setSource(context: Context, src: Source, ids: List<Long>) {
        init(context)
        source = src
        sourceSongIds = ids
        prefs?.edit()
            ?.putString("source_key", src.key)
            ?.putString("source_label", src.label)
            ?.putString("source_title", src.title)
            ?.putString("source_ids", JSONArray(ids).toString())
            ?.apply()
    }

    fun saveQueue(context: Context, ids: List<Long>) {
        init(context)
        prefs?.edit()?.putString("queue", JSONArray(ids).toString())?.apply()
    }

    fun saveProgress(context: Context, index: Int, positionMs: Long) {
        init(context)
        prefs?.edit()?.putInt("q_index", index)?.putLong("q_pos", positionMs)?.apply()
    }

    fun loadSession(context: Context): Session? {
        init(context)
        val p = prefs ?: return null
        val ids = parseIds(p.getString("queue", null))
        if (ids.isEmpty()) return null
        return Session(ids, p.getInt("q_index", 0), p.getLong("q_pos", 0L))
    }

    private fun parseIds(raw: String?): List<Long> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getLong(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
