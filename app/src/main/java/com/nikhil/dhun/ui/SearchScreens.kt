package com.nikhil.dhun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nikhil.dhun.data.PlaybackPrefs
import com.nikhil.dhun.data.Song
import com.nikhil.dhun.data.Source
import com.nikhil.dhun.ui.theme.DhunColors

private fun matches(song: Song, q: String): Boolean =
    song.title.contains(q, ignoreCase = true) ||
        song.artist.contains(q, ignoreCase = true) ||
        song.album.contains(q, ignoreCase = true)

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onBack: () -> Unit, placeholder: String) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (e: Exception) {
            // ignore
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = DhunColors.Text)
        }
        TextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text(placeholder, color = DhunColors.Muted) },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = DhunColors.Surface,
                unfocusedContainerColor = DhunColors.Surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = DhunColors.Accent,
                focusedTextColor = DhunColors.Text,
                unfocusedTextColor = DhunColors.Text,
            ),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = DhunColors.Muted)
                    }
                }
            },
            modifier = Modifier.weight(1f).padding(end = 10.dp).focusRequester(focus),
        )
    }
}

@Composable
fun SearchScreen(
    songs: List<Song>,
    currentId: Long?,
    actions: AppActions,
    onBack: () -> Unit,
    bottomPadding: Dp,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val results = remember(q, songs) { if (q.isEmpty()) emptyList() else songs.filter { matches(it, q) } }

    Column(Modifier.fillMaxSize()) {
        SearchBar(query, { query = it }, onBack, "Songs, artists, albums")
        if (q.isNotEmpty() && results.isEmpty()) {
            SectionEmpty("No matches", "Try a different song, artist or album name.")
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
            items(results, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    isCurrent = song.id == currentId,
                    actions = actions,
                    onClick = { actions.play(Source.search(q), results, song, PlaybackPrefs.shuffle) },
                )
            }
        }
    }
}

/** Pick songs to add to a playlist. */
@Composable
fun PickSongsScreen(
    playlistName: String,
    songs: List<Song>,
    alreadyIn: Set<Long>,
    onBack: () -> Unit,
    onAdd: (List<Long>) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val selected = remember { mutableStateListOf<Long>() }
    val shown = remember(q, songs) { if (q.isEmpty()) songs else songs.filter { matches(it, q) } }

    Column(Modifier.fillMaxSize()) {
        SearchBar(query, { query = it }, onBack, "Add to $playlistName")
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.id }) { song ->
                val inPlaylist = song.id in alreadyIn
                val checked = inPlaylist || song.id in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !inPlaylist) {
                            if (song.id in selected) selected.remove(song.id) else selected.add(song.id)
                        }
                        .padding(start = 20.dp, end = 8.dp)
                        .height(64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SongArt(song, Modifier.size(46.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song.title, color = if (inPlaylist) DhunColors.Muted else DhunColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (inPlaylist) "Already in playlist" else song.artist,
                            color = DhunColors.Muted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Checkbox(
                        checked = checked,
                        enabled = !inPlaylist,
                        onCheckedChange = { on -> if (on) selected.add(song.id) else selected.remove(song.id) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = DhunColors.Accent,
                            checkmarkColor = DhunColors.OnAccent,
                            uncheckedColor = DhunColors.Muted,
                        ),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().background(DhunColors.Bg).navigationBarsPadding().padding(16.dp)) {
            Button(
                onClick = { onAdd(selected.toList()) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DhunColors.Accent,
                    contentColor = DhunColors.OnAccent,
                    disabledContainerColor = DhunColors.Surface2,
                    disabledContentColor = DhunColors.Muted,
                ),
            ) {
                Text(
                    if (selected.isEmpty()) "Select songs" else "Add ${songCount(selected.size)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
