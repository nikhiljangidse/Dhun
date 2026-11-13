package com.nikhil.dhun.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.nikhil.dhun.data.Song
import com.nikhil.dhun.playback.PlayerConnection
import com.nikhil.dhun.ui.theme.DhunColors
import com.nikhil.dhun.ui.theme.artColor
import kotlinx.coroutines.delay

@Composable
fun MiniPlayer(song: Song, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(song.id) {
        while (true) {
            val d = PlayerConnection.durationMs
            progress = if (d > 0) (PlayerConnection.position.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .shadow(16.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(DhunColors.Surface2)
            .clickable(onClickLabel = "Open player", onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SongArt(song, Modifier.size(48.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(song.title, color = DhunColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = DhunColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { PlayerConnection.playPause() }) {
                Icon(
                    if (PlayerConnection.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (PlayerConnection.isPlaying) "Pause" else "Play",
                    tint = DhunColors.Text,
                    modifier = Modifier.size(28.dp),
                )
            }
            IconButton(onClick = { PlayerConnection.next() }) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = DhunColors.Text, modifier = Modifier.size(28.dp))
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(progress)
                .height(3.dp)
                .background(DhunColors.Accent),
        )
    }
}

@Composable
fun NowPlayingScreen(
    song: Song,
    sourceLabel: String,
    sourceTitle: String,
    shuffleOn: Boolean,
    shuffleInfo: String?,
    upNext: Song?,
    onClose: () -> Unit,
    onToggleShuffle: () -> Unit,
    onOpenQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    val tint = lerp(DhunColors.Bg, artColor(song.albumId), 0.45f)
    var position by remember { mutableLongStateOf(PlayerConnection.position) }
    LaunchedEffect(Unit) {
        while (true) {
            position = PlayerConnection.position
            delay(250)
        }
    }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = PlayerConnection.durationMs
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shown = dragging ?: fraction

    Box(
        Modifier
            .fillMaxSize()
            .background(tint)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        if (Build.VERSION.SDK_INT >= 31) {
            AsyncImage(
                model = song.artUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(90.dp).alpha(0.45f),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), DhunColors.Bg.copy(alpha = 0.92f)))),
        )

        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close player", tint = DhunColors.Text, modifier = Modifier.size(32.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING FROM $sourceLabel", color = DhunColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, maxLines = 1)
                    Text(sourceTitle, color = DhunColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onOpenQueue) {
                    Icon(Icons.Rounded.QueueMusic, contentDescription = "Queue", tint = DhunColors.Text)
                }
            }

            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val side = if (maxWidth < maxHeight) maxWidth else maxHeight
                SongArt(
                    song,
                    Modifier
                        .size(side)
                        .shadow(28.dp, RoundedCornerShape(24.dp)),
                    corner = 24.dp,
                    iconSize = 96.dp,
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(song.title, color = DhunColors.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${song.artist} · ${song.album}", color = DhunColors.Muted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onAddToPlaylist) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add to playlist", tint = DhunColors.Text)
                }
            }

            Spacer(Modifier.height(12.dp))

            Slider(
                value = shown,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { PlayerConnection.seekTo((it * duration).toLong()) }
                    dragging = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = DhunColors.Accent,
                    inactiveTrackColor = DhunColors.Track,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatDuration((shown * duration).toLong()), color = DhunColors.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(formatDuration(duration), color = DhunColors.Muted, fontSize = 12.sp)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onToggleShuffle) {
                        Icon(
                            Icons.Rounded.Shuffle,
                            contentDescription = if (shuffleOn) "Smart shuffle on" else "Smart shuffle off",
                            tint = if (shuffleOn) DhunColors.Accent else DhunColors.Muted,
                        )
                    }
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (shuffleOn) DhunColors.Accent else Color.Transparent),
                    )
                }
                IconButton(onClick = { PlayerConnection.previous() }, modifier = Modifier.size(60.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = DhunColors.Text, modifier = Modifier.size(40.dp))
                }
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .clip(CircleShape)
                        .background(DhunColors.Accent)
                        .clickable(role = Role.Button, onClick = { PlayerConnection.playPause() }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (PlayerConnection.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (PlayerConnection.isPlaying) "Pause" else "Play",
                        tint = DhunColors.OnAccent,
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = { PlayerConnection.next() }, modifier = Modifier.size(60.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = DhunColors.Text, modifier = Modifier.size(40.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val mode = PlayerConnection.repeatMode
                    IconButton(onClick = { PlayerConnection.cycleRepeat() }) {
                        Icon(
                            if (mode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = when (mode) {
                                Player.REPEAT_MODE_ONE -> "Repeat one"
                                Player.REPEAT_MODE_ALL -> "Repeat all"
                                else -> "Repeat off"
                            },
                            tint = if (mode != Player.REPEAT_MODE_OFF) DhunColors.Accent else DhunColors.Muted,
                        )
                    }
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (mode != Player.REPEAT_MODE_OFF) DhunColors.Accent else Color.Transparent),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                if (shuffleOn && shuffleInfo != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(DhunColors.Accent))
                        Text(shuffleInfo, color = DhunColors.Text, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .clickable(onClickLabel = "Open queue", onClick = onOpenQueue)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = DhunColors.Text, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text("UP NEXT", color = DhunColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    Text(
                        upNext?.let { "${it.title} · ${it.artist}" } ?: "Nothing after this song",
                        color = DhunColors.Text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Rounded.ExpandLess, contentDescription = null, tint = DhunColors.Muted)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun QueueScreen(byId: Map<Long, Song>, onClose: () -> Unit) {
    val ids = PlayerConnection.queueIds
    val current = PlayerConnection.currentIndex
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = current.coerceAtLeast(0))
    Column(
        Modifier
            .fillMaxSize()
            .background(DhunColors.Bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close queue", tint = DhunColors.Text, modifier = Modifier.size(30.dp))
            }
            Text("Queue", color = DhunColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${ids.size} songs", color = DhunColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(end = 14.dp))
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            itemsIndexed(ids) { index, id ->
                val song = byId[id] ?: return@itemsIndexed
                val isCurrent = index == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isCurrent) DhunColors.Surface else Color.Transparent)
                        .clickable { PlayerConnection.seekToIndex(index) }
                        .padding(horizontal = 20.dp)
                        .height(62.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SongArt(song, Modifier.size(44.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            song.title,
                            color = when {
                                isCurrent -> DhunColors.Accent
                                index < current -> DhunColors.Muted
                                else -> DhunColors.Text
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(song.artist, color = DhunColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (isCurrent) {
                        Text("Playing", color = DhunColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
