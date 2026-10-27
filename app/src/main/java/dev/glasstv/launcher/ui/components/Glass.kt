package dev.glasstv.launcher.ui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import dev.glasstv.launcher.ui.theme.GlassColors
import dev.glasstv.launcher.ui.theme.GlassType
import dev.glasstv.launcher.ui.theme.LocalGlass

const val FOCUS_SCALE = 1.08f

fun highlightBrush(c: GlassColors) = Brush.verticalGradient(listOf(c.highlightTop, c.highlightBottom))

/** Static glass panel: translucent white, 1px inner highlight, soft pink-tinted (never grey) shadow. */
@Composable
fun Modifier.glassPanel(shape: Shape = RoundedCornerShape(28.dp), elevation: Dp = 20.dp): Modifier {
    val c = LocalGlass.current
    // Coloured shadows need API 28; below that a grey shadow would break the palette, so skip it.
    val shadowed = if (Build.VERSION.SDK_INT >= 28) this.shadow(elevation, shape, ambientColor = c.shadow, spotColor = c.shadow) else this
    return shadowed.clip(shape).background(c.glassFill).border(1.dp, highlightBrush(c), shape)
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    padding: PaddingValues = PaddingValues(24.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.glassPanel(shape).padding(padding), content = content)
}

/**
 * Focusable glass surface used everywhere a D-pad can land. Focus = 1.08x scale, solid rose ring
 * and a pink glow, so it is unmistakable from the couch.
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(24.dp),
    focusScale: Float = FOCUS_SCALE,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalGlass.current
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) c.pink.copy(alpha = 0.75f) else c.glassFill,
            contentColor = c.text,
            focusedContainerColor = if (selected) c.pink else c.glassFillFocused,
            focusedContentColor = c.text,
            pressedContainerColor = c.pink,
            pressedContentColor = c.text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusScale, pressedScale = focusScale * 0.97f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, highlightBrush(c)), shape = shape),
            focusedBorder = Border(BorderStroke(3.dp, c.rose), shape = shape),
            pressedBorder = Border(BorderStroke(3.dp, c.rose), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(
            glow = Glow(c.shadow, 6.dp),
            focusedGlow = Glow(c.rose.copy(alpha = 0.85f), 22.dp),
            pressedGlow = Glow(c.rose, 14.dp),
        ),
    ) {
        // Centre content in the surface (the surface's own box is top-start aligned).
        Box(Modifier.align(Alignment.Center), contentAlignment = Alignment.Center, content = content)
    }
}

@Composable
fun GlassText(
    text: String,
    style: TextStyle = GlassType.body,
    modifier: Modifier = Modifier,
    color: Color = LocalGlass.current.text,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(text, modifier, style.copy(color = color), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    GlassButton(onClick, modifier, shape = RoundedCornerShape(50), selected = selected) {
        GlassText(text, GlassType.label, Modifier.padding(horizontal = 22.dp, vertical = 12.dp), maxLines = 1)
    }
}
