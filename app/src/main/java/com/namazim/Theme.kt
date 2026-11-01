package com.namazim

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalTextApi::class)
val Cormorant = FontFamily(
    Font(R.font.cormorant, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

@OptIn(ExperimentalTextApi::class)
val SpaceGrotesk = FontFamily(
    Font(R.font.spacegrotesk, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(650))),
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

val OdakPalette = Palette(
    "odak", "Odak", ground = Color(0xFF0B0C0E), card = Color(0xFF16181C), track = Color(0xFF24272D),
    line = Color(0x1FF5F5F2), nav = Color(0xFF0B0C0E), accent = Color(0xFFC6F45E), text = Color(0xFFF5F5F2),
    muted = Color(0xFFA0A4AB), warn = Color(0xFFFF8A4C), border = Color(0xFF3A3D44), display = SpaceGrotesk,
)

/**
 * Current theme colors. Backed by Compose state, so switching the theme in Ayarlar recomposes every screen.
 * ("Gold" is the accent: gold in Seher, lime in Odak.)
 */
object Pal {
    var current by mutableStateOf(SeherPalette)
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
    fun byId(id: String) = if (id == OdakPalette.id) OdakPalette else SeherPalette
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
        content = content,
    )
}
