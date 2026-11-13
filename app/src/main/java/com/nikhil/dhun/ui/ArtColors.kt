package com.nikhil.dhun.ui

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import com.nikhil.dhun.data.Song
import com.nikhil.dhun.ui.theme.DhunColors
import com.nikhil.dhun.ui.theme.artColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Colours pulled from a song's cover art. [dark] is safe behind white text. */
data class ArtColors(val dark: Color, val vibrant: Color)

fun Color.forBackground(): Color {
    var c = lerp(this, Color.Black, 0.25f)
    var guard = 0
    while (c.luminance() > 0.09f && guard < 12) {
        c = lerp(c, Color.Black, 0.2f)
        guard++
    }
    return c
}

/** A bright version of a colour, safe behind black icons. */
fun Color.forAccent(): Color {
    var c = this
    var guard = 0
    while (c.luminance() < 0.42f && guard < 14) {
        c = lerp(c, Color.White, 0.18f)
        guard++
    }
    return c
}

@Composable
fun rememberArtColors(song: Song?): ArtColors {
    val context = LocalContext.current
    var target by remember {
        mutableStateOf(ArtColors(artColor(song?.albumId ?: 0L).forBackground(), DhunColors.Accent))
    }
    LaunchedEffect(song?.albumId) {
        if (song == null) return@LaunchedEffect
        val fallback = artColor(song.albumId)
        val palette = withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(song.artUri)
                    .allowHardware(false)
                    .size(160)
                    .build()
                val result = context.imageLoader.execute(request)
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                bitmap?.let { Palette.from(it).generate() }
            } catch (e: Exception) {
                null
            }
        }
        target = if (palette != null) {
            val base = palette.getDarkVibrantColor(palette.getDominantColor(fallback.toArgb()))
            val vivid = palette.getVibrantColor(palette.getLightVibrantColor(DhunColors.Accent.toArgb()))
            ArtColors(Color(base).forBackground(), Color(vivid))
        } else {
            ArtColors(fallback.forBackground(), DhunColors.Accent)
        }
    }
    val dark by animateColorAsState(target.dark, tween(700), label = "artDark")
    val vibrant by animateColorAsState(target.vibrant, tween(700), label = "artVibrant")
    return ArtColors(dark, vibrant)
}
