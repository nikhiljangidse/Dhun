package com.nikhil.dhun.data

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Reads every audio file on the phone from MediaStore. */
object MusicLibrary {
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    @Volatile
    private var cache: Pair<List<Song>, Map<Long, Song>> = emptyList<Song>() to emptyMap()

    fun byId(): Map<Long, Song> {
        val list = _songs.value
        val c = cache
        if (c.first === list) return c.second
        val map = list.associateBy { it.id }
        cache = list to map
        return map
    }

    suspend fun refresh(context: Context) {
        val list = withContext(Dispatchers.IO) {
            try {
                query(context.applicationContext)
            } catch (e: Exception) {
                null
            }
        }
        if (list != null && list != _songs.value) _songs.value = list
        _loaded.value = true
    }

    private fun query(context: Context): List<Song> {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val pathColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DISPLAY_NAME,
            pathColumn,
        )
        val selection = "${MediaStore.Audio.Media.DURATION} >= 30000" +
            " AND ${MediaStore.Audio.Media.IS_RINGTONE} = 0" +
            " AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0" +
            " AND ${MediaStore.Audio.Media.IS_ALARM} = 0"

        val out = ArrayList<Song>()
        context.contentResolver.query(uri, projection, selection, null, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iDuration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val iDate = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val iPath = c.getColumnIndexOrThrow(pathColumn)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val fileName = c.getString(iName) ?: "Unknown"
                val title = c.getString(iTitle)?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.')
                val artist = c.getString(iArtist)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist"
                val album = c.getString(iAlbum)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown album"
                val rawPath = c.getString(iPath) ?: ""
                val folderPath = if (Build.VERSION.SDK_INT >= 29) rawPath.trimEnd('/') else rawPath.substringBeforeLast('/', "")
                val folderName = folderPath.substringAfterLast('/').ifBlank { "Internal storage" }
                out += Song(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    albumId = c.getLong(iAlbumId),
                    durationMs = c.getLong(iDuration),
                    track = c.getInt(iTrack) % 1000,
                    dateAdded = c.getLong(iDate),
                    folderPath = folderPath,
                    folderName = folderName,
                )
            }
        }
        return out.sortedBy { it.title.lowercase() }
    }
}
