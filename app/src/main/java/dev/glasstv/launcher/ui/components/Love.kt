package dev.glasstv.launcher.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glasstv.launcher.Personal
import dev.glasstv.launcher.ui.theme.Fraunces
import dev.glasstv.launcher.ui.theme.Figtree
import dev.glasstv.launcher.ui.theme.LocalGlass
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** A soft heart centred on [center], [w] wide. */
fun DrawScope.drawHeart(center: Offset, w: Float, color: Color) {
    val h = w * 0.9f
    val x = center.x - w / 2; val y = center.y - h / 2
    val p = Path().apply {
        moveTo(x + w / 2, y + h)
        cubicTo(x - w * 0.15f, y + h * 0.55f, x + w * 0.05f, y - h * 0.05f, x + w / 2, y + h * 0.28f)
        cubicTo(x + w * 0.95f, y - h * 0.05f, x + w * 1.15f, y + h * 0.55f, x + w / 2, y + h)
        close()
    }
    drawPath(p, color)
}

/** "I love you, Anna — Justin ♡" in italic Fraunces with a slow rose-gold shimmer passing through it. */
@Composable
fun LoveNote(modifier: Modifier = Modifier, size: TextUnit = 26.sp, animate: Boolean = true, center: Boolean = false) {
    val c = LocalGlass.current
    val shine = if (!animate) 0.3f else rememberInfiniteTransition(label = "love").animateFloat(
        -0.5f, 1.5f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "shine",
    ).value
    val base = c.rose
    val brush = Brush.linearGradient(
        0f to base, (shine - 0.15f).coerceIn(0f, 1f) to base, shine.coerceIn(0f, 1f) to c.gold,
        (shine + 0.15f).coerceIn(0f, 1f) to base, 1f to base,
        start = Offset.Zero, end = Offset(900f, 0f),
    )
    Column(modifier, horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        BasicText(
            Personal.LOVE_NOTE,
            style = TextStyle(brush = brush, fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal, fontSize = size),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(
                "— ${Personal.LOVE_FROM}",
                style = TextStyle(color = c.gold, fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = size * 0.55f, letterSpacing = 2.sp),
            )
            Canvas(Modifier.size((size.value * 0.55f).dp)) { drawHeart(this.center, this.size.width, c.rose) }
        }
    }
}

/**
 * The welcome moment: a burst of hearts rising from the centre with the love note fading in,
 * then everything settles. ~2.6 s; skipped entirely with reduce motion.
 */
@Composable
fun HeartBurst(onDone: () -> Unit) {
    val c = LocalGlass.current
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        anim.animateTo(1f, tween(2600, easing = LinearEasing))
        onDone()
    }
    val t = anim.value
    val colors = listOf(c.rose, c.pink, c.gold, Color.White, c.lilac)
    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width / 2; val cy = size.height * 0.55f
        // a soft blush wash that fades out
        drawRect(c.pink.copy(alpha = 0.35f * (1f - t)))
        for (i in 0 until 28) {
            val ang = (i / 28f) * 2f * PI.toFloat() + i * 0.37f
            val speed = 0.45f + (i % 5) * 0.12f
            val d = t * speed * size.height * 0.7f
            val px = cx + kotlin.math.cos(ang) * d
            val py = cy + sin(ang) * d * 0.6f - t * t * size.height * 0.35f
            val a = (1f - t) * (if (t < 0.1f) t * 10f else 1f)
            drawHeart(Offset(px, py), 18f + (i % 4) * 9f, colors[i % colors.size].copy(alpha = a))
        }
    }
}

/** Grace with blinking eyes. Blinks for ~140 ms every few seconds. */
@Composable
fun BlinkingGrace(size: Dp, animate: Boolean, modifier: Modifier = Modifier) {
    var open by remember { mutableFloatStateOf(1f) }
    if (animate) LaunchedEffect(Unit) {
        while (true) {
            delay(2800L + (0..2400).random())
            open = 0.1f; delay(140); open = 1f
        }
    }
    CuteOrb(size, modifier, eyesOpen = open)
}

/** Small helper so other files can draw a heart filling their canvas. */
object HeartDraw {
    fun DrawScope.heart(color: Color) = drawHeart(center, size.width, color)
}
