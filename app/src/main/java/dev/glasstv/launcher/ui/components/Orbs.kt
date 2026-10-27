package dev.glasstv.launcher.ui.components

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.glasstv.launcher.ui.theme.LocalGlass
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private class Orb(val x: Float, val y: Float, val r: Float, val dx: Float, val dy: Float, val speed: Int, val phase: Float)

private class Sparkle(val x: Float, val y: Float, val size: Float, val speed: Int, val phase: Float)

private val sparkles = listOf(
    Sparkle(0.08f, 0.40f, 0.0045f, 7, 0.0f), Sparkle(0.93f, 0.12f, 0.0055f, 5, 0.3f),
    Sparkle(0.47f, 0.07f, 0.0035f, 9, 0.6f), Sparkle(0.97f, 0.62f, 0.0040f, 6, 0.15f),
    Sparkle(0.04f, 0.88f, 0.0050f, 8, 0.8f), Sparkle(0.62f, 0.95f, 0.0035f, 7, 0.45f),
    Sparkle(0.80f, 0.40f, 0.0030f, 10, 0.7f),
)

private val orbs = listOf(
    Orb(0.18f, 0.22f, 0.42f, 0.10f, 0.08f, 1, 0.00f), // pink
    Orb(0.82f, 0.30f, 0.38f, 0.08f, 0.10f, 1, 0.33f), // lilac
    Orb(0.60f, 0.86f, 0.36f, 0.12f, 0.06f, 2, 0.66f), // latte (brown)
    Orb(0.30f, 0.80f, 0.28f, 0.07f, 0.09f, 1, 0.50f), // rose
)

/**
 * The one signature motion: slowly drifting soft orbs.
 *
 * Orbs are radial gradients that fade to transparent, so they are already "pre-blurred" — no
 * RenderEffect needed and no offscreen buffers. The animated value is read only in the draw
 * phase, so a frame costs four gradient fills and zero recompositions.
 */
@Composable
fun OrbBackground(reduceMotion: Boolean, extraBlur: Boolean, modifier: Modifier = Modifier, accent: Color? = null) {
    // Clean SaaS backdrop: blush-white with a faint dot grid and a soft pink glow at the top
    // (Vercel/Perplexity-style hero light). Static; the glow leans toward the focused app's colour.
    val c = LocalGlass.current
    val glow by androidx.compose.animation.animateColorAsState(
        androidx.compose.ui.graphics.lerp(c.rose, accent ?: c.rose, 0.35f).copy(alpha = if (c.isDark) 0.22f else 0.16f), tween(900), label = "glow",
    )
    Canvas(modifier.fillMaxSize()) {
        drawRect(c.background)
        val step = 28.dp.toPx()
        val dot = (if (c.isDark) Color.White else c.cocoa).copy(alpha = if (c.isDark) 0.05f else 0.07f)
        var y = step / 2
        while (y < size.height) {
            var x = step / 2
            while (x < size.width) { drawCircle(dot, 1.2.dp.toPx(), Offset(x, y)); x += step }
            y += step
        }
        drawRect(
            Brush.radialGradient(
                listOf(glow, Color.Transparent),
                center = Offset(size.width / 2, -size.height * 0.05f), radius = size.width * 0.55f,
            )
        )
        // fade the dots out toward the bottom so the page feels calm
        drawRect(Brush.verticalGradient(0.45f to Color.Transparent, 1f to c.background))
    }
}
