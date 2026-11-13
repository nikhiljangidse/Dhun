package com.nikhil.dhun.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nikhil.dhun.data.Song
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ---------------------------------------------------------------- Waveform seek bar

/** A stable, song-specific "waveform" shape (generated from the song id, no decoding needed). */
private fun waveform(seed: Long, count: Int): FloatArray {
    val r = java.util.Random(seed * 31 + 7)
    val raw = FloatArray(count) { 0.25f + r.nextFloat() * 0.75f }
    val out = FloatArray(count)
    for (i in 0 until count) {
        val a = raw[max(0, i - 1)]
        val b = raw[i]
        val c = raw[min(count - 1, i + 1)]
        val envelope = 0.5f + 0.5f * sin(PI * (i + 0.5) / count).toFloat()
        out[i] = ((a + 2 * b + c) / 4f * envelope).coerceIn(0.14f, 1f)
    }
    return out
}

/**
 * Seek bar drawn as a waveform. Played part glows in the song's colour, bars breathe while
 * music plays, and they stretch when you touch them. Tap or drag anywhere to seek.
 */
@Composable
fun WaveSeekBar(
    seed: Long,
    value: Float,
    playing: Boolean,
    accent: Color,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    barCount: Int = 48,
    height: Dp = 58.dp,
) {
    val bars = remember(seed, barCount) { waveform(seed, barCount) }
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    var active by remember { mutableStateOf(false) }
    var width by remember { mutableIntStateOf(1) }

    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "phase",
    )
    val energy by animateFloatAsState(if (playing) 1f else 0.45f, tween(500), label = "energy")
    val stretch by animateFloatAsState(if (active) 1.18f else 1f, spring(dampingRatio = 0.6f), label = "stretch")

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
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
                    onPress = {
                        active = true
                        tryAwaitRelease()
                        active = false
                    },
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
    ) {
        val n = bars.size
        val gap = 3.dp.toPx()
        val barW = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        val playedX = value * size.width
        val mid = size.height / 2f
        val dim = Color.White.copy(alpha = 0.22f)

        for (i in 0 until n) {
            val x = i * (barW + gap)
            val wobble = 0.10f * energy * sin(phase + i * 0.55f)
            val hFrac = ((bars[i] * (0.55f + 0.45f * energy) + wobble) * stretch).coerceIn(0.08f, 1f)
            val h = hFrac * size.height
            val top = mid - h / 2f
            val radius = CornerRadius(barW / 2f, barW / 2f)
            drawRoundRect(dim, Offset(x, top), Size(barW, h), radius)
            if (x < playedX) {
                clipRect(right = playedX) {
                    drawRoundRect(accent, Offset(x, top), Size(barW, h), radius)
                }
            }
        }
        // playhead
        drawCircle(accent.copy(alpha = 0.25f), radius = 13.dp.toPx(), center = Offset(playedX, mid))
        drawLine(
            color = Color.White,
            start = Offset(playedX, 2.dp.toPx()),
            end = Offset(playedX, size.height - 2.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

// ---------------------------------------------------------------- Vinyl record

/** The cover as a spinning vinyl record. Spins while playing, eases to a stop on pause. */
@Composable
fun VinylDisc(song: Song, playing: Boolean, glow: Color, modifier: Modifier = Modifier) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (playing) {
            while (true) {
                val start = rotation.value % 360f
                rotation.snapTo(start)
                rotation.animateTo(start + 360f, tween(7000, easing = LinearEasing))
            }
        } else {
            rotation.animateTo(rotation.value + 25f, tween(900))
        }
    }
    Box(
        modifier = modifier.shadow(40.dp, CircleShape, ambientColor = glow, spotColor = glow),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation.value },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                drawCircle(Color(0xFF0D0D10), radius = r)
                // grooves
                var gr = r * 0.97f
                var k = 0
                while (gr > r * 0.46f) {
                    drawCircle(
                        Color.White.copy(alpha = if (k % 3 == 0) 0.07f else 0.035f),
                        radius = gr,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    gr -= r * 0.022f
                    k++
                }
                drawCircle(glow.copy(alpha = 0.55f), radius = r * 0.44f, style = Stroke(width = 2.dp.toPx()))
            }
            SongArt(
                song,
                Modifier
                    .fillMaxSize(0.42f)
                    .clip(CircleShape),
                corner = 999.dp,
                iconSize = 36.dp,
            )
            Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFF0D0D10)))
        }
        // light reflection (does not rotate)
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.10f to Color.White.copy(alpha = 0.10f),
                    0.20f to Color.Transparent,
                    0.55f to Color.Transparent,
                    0.62f to Color.White.copy(alpha = 0.06f),
                    0.72f to Color.Transparent,
                    1f to Color.Transparent,
                ),
                radius = size.minDimension / 2f,
            )
        }
    }
}

// ---------------------------------------------------------------- Play button

/** Big play button that morphs: circle when paused, soft square when playing. */
@Composable
fun MorphPlayButton(playing: Boolean, color: Color, onClick: () -> Unit, size: Dp = 84.dp) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.45f), label = "pressScale")
    val corner by animateDpAsState(if (playing) size * 0.3f else size / 2, spring(dampingRatio = 0.6f), label = "corner")
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .shadow(26.dp, shape, ambientColor = color, spotColor = color)
            .clip(shape)
            .background(color)
            .clickable(interactionSource = press, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PlayPauseGlyph(playing, Color(0xFF111114), (size.value * 0.48f).toInt())
    }
}

// ---------------------------------------------------------------- Background

/** Slowly drifting, blurred cover behind the player (Android 12+), over the art colour. */
@Composable
fun AmbientBackground(song: Song, base: Color) {
    Box(Modifier.fillMaxSize().background(base)) {
        if (Build.VERSION.SDK_INT >= 31) {
            val t = rememberInfiniteTransition(label = "ambient")
            val zoom by t.animateFloat(1.25f, 1.55f, infiniteRepeatable(tween(18000), RepeatMode.Reverse), label = "zoom")
            val turn by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(26000), RepeatMode.Reverse), label = "turn")
            AsyncImage(
                model = song.artUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        rotationZ = turn
                    }
                    .blur(110.dp)
                    .alpha(0.55f),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.10f),
                        0.5f to base.copy(alpha = 0.45f),
                        1f to Color(0xFF0B0B0F),
                    )
                ),
        )
    }
}
