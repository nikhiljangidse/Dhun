package com.nikhil.dhun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nikhil.dhun.data.Song
import com.nikhil.dhun.data.Source
import com.nikhil.dhun.data.heardCount
import com.nikhil.dhun.ui.theme.DhunColors

/** Detail page for a playlist, album, artist or folder. */
@Composable
fun CollectionScreen(
    source: Source,
    songs: List<Song>,
    isPlaylist: Boolean,
    heard: Map<Long, Long>,
    cycles: Map<String, Long>,
    currentId: Long?,
    actions: AppActions,
    onBack: () -> Unit,
    onStartFresh: () -> Unit,
    onAddSongs: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onRemoveSong: (Song) -> Unit,
    bottomPadding: Dp,
) {
    val cycleStart = cycles[source.key] ?: 0L
    val heardIds = remember(songs, heard, cycleStart) {
        songs.filter { (heard[it.id] ?: 0L) > cycleStart }.map { it.id }.toHashSet()
    }
    val h = heardCount(songs, source.key, heard, cycles)
    val total = songs.size
    var menu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = DhunColors.Text)
            }
            Box(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Options", tint = DhunColors.Text)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Start fresh shuffle cycle") }, onClick = { menu = false; onStartFresh() })
                    if (isPlaylist) {
                        DropdownMenuItem(text = { Text("Add songs") }, onClick = { menu = false; onAddSongs() })
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (isPlaylist) Mosaic(songs, Modifier.size(116.dp), 16.dp)
                    else SongArt(songs.firstOrNull(), Modifier.size(116.dp), 16.dp, 40.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                        Text(source.label, color = DhunColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                        Text(source.title, color = DhunColors.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 32.sp, maxLines = 2)
                        Text(
                            "${songCount(total)} · ${formatTotal(songs.sumOf { it.durationMs })}",
                            color = DhunColors.Muted,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            item(key = "buttons") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ShuffleButton(
                        label = "Smart shuffle",
                        onClick = { actions.play(source, songs, null, true) },
                        modifier = Modifier.weight(1f),
                        height = 52.dp,
                    )
                    PlayOutlineButton(onClick = { actions.play(source, songs, null, false) }, size = 52.dp)
                    if (isPlaylist) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .border(1.dp, DhunColors.Line, CircleShape)
                                .clickable(role = Role.Button, onClick = onAddSongs),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add songs", tint = DhunColors.Text)
                        }
                    }
                }
            }

            if (total > 0) {
                item(key = "cycle") {
                    CycleCard(heard = h, total = total, onStartFresh = onStartFresh)
                }
            } else {
                item(key = "empty") {
                    SectionEmpty(
                        if (isPlaylist) "This playlist is empty" else "No songs here",
                        if (isPlaylist) "Tap + to add songs from your library." else "Songs may have been moved or deleted.",
                    )
                }
            }

            items(songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    isCurrent = song.id == currentId,
                    actions = actions,
                    onClick = { actions.play(source, songs, song, com.nikhil.dhun.data.PlaybackPrefs.shuffle) },
                    showHeardTag = song.id in heardIds && song.id != currentId,
                    onRemove = if (isPlaylist) ({ onRemoveSong(song) }) else null,
                )
            }
        }
    }
}

@Composable
private fun CycleCard(heard: Int, total: Int, onStartFresh: () -> Unit) {
    val fraction = if (total > 0) heard.toFloat() / total else 0f
    val left = total - heard
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(DhunColors.Surface)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Shuffle cycle", color = DhunColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onStartFresh) {
                Text("Start fresh", color = DhunColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Box(
            Modifier
                .padding(end = 8.dp)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(DhunColors.Chip),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(DhunColors.Accent))
        }
        Text(
            if (left == 0) "All $total heard. The next shuffle starts a new cycle."
            else "$heard of $total heard. Shuffle plays the other $left first, then starts a new cycle.",
            color = DhunColors.Muted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
}
