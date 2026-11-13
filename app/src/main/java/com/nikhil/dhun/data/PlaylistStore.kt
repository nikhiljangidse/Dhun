package com.nikhil.dhun.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** User playlists, saved on the phone as JSON. */
object PlaylistStore {
    private const val PREFS = "dhun_playlists"
    private var prefs: SharedPreferences? = null

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _playlists.value = parse(p.getString("items", null))
    }

    @Synchronized
    fun create(context: Context, name: String): Long {
        init(context)
        val p = prefs!!
        val id = p.getLong("next_id", 1L)
        p.edit().putLong("next_id", id + 1).apply()
        save(_playlists.value + Playlist(id, name.trim(), emptyList(), System.currentTimeMillis()))
        return id
    }

    @Synchronized
    fun rename(context: Context, id: Long, name: String) {
        init(context)
        save(_playlists.value.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    @Synchronized
    fun delete(context: Context, id: Long) {
        init(context)
        save(_playlists.value.filterNot { it.id == id })
    }

    @Synchronized
    fun addSongs(context: Context, id: Long, songIds: List<Long>): Int {
        init(context)
        var added = 0
        save(_playlists.value.map { pl ->
            if (pl.id != id) return@map pl
            val existing = pl.songIds.toHashSet()
            val fresh = songIds.filter { existing.add(it) }
            added = fresh.size
            pl.copy(songIds = pl.songIds + fresh)
        })
        return added
    }

    @Synchronized
    fun removeSong(context: Context, id: Long, songId: Long) {
        init(context)
        save(_playlists.value.map { if (it.id == id) it.copy(songIds = it.songIds - songId) else it })
    }

    private fun save(list: List<Playlist>) {
        _playlists.value = list
        val arr = JSONArray()
        list.forEach { pl ->
            arr.put(
                JSONObject()
                    .put("id", pl.id)
                    .put("name", pl.name)
                    .put("createdAt", pl.createdAt)
                    .put("songs", JSONArray(pl.songIds))
            )
        }
        prefs?.edit()?.putString("items", arr.toString())?.apply()
    }

    private fun parse(raw: String?): List<Playlist> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val s = o.optJSONArray("songs") ?: JSONArray()
                Playlist(
                    id = o.getLong("id"),
                    name = o.optString("name", "Playlist"),
                    songIds = (0 until s.length()).map { s.getLong(it) },
                    createdAt = o.optLong("createdAt", 0L),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
