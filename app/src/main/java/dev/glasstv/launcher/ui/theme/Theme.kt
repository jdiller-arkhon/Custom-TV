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
    /** Home panel gradient (the soft-pink "screen"). */
    val panelTop: Color = Color(0xFFFFF1F6),
    val panelBottom: Color = Color(0xFFFCE1EC),
    val orbAlpha: Float,
)

val LightColors = GlassColors(
    isDark = false,
    background = Color(0xFFFFF8FB),       // blush white
    pink = Color(0xFFFCE4EE),             // pink tint
    rose = Color(0xFFEC6AA0),             // accent
    lilac = Color(0xFFF3E8FA),
    peach = Color(0xFFFFEDE6),
    cocoa = Color(0xFF6B3D52),
    latte = Color(0xFFF1D9E3),
    gold = Color(0xFFD99AB5),
    text = Color(0xFF2A1A22),
    textSoft = Color(0xFF8A6F7B),
    glassFill = Color(0xFFFFFFFF),        // card
    glassFillFocused = Color(0xFFFFFFFF),
    highlightTop = Color(0xFFF6DCE6),     // 1px border
    highlightBottom = Color(0xFFF6DCE6),
    shadow = Color(0xFFEC6AA0).copy(alpha = 0.18f),
    orbAlpha = 0f,
)

val DarkColors = GlassColors(
    isDark = true,
    background = Color(0xFF161014),
    pink = Color(0xFF3A2230),
    rose = Color(0xFFF27AAE),
    lilac = Color(0xFF241C28),
    peach = Color(0xFF2A1E1E),
    cocoa = Color(0xFFB0728E),
    latte = Color(0xFF3A2A32),
    gold = Color(0xFFE7A9C4),
    text = Color(0xFFF8EEF2),
    textSoft = Color(0xFFB59AA7),
    glassFill = Color(0xFF1F181C),
    glassFillFocused = Color(0xFF281F24),
    highlightTop = Color(0xFF34262D),
    highlightBottom = Color(0xFF34262D),
    shadow = Color(0xFFF27AAE).copy(alpha = 0.20f),
    panelTop = Color(0xFF2A1D24),
    panelBottom = Color(0xFF221820),
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
    // Clean SaaS
    val hero = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, letterSpacing = (-1.1).sp)
    val heroSub = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 21.sp)
    val nav = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 16.sp)
    val wordmark = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.5).sp)
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
