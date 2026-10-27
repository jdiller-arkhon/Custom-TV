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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import dev.glasstv.launcher.ui.theme.LocalGlass
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private class Orb(val x: Float, val y: Float, val r: Float, val dx: Float, val dy: Float, val speed: Int, val phase: Float)

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
fun OrbBackground(reduceMotion: Boolean, extraBlur: Boolean, modifier: Modifier = Modifier) {
    val c = LocalGlass.current
    val colors = remember(c) { listOf(c.pink, c.lilac, c.latte, c.rose) }
    val t: State<Float> = if (reduceMotion) {
        remember { mutableFloatStateOfConst(0.12f) }
    } else {
        rememberInfiniteTransition(label = "orbs").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(90_000, easing = LinearEasing), RepeatMode.Restart),
            label = "orbT",
        )
    }
    val blur = extraBlur && Build.VERSION.SDK_INT >= 31
    Canvas(
        modifier.fillMaxSize().then(
            if (blur) Modifier.graphicsLayer { renderEffect = BlurEffect(60f, 60f, TileMode.Decal) } else Modifier
        )
    ) {
        drawRect(c.background)
        val time = t.value
        val dim = maxOf(size.width, size.height)
        orbs.forEachIndexed { i, o ->
            val a = 2f * PI.toFloat() * (time * o.speed + o.phase)
            val center = Offset(
                (o.x + o.dx * sin(a)) * size.width,
                (o.y + o.dy * cos(a * 0.8f)) * size.height,
            )
            val radius = o.r * dim
            val color = colors[i]
            drawCircle(
                brush = Brush.radialGradient(
                    0f to color.copy(alpha = c.orbAlpha),
                    0.45f to color.copy(alpha = c.orbAlpha * 0.45f),
                    1f to Color.Transparent,
                    center = center, radius = radius,
                ),
                radius = radius, center = center,
            )
        }
    }
}

private fun mutableFloatStateOfConst(v: Float): State<Float> = object : State<Float> { override val value = v }
