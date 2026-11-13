package com.nikhil.dhun.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object DhunColors {
    val Bg = Color(0xFF0F0F13)
    val Surface = Color(0xFF1A1A22)
    val Surface2 = Color(0xFF23232D)
    val Chip = Color(0xFF24242E)
    val Line = Color(0xFF3A3A45)
    val Text = Color(0xFFF4F4F6)
    val Muted = Color(0xFFA0A0AE)
    val Accent = Color(0xFFFFB547)
    val OnAccent = Color(0xFF1F1400)
    val Track = Color(0xFF3A3A45)
}

private val artPalette = listOf(
    Color(0xFF5A3B22), Color(0xFF23414A), Color(0xFF3D2A4F), Color(0xFF4A2A2A),
    Color(0xFF2C3E2A), Color(0xFF283350), Color(0xFF4A3A24), Color(0xFF3A2440),
)

/** Stable placeholder colour per album, used when a song has no cover art. */
fun artColor(albumId: Long): Color {
    val n = artPalette.size
    val i = (((albumId % n) + n) % n).toInt()
    return artPalette[i]
}

@Composable
fun DhunTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = DhunColors.Accent,
            onPrimary = DhunColors.OnAccent,
            background = DhunColors.Bg,
            onBackground = DhunColors.Text,
            surface = DhunColors.Surface,
            onSurface = DhunColors.Text,
            surfaceVariant = DhunColors.Surface2,
            onSurfaceVariant = DhunColors.Muted,
            surfaceContainer = DhunColors.Surface2,
            surfaceContainerHigh = DhunColors.Surface2,
            surfaceContainerHighest = DhunColors.Surface2,
            outline = DhunColors.Line,
            secondaryContainer = DhunColors.Chip,
            onSecondaryContainer = DhunColors.Text,
        ),
        content = content,
    )
}
