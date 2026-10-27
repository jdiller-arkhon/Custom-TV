package dev.glasstv.launcher.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import dev.glasstv.launcher.GlassApp
import dev.glasstv.launcher.data.CalEvent
import dev.glasstv.launcher.ui.components.CuteOrb
import dev.glasstv.launcher.ui.theme.GlassType
import dev.glasstv.launcher.ui.theme.LocalGlass
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Month view + agenda. Pick a day to see its events; OK on an event removes it (confirm by pressing twice). */
@Composable
fun CalendarSheet(app: GlassApp, onAskGrace: () -> Unit, onClose: () -> Unit) {
    val c = LocalGlass.current
    val events by app.calendar.events.collectAsState()
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }
    var armedDelete by remember { mutableStateOf<String?>(null) }
    val todayFocus = remember { FocusRequester() }
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { runCatching { todayFocus.requestFocus() } }
    val byDay = remember(events) { events.groupBy { it.start.toLocalDate() } }

    Box(Modifier.fillMaxSize().background(c.background.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
        Row(
            Modifier.padding(36.dp).fillMaxSize()
                .shadow(24.dp, RoundedCornerShape(30.dp), ambientColor = c.shadow, spotColor = c.shadow)
                .background(c.glassFill, RoundedCornerShape(30.dp))
                .border(1.dp, c.highlightTop, RoundedCornerShape(30.dp))
                .padding(32.dp)
                .focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(36.dp),
        ) {
            // Month grid
            Column(Modifier.weight(1.15f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = GlassType.hero.copy(fontSize = GlassType.hero.fontSize * 0.62f, color = c.text))
                    Spacer(Modifier.weight(1f))
                    SmallButton("‹") { month = month.minusMonths(1) }
                    Spacer(Modifier.width(8.dp))
                    SmallButton("Today") { month = YearMonth.from(today); selected = today }
                    Spacer(Modifier.width(8.dp))
                    SmallButton("›") { month = month.plusMonths(1) }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth()) {
                    listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                        BasicText(it, Modifier.weight(1f), GlassType.small.copy(color = c.textSoft, textAlign = TextAlign.Center))
                    }
                }
                Spacer(Modifier.height(8.dp))
                val first = month.atDay(1)
                val lead = first.dayOfWeek.value % 7
                val cells = (0 until 42).map { i -> first.plusDays((i - lead).toLong()) }
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        week.forEach { d ->
                            DayCell(
                                d, inMonth = d.month == month.month, isToday = d == today, isSelected = d == selected,
                                dots = byDay[d].orEmpty().size,
                                modifier = Modifier.weight(1f).then(if (d == today) Modifier.focusRequester(todayFocus) else Modifier),
                                onFocus = { selected = d }, onClick = { selected = d },
                            )
                        }
                    }
                }
            }
            // Agenda
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CuteOrb(40.dp)
                    Spacer(Modifier.width(10.dp))
                    BasicText(
                        if (selected == today) "Today" else selected.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                        style = GlassType.title.copy(color = c.text),
                    )
                }
                Spacer(Modifier.height(14.dp))
                val dayEvents = byDay[selected].orEmpty()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (dayEvents.isEmpty()) {
                        BasicText("Nothing planned ♡", style = GlassType.body.copy(color = c.textSoft))
                    }
                    dayEvents.forEach { e ->
                        EventRow(e, armed = armedDelete == e.id) {
                            if (armedDelete == e.id) { app.calendar.remove(e.id); armedDelete = null } else armedDelete = e.id
                        }
                    }
                    val later = app.calendar.upcoming(LocalDateTime.now()).filter { it.start.toLocalDate().isAfter(selected) }.take(5)
                    if (later.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        BasicText("COMING UP", style = GlassType.kicker.copy(color = c.textSoft))
                        later.forEach { e -> BasicText("${e.emoji}  ${e.title}  ·  ${e.whenText()}", style = GlassType.small.copy(color = c.text), maxLines = 1) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                BasicText("Tip: tell Grace “add dinner Friday at 7pm”", style = GlassType.small.copy(color = c.textSoft))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallButton("✨  Ask Grace to add an event", primary = true, onClick = onAskGrace)
                    SmallButton("Done", onClick = onClose)
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    d: LocalDate, inMonth: Boolean, isToday: Boolean, isSelected: Boolean, dots: Int,
    modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit,
) {
    val c = LocalGlass.current
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp).onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isToday) c.pink else Color.Transparent,
            focusedContainerColor = c.rose, pressedContainerColor = c.rose,
            contentColor = if (inMonth) c.text else c.textSoft.copy(alpha = 0.5f), focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(if (isSelected) 2.dp else 0.dp, if (isSelected) c.rose else Color.Transparent), shape = RoundedCornerShape(14.dp)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        glow = ClickableSurfaceDefaults.glow(Glow.None, Glow(c.shadow, 10.dp)),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            androidx.tv.material3.Text(d.dayOfMonth.toString(), style = GlassType.label)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(dots.coerceAtMost(3)) { Box(Modifier.size(5.dp).background(c.rose, CircleShape)) }
            }
        }
    }
}

@Composable
private fun EventRow(e: CalEvent, armed: Boolean, onClick: () -> Unit) {
    val c = LocalGlass.current
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = c.background, focusedContainerColor = if (armed) c.rose else c.pink, pressedContainerColor = c.pink,
            contentColor = c.text, focusedContentColor = if (armed) Color.White else c.cocoa,
        ),
        border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(1.dp, c.highlightTop), shape = RoundedCornerShape(16.dp))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        glow = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.tv.material3.Text(e.emoji, style = GlassType.title)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                androidx.tv.material3.Text(e.title, style = GlassType.label, maxLines = 1)
                androidx.tv.material3.Text(if (armed) "Press OK again to delete" else if (e.allDay) "All day" else e.start.format(DateTimeFormatter.ofPattern("h:mm a")), style = GlassType.small)
            }
        }
    }
}

@Composable
private fun SmallButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val c = LocalGlass.current
    Surface(
        onClick = onClick,
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
    ) {
        androidx.tv.material3.Text(text, style = GlassType.label, modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp))
    }
}
