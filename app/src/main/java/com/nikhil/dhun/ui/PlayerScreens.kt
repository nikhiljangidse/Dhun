package com.nikhil.dhun.ui

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.nikhil.dhun.data.Song
import com.nikhil.dhun.playback.PlayerConnection
import com.nikhil.dhun.ui.theme.DhunColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------------------------------------------------------------- Mini player

@Composable
fun MiniPlayer(song: Song, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = rememberArtColors(song)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val swipe = with(density) { 70.dp.toPx() }
    val openLatest by rememberUpdatedState(onOpen)
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(song.id) {
        while (true) {
            val d = PlayerConnection.durationMs
            progress = if (d > 0) (PlayerConnection.position.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(400)
        }
    }
    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .shadow(20.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(colors.dark)
            .pointerInput(Unit) {
                var dy = 0f
                detectVerticalDragGestures(
                    onDragStart = { dy = 0f },
                    onDragEnd = { if (dy < -swipe / 2) openLatest() },
                ) { change, amount ->
                    change.consume()
                    dy += amount
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val x = dragX.value
                            if (x < -swipe) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                PlayerConnection.next()
                            } else if (x > swipe) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                PlayerConnection.previous()
                            }
                            dragX.animateTo(0f, spring(dampingRatio = 0.7f))
                        }
                    },
                    onDragCancel = { scope.launch { dragX.animateTo(0f) } },
                ) { change, amount ->
                    change.consume()
                    scope.launch { dragX.snapTo(dragX.value + amount * 0.6f) }
                }
            }
            .clickable(onClickLabel = "Open player", onClick = onOpen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 6.dp)
                .offset { IntOffset(dragX.value.roundToInt(), 0) }
                .alpha(1f - (abs(dragX.value) / (swipe * 3)).coerceIn(0f, 0.6f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Crossfade(targetState = song, label = "miniArt") { s ->
                SongArt(s, Modifier.size(50.dp), corner = 12.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(song.title, color = DhunColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = DhunColors.Text.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                PlayerConnection.playPause()
            }) {
                PlayPauseGlyph(PlayerConnection.isPlaying, DhunColors.Text, 30)
            }
            IconButton(onClick = { PlayerConnection.next() }) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = DhunColors.Text, modifier = Modifier.size(28.dp))
            }
        }
        // progress line
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 14.dp)
                .fillMaxWidth()
                .height(2.5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.18f)),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Color.White))
        }
    }
}

@Composable
fun PlayPauseGlyph(playing: Boolean, tint: Color, sizeDp: Int) {
    AnimatedContent(
        targetState = playing,
        transitionSpec = { (scaleIn(tween(180)) + fadeIn(tween(180))) togetherWith (scaleOut(tween(120)) + fadeOut(tween(120))) },
        label = "playPause",
    ) { isPlaying ->
        Icon(
            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = tint,
            modifier = Modifier.size(sizeDp.dp),
        )
    }
}

// ---------------------------------------------------------------- Seek bar

/** Slim bar that grows when you touch it (tap or drag to seek). */
@Composable
fun SeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    var active by remember { mutableStateOf(false) }
    var width by remember { mutableIntStateOf(1) }
    val barHeight by animateDpAsState(if (active) 10.dp else 5.dp, label = "seekHeight")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .semantics {
                contentDescription = "Seek"
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress { v ->
                    change(v.coerceIn(0f, 1f))
                    finished()
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { active = true; tryAwaitRelease(); active = false },
                    onTap = { o ->
                        change((o.x / width).coerceIn(0f, 1f))
                        finished()
                    },
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        active = true
                        change((o.x / width).coerceIn(0f, 1f))
                    },
                    onDragEnd = {
                        active = false
                        finished()
                    },
                    onDragCancel = {
                        active = false
                        finished()
                    },
                ) { c, _ ->
                    c.consume()
                    change((c.position.x / width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.2f)),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(value).clip(RoundedCornerShape(50)).background(Color.White))
        }
    }
}

// ---------------------------------------------------------------- Now playing

@OptIn(ExperimentalFoundationApi::class)
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
    val colors = rememberArtColors(song)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val closeLatest by rememberUpdatedState(onClose)

    var position by remember { mutableLongStateOf(PlayerConnection.position) }
    LaunchedEffect(Unit) {
        while (true) {
            position = PlayerConnection.position
            delay(200)
        }
    }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = PlayerConnection.durationMs
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shown = dragging ?: fraction
    val playing = PlayerConnection.isPlaying
    val accent = colors.vibrant.forAccent()
    val context = androidx.compose.ui.platform.LocalContext.current
    val vinyl = com.nikhil.dhun.data.PlaybackPrefs.vinylMode
    val toggleVinyl = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        com.nikhil.dhun.data.PlaybackPrefs.setVinyl(context, !com.nikhil.dhun.data.PlaybackPrefs.vinylMode)
    }

    // Swipe down anywhere to close.
    val sheetY = remember { Animatable(0f) }
    val closeAt = with(density) { 140.dp.toPx() }
    // Swipe the cover left/right to change song.
    val artX = remember { Animatable(0f) }
    val skipAt = with(density) { 90.dp.toPx() }
    val artScale by animateFloatAsState(
        targetValue = if (playing) 1f else 0.86f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        label = "artScale",
    )

    Box(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, sheetY.value.roundToInt()) }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (sheetY.value > closeAt) closeLatest() else sheetY.animateTo(0f, spring(dampingRatio = 0.8f))
                        }
                    },
                    onDragCancel = { scope.launch { sheetY.animateTo(0f) } },
                ) { change, dy ->
                    change.consume()
                    scope.launch { sheetY.snapTo((sheetY.value + dy).coerceAtLeast(0f)) }
                }
            },
    ) {
        AmbientBackground(song, colors.dark)

        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ---- top bar
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close player", tint = DhunColors.Text, modifier = Modifier.size(32.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "PLAYING FROM $sourceLabel",
                        color = DhunColors.Text.copy(alpha = 0.65f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp,
                        maxLines = 1,
                    )
                    Text(sourceTitle, color = DhunColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onOpenQueue) {
                    Icon(Icons.Rounded.QueueMusic, contentDescription = "Queue", tint = DhunColors.Text)
                }
            }

            // ---- cover
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                val side = if (maxWidth < maxHeight) maxWidth else maxHeight
                Box(
                    Modifier
                        .size(side)
                        .scale(artScale)
                        .offset { IntOffset(artX.value.roundToInt(), 0) }
                        .graphicsLayer {
                            rotationZ = artX.value / 60f
                            alpha = 1f - (abs(artX.value) / (skipAt * 4)).coerceIn(0f, 0.5f)
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        val x = artX.value
                                        if (x < -skipAt) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            PlayerConnection.next()
                                        } else if (x > skipAt) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            PlayerConnection.previous()
                                        }
                                        artX.animateTo(0f, spring(dampingRatio = 0.7f))
                                    }
                                },
                                onDragCancel = { scope.launch { artX.animateTo(0f) } },
                            ) { change, dx ->
                                change.consume()
                                scope.launch { artX.snapTo(artX.value + dx * 0.7f) }
                            }
                        }
                        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { toggleVinyl() }) },
                ) {
                    Crossfade(targetState = vinyl, animationSpec = tween(400), label = "coverMode") { isVinyl ->
                        if (isVinyl) {
                            VinylDisc(song = song, playing = playing, glow = accent, modifier = Modifier.fillMaxSize())
                        } else {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .shadow(44.dp, RoundedCornerShape(28.dp), ambientColor = accent, spotColor = accent),
                            ) {
                                Crossfade(targetState = song, animationSpec = tween(350), label = "cover") { s ->
                                    SongArt(s, Modifier.fillMaxSize(), corner = 28.dp, iconSize = 96.dp)
                                }
                            }
                        }
                    }
                }
            }

            // ---- title
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = song,
                    transitionSpec = {
                        (slideInVertically(tween(260)) { it / 3 } + fadeIn(tween(260))) togetherWith
                            (slideOutVertically(tween(200)) { -it / 3 } + fadeOut(tween(200)))
                    },
                    modifier = Modifier.weight(1f),
                    label = "title",
                ) { s ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            s.title,
                            color = DhunColors.Text,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                initialDelayMillis = 2500,
                                repeatDelayMillis = 3000,
                                velocity = 28.dp,
                            ),
                        )
                        Text(
                            s.artist,
                            color = DhunColors.Text.copy(alpha = 0.7f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                CircleIconButton(onClick = { toggleVinyl() }, label = if (vinyl) "Show cover" else "Show vinyl") {
                    Icon(
                        if (vinyl) Icons.Rounded.Image else Icons.Rounded.Album,
                        contentDescription = null,
                        tint = if (vinyl) accent else DhunColors.Text,
                    )
                }
                Spacer(Modifier.width(8.dp))
                CircleIconButton(onClick = onAddToPlaylist, label = "Add to playlist") {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = DhunColors.Text)
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---- seek
            WaveSeekBar(
                seed = song.id,
                value = shown,
                playing = playing,
                accent = accent,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { PlayerConnection.seekTo((it * duration).toLong()) }
                    dragging = null
                },
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatDuration((shown * duration).toLong()), color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("-" + formatDuration(((1f - shown) * duration).toLong()), color = DhunColors.Text.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(10.dp))

            // ---- controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToggleIcon(
                    activeColor = accent,
                    active = shuffleOn,
                    label = if (shuffleOn) "Smart shuffle on" else "Smart shuffle off",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleShuffle()
                    },
                ) { tint -> Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = tint) }

                IconButton(onClick = { PlayerConnection.previous() }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = DhunColors.Text, modifier = Modifier.size(44.dp))
                }

                MorphPlayButton(
                    playing = playing,
                    color = accent,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        PlayerConnection.playPause()
                    },
                )

                IconButton(onClick = { PlayerConnection.next() }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = DhunColors.Text, modifier = Modifier.size(44.dp))
                }

                val mode = PlayerConnection.repeatMode
                ToggleIcon(
                    activeColor = accent,
                    active = mode != Player.REPEAT_MODE_OFF,
                    label = when (mode) {
                        Player.REPEAT_MODE_ONE -> "Repeat one"
                        Player.REPEAT_MODE_ALL -> "Repeat all"
                        else -> "Repeat off"
                    },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        PlayerConnection.cycleRepeat()
                    },
                ) { tint ->
                    Icon(if (mode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, contentDescription = null, tint = tint)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- smart shuffle chip
            Box(Modifier.heightIn(min = 30.dp), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = shuffleOn && shuffleInfo != null,
                    enter = fadeIn() + scaleIn(initialScale = 0.9f),
                    exit = fadeOut() + scaleOut(targetScale = 0.9f),
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(15.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
                        Text(shuffleInfo ?: "", color = DhunColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- up next
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClickLabel = "Open queue", onClick = onOpenQueue)
                    .padding(start = 8.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SongArt(upNext, Modifier.size(42.dp), corner = 10.dp, iconSize = 16.dp)
                Column(Modifier.weight(1f)) {
                    Text("UP NEXT", color = DhunColors.Text.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text(
                        upNext?.let { "${it.title} · ${it.artist}" } ?: "Nothing after this song",
                        color = DhunColors.Text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = DhunColors.Text.copy(alpha = 0.6f))
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ToggleIcon(
    active: Boolean,
    label: String,
    onClick: () -> Unit,
    activeColor: Color = DhunColors.Accent,
    icon: @Composable (Color) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
            icon(if (active) activeColor else DhunColors.Text.copy(alpha = 0.7f))
        }
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (active) activeColor else Color.Transparent),
        )
    }
}

@Composable
private fun CircleIconButton(onClick: () -> Unit, label: String, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ---------------------------------------------------------------- Queue

@Composable
fun QueueScreen(byId: Map<Long, Song>, onClose: () -> Unit) {
    val ids = PlayerConnection.queueIds
    val current = PlayerConnection.currentIndex
    val currentSong = ids.getOrNull(current)?.let { byId[it] }
    val colors = rememberArtColors(currentSong)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = current.coerceAtLeast(0))
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to colors.dark, 0.35f to DhunColors.Bg))
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
            Text("Queue", color = DhunColors.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text(songCount(ids.size), color = DhunColors.Text.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(end = 14.dp))
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        ) {
            itemsIndexed(ids) { index, id ->
                val song = byId[id] ?: return@itemsIndexed
                val isCurrent = index == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isCurrent) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable { PlayerConnection.seekToIndex(index) }
                        .padding(horizontal = 8.dp)
                        .height(62.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(46.dp)) {
                        SongArt(song, Modifier.matchParentSize(), corner = 10.dp)
                        if (isCurrent) {
                            Box(
                                Modifier.matchParentSize().clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center,
                            ) { EqualizerBars(playing = PlayerConnection.isPlaying, color = DhunColors.Accent) }
                        }
                    }
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
                    Text(formatDuration(song.durationMs), color = DhunColors.Muted, fontSize = 12.sp)
                }
            }
        }
    }
}
