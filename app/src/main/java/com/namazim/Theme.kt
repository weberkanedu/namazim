package com.namazim

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
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalTextApi::class)
val Cormorant = FontFamily(
    Font(R.font.cormorant, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

/** Reading face (body, buttons, nav). One variable file, four weights. */
@OptIn(ExperimentalTextApi::class)
val Inter = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(R.font.inter, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

class Palette(
    val id: String, val name: String,
    val ground: Color, val card: Color, val track: Color, val line: Color, val nav: Color,
    val accent: Color, val text: Color, val muted: Color, val warn: Color, val border: Color,
    val display: FontFamily,
)

val SeherPalette = Palette(
    "seher", "Seher", ground = Color(0xFF0B1322), card = Color(0xFF121D33), track = Color(0xFF1C2943),
    line = Color(0x1FF3EEE3), nav = Color(0xFF091020), accent = Color(0xFFE8BE74), text = Color(0xFFF3EEE3),
    muted = Color(0xFFC9D1DC), warn = Color(0xFFF4B08F), border = Color(0xFF2A3A52), display = Cormorant,
)

/**
 * Current theme colors (Seher is the only theme).
 */
object Pal {
    val current = SeherPalette
    val Ground get() = current.ground
    val Card get() = current.card
    val Track get() = current.track
    val Line get() = current.line
    val Nav get() = current.nav
    val Gold get() = current.accent
    val Text get() = current.text
    val Muted get() = current.muted
    val Warn get() = current.warn
    val Border get() = current.border
}

/** Display font with lining figures (Cormorant's default old-style "0" reads like the letter "O"). */
val NumStyle get() = TextStyle(fontFamily = Pal.current.display, fontFeatureSettings = "lnum")

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pal.Gold, onPrimary = Pal.Ground, background = Pal.Ground, onBackground = Pal.Text,
            surface = Pal.Card, onSurface = Pal.Text, surfaceVariant = Pal.Card, onSurfaceVariant = Pal.Muted,
            surfaceContainer = Pal.Nav, surfaceContainerHighest = Pal.Card, secondaryContainer = Pal.Track,
            onSecondaryContainer = Pal.Gold, outline = Pal.Border,
        ),
        // Inter everywhere by default; Cormorant only where a Text asks for NumStyle / display
        typography = Typography().run {
            // M3's default tracking (up to .5sp) is tuned for Roboto and reads loose in Inter
            fun TextStyle.inter() = copy(fontFamily = Inter, letterSpacing = 0.sp)
            copy(
                displayLarge.inter(), displayMedium.inter(), displaySmall.inter(),
                headlineLarge.inter(), headlineMedium.inter(), headlineSmall.inter(),
                titleLarge.inter(), titleMedium.inter(), titleSmall.inter(),
                bodyLarge.inter(), bodyMedium.inter(), bodySmall.inter(),
                labelLarge.inter(), labelMedium.inter(), labelSmall.inter(),
            )
        },
        content = content,
    )
}
