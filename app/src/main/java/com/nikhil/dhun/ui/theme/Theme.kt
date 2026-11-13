package com.nikhil.dhun.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.nikhil.dhun.R

object DhunColors {
    val Bg = Color(0xFF0B0B0F)
    val Surface = Color(0xFF17171E)
    val Surface2 = Color(0xFF22222B)
    val Chip = Color(0xFF22222B)
    val Line = Color(0xFF34343F)
    val Text = Color(0xFFF5F5F7)
    val Muted = Color(0xFFA3A3B2)
    val Accent = Color(0xFFFFB547)
    val OnAccent = Color(0xFF1F1400)
    val Track = Color(0xFF34343F)
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

/** Plus Jakarta Sans (variable font, OFL) for the whole app. */
@OptIn(ExperimentalTextApi::class)
val Jakarta = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(
            R.font.plus_jakarta_sans,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    }
)

private fun TextStyle.jakarta() = copy(fontFamily = Jakarta)

private val base = Typography()

private val DhunTypography = Typography(
    displayLarge = base.displayLarge.jakarta(),
    displayMedium = base.displayMedium.jakarta(),
    displaySmall = base.displaySmall.jakarta(),
    headlineLarge = base.headlineLarge.jakarta(),
    headlineMedium = base.headlineMedium.jakarta(),
    headlineSmall = base.headlineSmall.jakarta(),
    titleLarge = base.titleLarge.jakarta(),
    titleMedium = base.titleMedium.jakarta(),
    titleSmall = base.titleSmall.jakarta(),
    bodyLarge = base.bodyLarge.jakarta(),
    bodyMedium = base.bodyMedium.jakarta(),
    bodySmall = base.bodySmall.jakarta(),
    labelLarge = base.labelLarge.jakarta(),
    labelMedium = base.labelMedium.jakarta(),
    labelSmall = base.labelSmall.jakarta(),
)

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
        typography = DhunTypography,
        content = content,
    )
}
