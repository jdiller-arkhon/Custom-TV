package dev.glasstv.launcher.ui.apps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.glasstv.launcher.ui.components.CuteOrb
import dev.glasstv.launcher.ui.theme.GlassType
import dev.glasstv.launcher.ui.theme.LocalGlass

/** Shared frame for every built-in app: dimmed backdrop, white card, title row, Back closes. */
@Composable
fun AppSheet(emoji: String, title: String, subtitle: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalGlass.current
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(c.background.copy(alpha = 0.86f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(36.dp).fillMaxSize()
                .shadow(24.dp, RoundedCornerShape(30.dp), ambientColor = c.shadow, spotColor = c.shadow)
                .background(c.glassFill, RoundedCornerShape(30.dp))
                .border(1.dp, c.highlightTop, RoundedCornerShape(30.dp))
                .padding(horizontal = 40.dp, vertical = 30.dp)
                .focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(emoji, style = GlassType.hero.copy(fontSize = GlassType.hero.fontSize * 0.7f))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    BasicText(title, style = GlassType.hero.copy(fontSize = GlassType.hero.fontSize * 0.62f, color = c.text))
                    BasicText(subtitle, style = GlassType.small.copy(color = c.textSoft))
                }
                CuteOrb(40.dp)
                Spacer(Modifier.width(14.dp))
                Btn("Done", onClick = onClose)
            }
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.highlightTop))
            Spacer(Modifier.height(18.dp))
            content()
        }
    }
}

/** Pill button in the app style. */
@Composable
fun Btn(text: String, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    val c = LocalGlass.current
    Surface(
        onClick = onClick, modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) c.rose else c.glassFill, contentColor = if (primary) Color.White else c.text,
            focusedContainerColor = if (primary) c.rose else c.pink, focusedContentColor = if (primary) Color.White else c.cocoa,
            pressedContainerColor = c.pink,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, if (primary) c.rose else c.highlightTop), shape = RoundedCornerShape(50)),
            focusedBorder = Border(BorderStroke(3.dp, if (primary) c.cocoa.copy(alpha = 0.35f) else c.rose), shape = RoundedCornerShape(50)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        glow = ClickableSurfaceDefaults.glow(Glow.None, Glow(c.shadow, 12.dp)),
    ) { Text(text, style = GlassType.label, modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)) }
}

/** A focusable row (list item, countdown, note…). Focus = soft pink fill. */
@Composable
fun RowCard(modifier: Modifier = Modifier, armed: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit) {
    val c = LocalGlass.current
    Surface(
        onClick = onClick, modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = c.background, focusedContainerColor = if (armed) c.rose else c.pink, pressedContainerColor = c.pink,
            contentColor = c.text, focusedContentColor = if (armed) Color.White else c.cocoa,
        ),
        border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(1.dp, c.highlightTop), shape = RoundedCornerShape(16.dp))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        glow = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
    ) { Box(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) { content() } }
}

@Composable
fun Hint(text: String) = BasicText(text, style = GlassType.small.copy(color = LocalGlass.current.textSoft))

/** Request initial focus once the sheet is up. */
@Composable
fun rememberInitialFocus(): FocusRequester {
    val f = remember { FocusRequester() }
    LaunchedEffect(Unit) { repeat(10) { kotlinx.coroutines.delay(40); if (runCatching { f.requestFocus() }.getOrDefault(false)) return@LaunchedEffect } }
    return f
}

fun Modifier.initialFocus(f: FocusRequester?) = if (f != null) this.focusRequester(f) else this
