package com.nikhil.dhun.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Listening history for smart shuffle.
 * - [heard]: song id -> last time it was "heard" (played 30 s or half the song).
 * - [cycles]: source key -> when its current shuffle cycle started.
 * A song counts as heard in a source's cycle when lastHeard > cycleStart.
 */
object HistoryStore {
    private const val PREFS = "dhun_history"
    private var prefs: SharedPreferences? = null

    private val _heard = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val heard: StateFlow<Map<Long, Long>> = _heard.asStateFlow()

    private val _cycles = MutableStateFlow<Map<String, Long>>(emptyMap())
    val cycles: StateFlow<Map<String, Long>> = _cycles.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _heard.value = parseLongKeys(p.getString("heard", null))
        _cycles.value = parseStringKeys(p.getString("cycles", null))
    }

    fun lastHeard(id: Long): Long = _heard.value[id] ?: 0L
    fun cycleStart(key: String): Long = _cycles.value[key] ?: 0L

    @Synchronized
    fun markHeard(context: Context, id: Long) {
        init(context)
        _heard.value = _heard.value + (id to System.currentTimeMillis())
        save()
    }

    @Synchronized
    fun resetCycle(context: Context, key: String) {
        init(context)
        _cycles.value = _cycles.value + (key to System.currentTimeMillis())
        save()
    }

    @Synchronized
    fun clearAll(context: Context) {
        init(context)
        _heard.value = emptyMap()
        _cycles.value = emptyMap()
        save()
    }

    /** Move "heard" history from deleted duplicates to the copy that was kept. */
    @Synchronized
    fun transfer(context: Context, keptFor: Map<Long, Long>, removed: Set<Long>) {
        init(context)
        val h = _heard.value.toMutableMap()
        keptFor.forEach { (old, new) ->
            val t = h[old]
            if (t != null) h[new] = maxOf(h[new] ?: 0L, t)
        }
        removed.forEach { h.remove(it) }
        _heard.value = h
        save()
    }

    private fun save() {
        val h = JSONObject()
        _heard.value.forEach { (k, v) -> h.put(k.toString(), v) }
        val c = JSONObject()
        _cycles.value.forEach { (k, v) -> c.put(k, v) }
        prefs?.edit()?.putString("heard", h.toString())?.putString("cycles", c.toString())?.apply()
    }

    private fun parseLongKeys(raw: String?): Map<Long, Long> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val o = JSONObject(raw)
            val out = HashMap<Long, Long>()
            o.keys().forEach { k -> k.toLongOrNull()?.let { out[it] = o.getLong(k) } }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun parseStringKeys(raw: String?): Map<String, Long> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val o = JSONObject(raw)
            val out = HashMap<String, Long>()
            o.keys().forEach { k -> out[k] = o.getLong(k) }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

/** How many of [songs] are already heard in the current cycle of [key]. */
fun heardCount(songs: List<Song>, key: String, heard: Map<Long, Long>, cycles: Map<String, Long>): Int {
    val start = cycles[key] ?: 0L
    return songs.count { (heard[it.id] ?: 0L) > start }
}

fun heardCountIds(ids: List<Long>, key: String, heard: Map<Long, Long>, cycles: Map<String, Long>): Int {
    val start = cycles[key] ?: 0L
    return ids.count { (heard[it] ?: 0L) > start }
}
