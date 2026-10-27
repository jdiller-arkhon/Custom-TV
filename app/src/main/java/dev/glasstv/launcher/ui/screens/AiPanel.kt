package dev.glasstv.launcher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.glasstv.launcher.GlassApp
import dev.glasstv.launcher.ui.components.GlassButton
import dev.glasstv.launcher.ui.components.GlassPanel
import dev.glasstv.launcher.ui.components.GlassText
import dev.glasstv.launcher.ui.components.GlassTextField
import dev.glasstv.launcher.ui.components.PillButton
import dev.glasstv.launcher.ui.components.glassPanel
import dev.glasstv.launcher.ui.theme.GlassType
import dev.glasstv.launcher.ui.theme.LocalGlass

/** Chips: a prompt sent as-is, or a starter that waits for a title. */
private data class Chip(val label: String, val prompt: String, val needsTitle: Boolean)

private val chips = listOf(
    Chip("What should we watch tonight?", "What should we watch tonight? Suggest 4 options across moods.", false),
    Chip("Recap a show", "Recap the latest season of ", true),
    Chip("Who's in this?", "Who's in the cast of ", true),
    Chip("Something funny", "Recommend 4 feel-good comedies, a mix of films and series.", false),
    Chip("Open YouTube", "open YouTube", false),
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AiPanel(app: GlassApp, onClose: () -> Unit) {
    val c = LocalGlass.current
    val state by app.assistant.state.collectAsState()
    val draft by app.assistant.draft.collectAsState()
    val list = rememberLazyListState()
    val firstChip = remember { FocusRequester() }

    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { runCatching { firstChip.requestFocus() } }
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content?.length) {
        if (state.messages.isNotEmpty()) list.scrollToItem(state.messages.lastIndex)
    }

    Column(
        Modifier
            .fillMaxHeight()
            .width(560.dp)
            .padding(top = 24.dp, bottom = 24.dp, end = 24.dp)
            .glassPanel(RoundedCornerShape(36.dp), 28.dp)
            .background(c.background.copy(alpha = 0.72f)) // frosted, near-opaque: text must read cleanly over the grid
            .padding(28.dp)
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(Brush.radialGradient(listOf(Color.White, c.pink, c.rose, c.cocoa)), CircleShape))
            Spacer(Modifier.width(14.dp))
            GlassText("Assistant", GlassType.greeting, Modifier.weight(1f))
            if (state.messages.isNotEmpty()) PillButton("Clear", { app.assistant.clear() })
        }
        Spacer(Modifier.height(16.dp))

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (state.messages.isEmpty() && state.error == null) {
                item {
                    GlassText(
                        "Ask for something to watch, a recap, or say \"open Netflix\". Type from your paired phone or with the TV keyboard.",
                        GlassType.body, color = c.textSoft,
                    )
                }
            }
            itemsIndexed(state.messages) { i, m ->
                val mine = m.role == "user"
                val text = m.content.ifEmpty { if (state.streaming && i == state.messages.lastIndex) "…" else "" }
                Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                    Box(
                        Modifier
                            .then(if (mine) Modifier.padding(start = 60.dp) else Modifier.padding(end = 30.dp))
                            .background(if (mine) c.pink.copy(alpha = 0.8f) else c.glassFillFocused, RoundedCornerShape(22.dp))
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                    ) { GlassText(text, GlassType.body) }
                }
            }
            state.error?.let { err ->
                item {
                    GlassPanel(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), PaddingValues(20.dp)) {
                        Column {
                            GlassText(err.title, GlassType.title)
                            Spacer(Modifier.height(6.dp))
                            GlassText(err.fix, GlassType.body, color = c.textSoft)
                            Spacer(Modifier.height(4.dp))
                            GlassText("Everything else on the TV still works.", GlassType.small, color = c.textSoft)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp)) {
            items(chips) { chip ->
                PillButton(
                    chip.label,
                    onClick = {
                        if (chip.needsTitle) app.assistant.setDraft(chip.prompt) else app.assistant.send(chip.prompt)
                    },
                    modifier = if (chip == chips.first()) Modifier.focusRequester(firstChip) else Modifier,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassTextField(
                value = draft,
                onCommit = { app.assistant.setDraft(it) },
                onChange = { app.assistant.setDraft(it) },
                placeholder = "Type a question…",
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            GlassButton(
                onClick = { val d = draft; app.assistant.setDraft(""); app.assistant.send(d) },
                shape = CircleShape,
                modifier = Modifier.size(56.dp),
            ) { GlassText("➤", GlassType.title) }
        }
    }
}
