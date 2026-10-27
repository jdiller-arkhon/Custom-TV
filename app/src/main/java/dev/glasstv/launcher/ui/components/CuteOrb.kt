package dev.glasstv.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.glasstv.launcher.ui.theme.LocalGlass

/** The assistant's face: a glossy pink orb with sleepy happy eyes and blush cheeks. */
@Composable
fun CuteOrb(size: Dp, modifier: Modifier = Modifier) {
    val c = LocalGlass.current
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        val ctr = Offset(r, r)
        drawCircle(
            Brush.radialGradient(listOf(Color.White, c.pink, c.rose, c.cocoa.copy(alpha = 0.9f)), center = Offset(r * 0.7f, r * 0.6f), radius = r * 1.5f),
            r, ctr,
        )
        val eyeY = r * 1.02f
        val ink = c.cocoa
        val w = r * 0.09f
        // ^ ^ closed happy eyes (arcs)
        listOf(r * 0.66f, r * 1.34f).forEach { x ->
            drawArc(ink, 200f, 140f, false, Offset(x - r * 0.16f, eyeY - r * 0.12f), Size(r * 0.32f, r * 0.26f), style = Stroke(w, cap = StrokeCap.Round))
        }
        // blush
        listOf(r * 0.48f, r * 1.52f).forEach { x ->
            drawOval(c.rose.copy(alpha = 0.55f), Offset(x - r * 0.13f, eyeY + r * 0.16f), Size(r * 0.26f, r * 0.14f))
        }
        // tiny smile
        drawArc(ink, 20f, 140f, false, Offset(r - r * 0.12f, eyeY + r * 0.08f), Size(r * 0.24f, r * 0.18f), style = Stroke(w * 0.9f, cap = StrokeCap.Round))
        // gloss
        drawCircle(Color.White.copy(alpha = 0.7f), r * 0.12f, Offset(r * 0.62f, r * 0.5f))
    }
}
