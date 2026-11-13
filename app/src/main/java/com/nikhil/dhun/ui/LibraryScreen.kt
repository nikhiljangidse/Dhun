package com.nikhil.dhun.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nikhil.dhun.data.PlaybackPrefs
import com.nikhil.dhun.data.Playlist
import com.nikhil.dhun.data.Song
import com.nikhil.dhun.data.SortMode
import com.nikhil.dhun.data.Source
import com.nikhil.dhun.data.albumsOf
import com.nikhil.dhun.data.artistsOf
import com.nikhil.dhun.data.foldersOf
import com.nikhil.dhun.data.heardCount
import com.nikhil.dhun.data.sortSongs
import com.nikhil.dhun.playback.PlayerConnection
import com.nikhil.dhun.ui.theme.DhunColors

enum class LibTab(val label: String) {
    Songs("Songs"),
    Playlists("Playlists"),
    Albums("Albums"),
    Artists("Artists"),
    Folders("Folders"),
}

@Composable
fun LibraryScreen(
    tab: LibTab,
    onTab: (LibTab) -> Unit,
    loaded: Boolean,
    songs: List<Song>,
    playlists: List<Playlist>,
    byId: Map<Long, Song>,
    heard: Map<Long, Long>,
    cycles: Map<String, Long>,
    currentId: Long?,
    actions: AppActions,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    bottomPadding: Dp,
) {
    val context = LocalContext.current
    val sort = PlaybackPrefs.sort
    val sorted = remember(songs, sort) { sortSongs(songs, sort) }
    val albums = remember(songs) { albumsOf(songs) }
    val artists = remember(songs) { artistsOf(songs) }
    val folders = remember(songs) { foldersOf(songs) }
    val recent = remember(heard, byId) {
        heard.entries.sortedByDescending { it.value }.asSequence().mapNotNull { byId[it.key] }.take(12).toList()
    }

    Column(Modifier.fillMaxSize()) {
        // Title bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(greeting(), color = DhunColors.Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Library", color = DhunColors.Text, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
            }
            RoundIcon(onClick = onSearch, label = "Search") { Icon(Icons.Rounded.Search, "Search", tint = DhunColors.Text) }
            Spacer(Modifier.width(8.dp))
            RoundIcon(onClick = onSettings, label = "Settings") { Icon(Icons.Rounded.Tune, "Settings", tint = DhunColors.Text) }
        }

        // Tabs
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            items(LibTab.entries.toList()) { t ->
                val on = t == tab
                val bg by animateColorAsState(if (on) DhunColors.Accent else DhunColors.Surface, tween(220), label = "tabBg")
                val fg by animateColorAsState(if (on) DhunColors.OnAccent else DhunColors.Text, tween(220), label = "tabFg")
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(bg)
                        .clickable(role = Role.Tab) { onTab(t) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        t.label,
                        color = fg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (loaded && songs.isEmpty()) {
            SectionEmpty(
                "No music found",
                "Copy songs (mp3, m4a, flac…) into your phone's Music or Download folder, then come back here.",
            )
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
            when (tab) {
                LibTab.Songs -> {
                    if (recent.isNotEmpty()) {
                        item(key = "recent") {
                            Column(Modifier.padding(bottom = 14.dp)) {
                                Text(
                                    "Recently played",
                                    color = DhunColors.Text,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(start = 20.dp, bottom = 12.dp),
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    items(recent, key = { "r_${it.id}" }) { s ->
                                        Column(
                                            modifier = Modifier
                                                .width(136.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable { actions.play(Source.all(), sorted, s, PlaybackPrefs.shuffle) },
                                            verticalArrangement = Arrangement.spacedBy(3.dp),
                                        ) {
                                            Box(Modifier.size(136.dp)) {
                                                SongArt(s, Modifier.matchParentSize(), corner = 16.dp, iconSize = 36.dp)
                                                if (s.id == currentId) {
                                                    Box(
                                                        Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .padding(8.dp)
                                                            .size(30.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.Black.copy(alpha = 0.6f)),
                                                        contentAlignment = Alignment.Center,
                                                    ) { EqualizerBars(playing = PlayerConnection.isPlaying, color = DhunColors.Accent, modifier = Modifier.height(14.dp)) }
                                                }
                                            }
                                            Spacer(Modifier.height(5.dp))
                                            Text(s.title, color = DhunColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(s.artist, color = DhunColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item(key = "all_card") {
                        val all = Source.all()
                        val h = heardCount(songs, all.key, heard, cycles)
                        SourceCard(
                            title = "All songs",
                            subtitle = songCount(songs.size),
                            freshCount = songs.size - h,
                            onPlay = { actions.play(all, sorted, null, false) },
                            onShuffle = { actions.play(all, sorted, null, true) },
                        )
                    }
                    item(key = "sort") {
                        SortRow(sort) { PlaybackPrefs.setSort(context, it) }
                    }
                    items(sorted, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            isCurrent = song.id == currentId,
                            actions = actions,
                            onClick = { actions.play(Source.all(), sorted, song, PlaybackPrefs.shuffle) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                LibTab.Playlists -> {
                    item(key = "new") {
                        ListRow(
                            onClick = actions.createPlaylist,
                            leading = {
                                Box(
                                    Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(DhunColors.Surface2),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Rounded.Add, null, tint = DhunColors.Accent, modifier = Modifier.size(28.dp)) }
                            },
                            title = "New playlist",
                            subtitle = "Make a mix for gym, drive, sleep…",
                        )
                    }
                    items(playlists, key = { "pl_${it.id}" }) { pl ->
                        val plSongs = pl.songIds.mapNotNull { byId[it] }
                        ListRow(
                            onClick = { actions.open(Source.playlist(pl.id, pl.name)) },
                            leading = { Mosaic(plSongs, Modifier.size(56.dp)) },
                            title = pl.name,
                            subtitle = songCount(plSongs.size),
                        )
                    }
                }

                LibTab.Albums -> {
                    items(albums.chunked(2), key = { "al_${it.first().id}" }) { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            pair.forEach { al ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { actions.open(Source.album(al.id, al.title)) },
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    SongArt(al.songs.firstOrNull(), Modifier.fillMaxWidth().aspectRatio(1f), 14.dp, 40.dp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(al.title, color = DhunColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(al.artist, color = DhunColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                LibTab.Artists -> {
                    items(artists, key = { "ar_${it.name}" }) { ar ->
                        ListRow(
                            onClick = { actions.open(Source.artist(ar.name)) },
                            leading = {
                                Box(
                                    Modifier.size(52.dp).clip(CircleShape).background(DhunColors.Surface2),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        ar.name.take(1).uppercase(),
                                        color = DhunColors.Accent,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            },
                            title = ar.name,
                            subtitle = songCount(ar.songs.size),
                        )
                    }
                }

                LibTab.Folders -> {
                    items(folders, key = { "fo_${it.path}" }) { fo ->
                        ListRow(
                            onClick = { actions.open(Source.folder(fo.path, fo.name)) },
                            leading = {
                                Box(
                                    Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(DhunColors.Surface2),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Rounded.Folder, null, tint = DhunColors.Accent) }
                            },
                            title = fo.name,
                            subtitle = "${songCount(fo.songs.size)} · ${fo.path}",
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RoundIcon(onClick: () -> Unit, label: String, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp).clip(CircleShape).background(DhunColors.Surface),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** "All songs / 248 songs · 173 not heard yet  [▶] [Shuffle]" card. */
@Composable
fun SourceCard(title: String, subtitle: String, freshCount: Int, onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(DhunColors.Surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = DhunColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Row {
                Text("$subtitle · ", color = DhunColors.Muted, fontSize = 13.sp)
                Text("$freshCount not heard", color = DhunColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        PlayOutlineButton(onClick = onPlay)
        ShuffleButton("Shuffle", onClick = onShuffle)
    }
}

@Composable
private fun SortRow(sort: SortMode, onSort: (SortMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        TextButton(onClick = { open = true }) {
            Text("Sort: ${sort.label}", color = DhunColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = DhunColors.Muted, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortMode.entries.forEach { m ->
                DropdownMenuItem(text = { Text(m.label) }, onClick = { open = false; onSort(m) })
            }
        }
    }
}

@Composable
fun ListRow(
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = DhunColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = DhunColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun greeting(): String {
    val h = java.time.LocalTime.now().hour
    return when (h) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Late night vibes"
    }
}
