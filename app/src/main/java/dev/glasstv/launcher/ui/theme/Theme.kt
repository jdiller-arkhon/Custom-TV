package dev.glasstv.launcher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.glasstv.launcher.R

@Immutable
data class GlassColors(
    val isDark: Boolean,
    val background: Color,
    val pink: Color,
    val rose: Color,
    val lilac: Color,
    val peach: Color,
    /** Dark-brown accent: ink, latte orb, deep ends of gradients. */
    val cocoa: Color,
    val latte: Color,
    /** Champagne gold: hairlines, monogram, signature. */
    val gold: Color,
    val text: Color,
    val textSoft: Color,
    val glassFill: Color,
    val glassFillFocused: Color,
    val highlightTop: Color,
    val highlightBottom: Color,
    val shadow: Color,
    val orbAlpha: Float,
)

val LightColors = GlassColors(
    isDark = false,
    background = Color(0xFFF6EFE9),       // ivory paper
    pink = Color(0xFFF2D4D7),             // blush
    rose = Color(0xFFB0304A),             // rouge
    lilac = Color(0xFFE7DCE4),
    peach = Color(0xFFF1DED2),
    cocoa = Color(0xFF3A2226),
    latte = Color(0xFFD9C3B4),
    gold = Color(0xFFB08A50),             // champagne
    text = Color(0xFF1C1416),             // ink
    textSoft = Color(0xFF1C1416).copy(alpha = 0.58f),
    glassFill = Color(0xFFFFFBF8),        // paper card
    glassFillFocused = Color(0xFFFFFFFF),
    highlightTop = Color(0xFFE4D6CB),     // printed hairline
    highlightBottom = Color(0xFFE4D6CB),
    shadow = Color(0xFFB0304A).copy(alpha = 0.16f),
    orbAlpha = 0f,
)

val DarkColors = GlassColors(
    isDark = true,
    background = Color(0xFF141012),       // velvet noir
    pink = Color(0xFF4A2630),
    rose = Color(0xFFE0607A),
    lilac = Color(0xFF2A2026),
    peach = Color(0xFF2E2220),
    cocoa = Color(0xFF6B3A44),
    latte = Color(0xFF5A4438),
    gold = Color(0xFFD9BB86),
    text = Color(0xFFF3E9E2),
    textSoft = Color(0xFFF3E9E2).copy(alpha = 0.6f),
    glassFill = Color(0xFF1E181A),
    glassFillFocused = Color(0xFF2A2124),
    highlightTop = Color(0xFF3A2E31),
    highlightBottom = Color(0xFF3A2E31),
    shadow = Color(0xFFE0607A).copy(alpha = 0.18f),
    orbAlpha = 0f,
)

val Fraunces = FontFamily(
    Font(R.font.fraunces_300, FontWeight.Light),
    Font(R.font.fraunces_400, FontWeight.Normal),
    Font(R.font.fraunces_600, FontWeight.SemiBold),
    Font(R.font.fraunces_italic_400, FontWeight.Normal, androidx.compose.ui.text.font.FontStyle.Italic),
)

val Figtree = FontFamily(
    Font(R.font.figtree_400, FontWeight.Normal),
    Font(R.font.figtree_500, FontWeight.Medium),
    Font(R.font.figtree_600, FontWeight.SemiBold),
    Font(R.font.figtree_700, FontWeight.Bold),
)

/** Sized for a 55" screen viewed from ~10 ft (TV renders at 960x540 dp). */
object GlassType {
    val clock = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Light, fontSize = 72.sp, letterSpacing = (-1).sp)
    val ambientClock = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Light, fontSize = 168.sp, letterSpacing = (-4).sp)
    val greeting = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Normal, fontSize = 30.sp)
    val title = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
    val section = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 1.5.sp)
    val body = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 28.sp)
    val label = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 17.sp)
    val small = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 15.sp)
    // Editorial
    val masthead = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Light, fontSize = 118.sp, letterSpacing = 14.sp, lineHeight = 112.sp)
    val kicker = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 4.sp)
    val coverTitle = TextStyle(fontFamily = Fraunces, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontWeight = FontWeight.Normal, fontSize = 64.sp, lineHeight = 64.sp)
    val contents = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Normal, fontSize = 27.sp)
    val folio = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 2.sp)
}

val LocalGlass = staticCompositionLocalOf { LightColors }

@Composable
fun GlassTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlass provides if (dark) DarkColors else LightColors, content = content)
}
