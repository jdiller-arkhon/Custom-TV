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
    background = Color(0xFFFBE8EE),
    pink = Color(0xFFF7C6D9),
    rose = Color(0xFFE58FB0),
    lilac = Color(0xFFD9C8F5),
    peach = Color(0xFFFFD3C4),
    text = Color(0xFF3B1F33),
    textSoft = Color(0xFF3B1F33).copy(alpha = 0.66f),
    glassFill = Color.White.copy(alpha = 0.40f),
    glassFillFocused = Color.White.copy(alpha = 0.62f),
    highlightTop = Color.White.copy(alpha = 0.85f),
    highlightBottom = Color.White.copy(alpha = 0.25f),
    shadow = Color(0xFFE58FB0).copy(alpha = 0.35f),
    orbAlpha = 0.85f,
)

val DarkColors = GlassColors(
    isDark = true,
    background = Color(0xFF1E1020),
    pink = Color(0xFFF07BAA),
    rose = Color(0xFFFF8FC0),
    lilac = Color(0xFFA98BEA),
    peach = Color(0xFFF2A58E),
    text = Color(0xFFFCEAF1),
    textSoft = Color(0xFFFCEAF1).copy(alpha = 0.66f),
    glassFill = Color.White.copy(alpha = 0.10f),
    glassFillFocused = Color.White.copy(alpha = 0.20f),
    highlightTop = Color.White.copy(alpha = 0.35f),
    highlightBottom = Color.White.copy(alpha = 0.06f),
    shadow = Color(0xFFFF8FC0).copy(alpha = 0.30f),
    orbAlpha = 0.55f,
)

val Fraunces = FontFamily(
    Font(R.font.fraunces_300, FontWeight.Light),
    Font(R.font.fraunces_400, FontWeight.Normal),
    Font(R.font.fraunces_600, FontWeight.SemiBold),
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
}

val LocalGlass = staticCompositionLocalOf { LightColors }

@Composable
fun GlassTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlass provides if (dark) DarkColors else LightColors, content = content)
}
