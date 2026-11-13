package com.nikhil.dhun.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val track: Int,
    val dateAdded: Long,
    val folderPath: String,
    val folderName: String,
) {
    val uri: Uri get() = songUri(id)
    val artUri: Uri get() = ContentUris.withAppendedId(ALBUM_ART_BASE, albumId)

    companion object {
        val ALBUM_ART_BASE: Uri = Uri.parse("content://media/external/audio/albumart")
        fun songUri(id: Long): Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    }
}

fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artUri)
                .build()
        )
        .build()

/** Where playback came from. Each source has its own smart-shuffle cycle (keyed by [key]). */
data class Source(val key: String, val label: String, val title: String) {
    companion object {
        fun all() = Source("all", "LIBRARY", "All songs")
        fun playlist(id: Long, name: String) = Source("playlist:$id", "PLAYLIST", name)
        fun album(id: Long, name: String) = Source("album:$id", "ALBUM", name)
        fun artist(name: String) = Source("artist:$name", "ARTIST", name)
        fun folder(path: String, name: String) = Source("folder:$path", "FOLDER", name)
        fun search(query: String) = Source("search:${query.lowercase()}", "SEARCH", "“$query”")
    }
}

data class Playlist(val id: Long, val name: String, val songIds: List<Long>, val createdAt: Long)

data class AlbumInfo(val id: Long, val title: String, val artist: String, val songs: List<Song>)
data class ArtistInfo(val name: String, val songs: List<Song>)
data class FolderInfo(val path: String, val name: String, val songs: List<Song>)

enum class SortMode(val label: String) {
    TITLE("A–Z"),
    RECENT("Recently added"),
    ARTIST("Artist"),
}

fun sortSongs(songs: List<Song>, mode: SortMode): List<Song> = when (mode) {
    SortMode.TITLE -> songs.sortedBy { it.title.lowercase() }
    SortMode.RECENT -> songs.sortedByDescending { it.dateAdded }
    SortMode.ARTIST -> songs.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
}

fun albumsOf(songs: List<Song>): List<AlbumInfo> =
    songs.groupBy { it.albumId }.map { (id, list) ->
        val artist = list.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key ?: ""
        AlbumInfo(id, list.first().album, artist, list.sortedWith(compareBy({ it.track }, { it.title.lowercase() })))
    }.sortedBy { it.title.lowercase() }

fun artistsOf(songs: List<Song>): List<ArtistInfo> =
    songs.groupBy { it.artist }.map { (name, list) -> ArtistInfo(name, list.sortedBy { it.title.lowercase() }) }
        .sortedBy { it.name.lowercase() }

fun foldersOf(songs: List<Song>): List<FolderInfo> =
    songs.groupBy { it.folderPath }.map { (path, list) ->
        FolderInfo(path, list.first().folderName, list.sortedBy { it.title.lowercase() })
    }.sortedBy { it.name.lowercase() }

/** Songs that belong to a source, in their natural (unshuffled) order. */
fun songsFor(
    source: Source,
    all: List<Song>,
    byId: Map<Long, Song>,
    playlists: List<Playlist>,
    sort: SortMode,
): List<Song> {
    val key = source.key
    return when {
        key == "all" -> sortSongs(all, sort)
        key.startsWith("playlist:") -> {
            val id = key.removePrefix("playlist:").toLongOrNull()
            playlists.firstOrNull { it.id == id }?.songIds?.mapNotNull { byId[it] } ?: emptyList()
        }
        key.startsWith("album:") -> {
            val id = key.removePrefix("album:").toLongOrNull()
            all.filter { it.albumId == id }.sortedWith(compareBy({ it.track }, { it.title.lowercase() }))
        }
        key.startsWith("artist:") -> {
            val name = key.removePrefix("artist:")
            all.filter { it.artist == name }.sortedWith(compareBy({ it.album.lowercase() }, { it.track }))
        }
        key.startsWith("folder:") -> {
            val path = key.removePrefix("folder:")
            all.filter { it.folderPath == path }.sortedBy { it.title.lowercase() }
        }
        else -> emptyList()
    }
}
