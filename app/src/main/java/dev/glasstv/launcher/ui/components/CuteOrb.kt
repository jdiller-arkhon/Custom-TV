package dev.glasstv.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import dev.glasstv.launcher.ui.theme.LocalGlass

/**
 * Grace: a round pink bunny-blob with floppy ears, a little bow, big sparkly eyes, blush and a tiny smile.
 * [eyesOpen] 1 = open, ~0 = blink.
 */
@Composable
fun CuteOrb(size: Dp, modifier: Modifier = Modifier, eyesOpen: Float = 1f) {
    val c = LocalGlass.current
    val body = Color(0xFFFFC6DC)
    val bodyDeep = Color(0xFFF59BBF)
    val ink = Color(0xFF4A2335)
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val r = w * 0.36f
        val ctr = Offset(w / 2, w * 0.6f)

        // Ears (one floppy), drawn behind the head.
        fun ear(cx: Float, angle: Float) = rotate(angle, Offset(cx, ctr.y - r * 0.55f)) {
            drawOval(bodyDeep, Offset(cx - r * 0.24f, ctr.y - r * 1.55f), Size(r * 0.48f, r * 1.05f))
            drawOval(Color(0xFFFFE3EE), Offset(cx - r * 0.12f, ctr.y - r * 1.4f), Size(r * 0.24f, r * 0.75f))
        }
        ear(ctr.x - r * 0.45f, -14f)
        ear(ctr.x + r * 0.45f, 32f)

        // Head with a soft top-light.
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFFE5F0), body, bodyDeep), center = Offset(ctr.x - r * 0.3f, ctr.y - r * 0.4f), radius = r * 1.6f),
            r, ctr,
        )

        // Bow on the left ear: two loops and a knot.
        val bow = Offset(ctr.x - r * 0.62f, ctr.y - r * 0.82f)
        val bowC = c.rose
        drawPath(Path().apply {
            moveTo(bow.x, bow.y); lineTo(bow.x - r * 0.38f, bow.y - r * 0.2f); lineTo(bow.x - r * 0.38f, bow.y + r * 0.22f); close()
            moveTo(bow.x, bow.y); lineTo(bow.x + r * 0.38f, bow.y - r * 0.2f); lineTo(bow.x + r * 0.38f, bow.y + r * 0.22f); close()
        }, bowC)
        drawCircle(Color(0xFFFFE3EE), r * 0.09f, bow)

        // Big sparkly eyes (squash to blink).
        val eyeY = ctr.y + r * 0.05f
        val open = eyesOpen.coerceIn(0.06f, 1f)
        listOf(ctr.x - r * 0.38f, ctr.x + r * 0.38f).forEach { x ->
            if (open > 0.3f) {
                drawOval(ink, Offset(x - r * 0.15f, eyeY - r * 0.19f * open), Size(r * 0.3f, r * 0.38f * open))
                drawCircle(Color.White, r * 0.07f, Offset(x - r * 0.05f, eyeY - r * 0.08f * open))
                drawCircle(Color.White, r * 0.035f, Offset(x + r * 0.06f, eyeY + r * 0.06f * open))
            } else {
                drawArc(ink, 20f, 140f, false, Offset(x - r * 0.15f, eyeY - r * 0.08f), Size(r * 0.3f, r * 0.16f), style = Stroke(r * 0.07f, cap = StrokeCap.Round))
            }
        }
        // Blush.
        listOf(ctr.x - r * 0.62f, ctr.x + r * 0.62f).forEach { x ->
            drawOval(Color(0xFFFF7FAE).copy(alpha = 0.45f), Offset(x - r * 0.15f, eyeY + r * 0.2f), Size(r * 0.3f, r * 0.16f))
        }
        // Tiny "w" smile.
        val my = eyeY + r * 0.32f
        drawArc(ink, 10f, 160f, false, Offset(ctr.x - r * 0.16f, my - r * 0.06f), Size(r * 0.16f, r * 0.13f), style = Stroke(r * 0.055f, cap = StrokeCap.Round))
        drawArc(ink, 10f, 160f, false, Offset(ctr.x, my - r * 0.06f), Size(r * 0.16f, r * 0.13f), style = Stroke(r * 0.055f, cap = StrokeCap.Round))
    }
}
