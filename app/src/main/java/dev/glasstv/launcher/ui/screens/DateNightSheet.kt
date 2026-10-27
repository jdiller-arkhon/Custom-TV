package dev.glasstv.launcher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.glasstv.launcher.GlassApp
import dev.glasstv.launcher.data.DateNight
import dev.glasstv.launcher.ui.components.CuteOrb
import dev.glasstv.launcher.ui.theme.Fraunces
import dev.glasstv.launcher.ui.theme.GlassType
import dev.glasstv.launcher.ui.theme.LocalGlass
import kotlinx.coroutines.delay

/** Titles picked recently this session, so a spin never repeats the last few. */
private val recentPicks = ArrayDeque<String>()

/**
 * Date Night: a slot-machine spin through titles that lands on a random pick for Justin & Anna.
 * Works fully offline; "Ask Grace" hands the pick to the assistant for details and where to stream it.
 */
@Composable
fun DateNightSheet(app: GlassApp, reduceMotion: Boolean, onAskGrace: (String) -> Unit, onClose: () -> Unit) {
    val c = LocalGlass.current
    var spin by remember { mutableIntStateOf(0) }
    var pick by remember { mutableStateOf<DateNight.Pick?>(null) }
    var flicker by remember { mutableStateOf("") }
    val pop = remember { Animatable(1f) }
    val again = remember { FocusRequester() }
    BackHandler(onBack = onClose)

    LaunchedEffect(spin) {
        pick = null
        val result = DateNight.pick(recentPicks)
        recentPicks.addLast(result.title); if (recentPicks.size > 8) recentPicks.removeFirst()
        if (!reduceMotion) {
            // Slot-machine: titles flash by, slowing down before the reveal.
            var d = 45L
            while (d < 260) {
                flicker = DateNight.catalog.random().title
                delay(d); d = (d * 1.18f).toLong()
            }
            pop.snapTo(0.7f)
        }
        pick = result
        if (!reduceMotion) pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
        runCatching { again.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(c.background.copy(alpha = 0.78f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(720.dp)
                .shadow(28.dp, RoundedCornerShape(32.dp), ambientColor = c.shadow, spotColor = c.shadow)
                .background(c.glassFill, RoundedCornerShape(32.dp))
                .border(1.dp, c.highlightTop, RoundedCornerShape(32.dp))
                .padding(horizontal = 44.dp, vertical = 34.dp)
                .focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CuteOrb(44.dp)
                Spacer(Modifier.width(12.dp))
                BasicText("Date Night  💕", style = GlassType.hero.copy(fontSize = GlassType.hero.fontSize * 0.62f, color = c.text))
            }
            Spacer(Modifier.height(4.dp))
            BasicText("Justin likes action & comedy · Anna likes romance", style = GlassType.small.copy(color = c.textSoft))
            Spacer(Modifier.height(26.dp))

            val p = pick
            if (p == null) {
                BasicText(flicker, style = GlassType.hero.copy(fontSize = GlassType.hero.fontSize * 0.8f, color = c.textSoft, textAlign = TextAlign.Center), maxLines = 1)
                Spacer(Modifier.height(110.dp))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tag(p.whose.label, filled = true)
                        Tag(p.genre)
                        Tag("${p.kind.label} · ${p.year}")
                    }
                    Spacer(Modifier.height(16.dp))
                    BasicText(
                        p.title, maxLines = 2,
                        style = GlassType.hero.copy(fontFamily = Fraunces, fontSize = GlassType.hero.fontSize * 1.05f, color = c.text, textAlign = TextAlign.Center),
                    )
                    Spacer(Modifier.height(10.dp))
                    BasicText(p.why, style = GlassType.heroSub.copy(color = c.textSoft, textAlign = TextAlign.Center))
                }
            }

            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Action("🎲  Spin again", primary = true, modifier = Modifier.focusRequester(again)) { spin++ }
                Action("✨  Ask Grace about it") {
                    pick?.let { onAskGrace("Tell me about \"${it.title}\" (${it.year}) in two sentences, no spoilers, and where it's usually available to stream (say if you're not sure).") }
                }
                Action("🍿  Save") { pick?.let { app.home.addWatch(dev.glasstv.launcher.data.WatchItem(title = it.title, year = it.year, kind = it.kind.label)) } }
                Action("Done") { onClose() }
            }
        }
    }
}

@Composable
private fun Tag(text: String, filled: Boolean = false) {
    val c = LocalGlass.current
    Box(
        Modifier.background(if (filled) c.rose else c.pink, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 5.dp)
    ) { BasicText(text, style = GlassType.small.copy(color = if (filled) Color.White else c.cocoa)) }
}

@Composable
private fun Action(text: String, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalGlass.current
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) c.rose else c.glassFill,
            contentColor = if (primary) Color.White else c.text,
            focusedContainerColor = if (primary) c.rose else c.pink,
            focusedContentColor = if (primary) Color.White else c.cocoa,
            pressedContainerColor = c.pink,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, if (primary) c.rose else c.highlightTop), shape = RoundedCornerShape(50)),
            focusedBorder = Border(BorderStroke(3.dp, if (primary) c.cocoa.copy(alpha = 0.35f) else c.rose), shape = RoundedCornerShape(50)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        glow = ClickableSurfaceDefaults.glow(Glow.None, Glow(c.shadow, 14.dp)),
    ) {
        Text(text, style = GlassType.label, modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp))
    }
}
