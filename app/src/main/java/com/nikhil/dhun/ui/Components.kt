package com.nikhil.dhun.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nikhil.dhun.data.Song
import com.nikhil.dhun.data.Source
import com.nikhil.dhun.ui.theme.DhunColors
import com.nikhil.dhun.ui.theme.artColor

/** Everything a song row / screen can ask the app to do. */
class AppActions(
    val play: (source: Source, songs: List<Song>, start: Song?, shuffle: Boolean) -> Unit,
    val open: (Source) -> Unit,
    val playNext: (Song) -> Unit,
    val addToQueue: (Song) -> Unit,
    val addToPlaylist: (Song) -> Unit,
    val createPlaylist: () -> Unit,
)

fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
}

fun formatTotal(ms: Long): String {
    val mins = ms / 60_000
    val h = mins / 60
    val m = mins % 60
    return if (h > 0) "$h hr $m min" else "$m min"
}

fun songCount(n: Int) = if (n == 1) "1 song" else "$n songs"

@Composable
fun Artwork(
    albumId: Long?,
    artUri: Uri?,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    iconSize: Dp = 20.dp,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(artColor(albumId ?: 0L)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(iconSize),
        )
        if (artUri != null) {
            AsyncImage(
                model = artUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
fun SongArt(song: Song?, modifier: Modifier = Modifier, corner: Dp = 10.dp, iconSize: Dp = 20.dp) {
    Artwork(song?.albumId, song?.artUri, modifier, corner, iconSize)
}

/** 2x2 grid of covers for a playlist. */
@Composable
fun Mosaic(songs: List<Song>, modifier: Modifier = Modifier, corner: Dp = 12.dp) {
    val covers = songs.distinctBy { it.albumId }.take(4)
    if (covers.size < 4) {
        SongArt(covers.firstOrNull(), modifier, corner, iconSize = 28.dp)
        return
    }
    Column(modifier.clip(RoundedCornerShape(corner))) {
        Row(Modifier.weight(1f)) {
            SongArt(covers[0], Modifier.weight(1f).fillMaxSize(), 0.dp, 14.dp)
            SongArt(covers[1], Modifier.weight(1f).fillMaxSize(), 0.dp, 14.dp)
        }
        Row(Modifier.weight(1f)) {
            SongArt(covers[2], Modifier.weight(1f).fillMaxSize(), 0.dp, 14.dp)
            SongArt(covers[3], Modifier.weight(1f).fillMaxSize(), 0.dp, 14.dp)
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    actions: AppActions,
    onClick: () -> Unit,
    showHeardTag: Boolean = false,
    onRemove: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 6.dp)
            .height(66.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongArt(song, Modifier.size(48.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                song.title,
                color = if (isCurrent) DhunColors.Accent else if (showHeardTag) DhunColors.Muted else DhunColors.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${song.artist} · ${formatDuration(song.durationMs)}",
                color = DhunColors.Muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showHeardTag) HeardTag()
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = DhunColors.Muted)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Play next") }, onClick = { menu = false; actions.playNext(song) })
                DropdownMenuItem(text = { Text("Add to queue") }, onClick = { menu = false; actions.addToQueue(song) })
                DropdownMenuItem(text = { Text("Add to playlist…") }, onClick = { menu = false; actions.addToPlaylist(song) })
                if (onRemove != null) {
                    DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = { menu = false; onRemove() })
                }
            }
        }
    }
}

@Composable
fun HeardTag() {
    Row(
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DhunColors.Chip)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = DhunColors.Muted, modifier = Modifier.size(12.dp))
        Text("Heard", color = DhunColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Big amber "Shuffle" pill. */
@Composable
fun ShuffleButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 48.dp) {
    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(DhunColors.Accent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = DhunColors.OnAccent, modifier = Modifier.size(20.dp))
        Text(label, color = DhunColors.OnAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** Round outlined "Play in order" button. */
@Composable
fun PlayOutlineButton(onClick: () -> Unit, size: Dp = 48.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, DhunColors.Line, CircleShape)
            .clickable(role = Role.Button, onClickLabel = "Play in order", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = "Play in order", tint = DhunColors.Text, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun SectionEmpty(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = DhunColors.Muted, modifier = Modifier.size(40.dp))
        Text(title, color = DhunColors.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = DhunColors.Muted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
