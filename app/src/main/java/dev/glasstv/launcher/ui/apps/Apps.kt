package dev.glasstv.launcher.ui.apps

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.glasstv.launcher.BuiltInApp
import dev.glasstv.launcher.GlassApp
import dev.glasstv.launcher.data.WatchItem
import dev.glasstv.launcher.ui.components.GlassTextField
import dev.glasstv.launcher.ui.components.QrCode
import dev.glasstv.launcher.ui.theme.Fraunces
import dev.glasstv.launcher.ui.theme.GlassType
import dev.glasstv.launcher.ui.theme.LocalGlass
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------- Apps hub

@Composable
fun AppsHub(onOpen: (BuiltInApp) -> Unit, onClose: () -> Unit) {
    val c = LocalGlass.current
    val first = rememberInitialFocus()
    AppSheet("✿", "Apps", "Everything Grace can help with", onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BuiltInApp.entries.chunked(5).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEachIndexed { i, a ->
                        Surface(
                            onClick = { if (a.ready) onOpen(a) },
                            modifier = Modifier.weight(1f).height(124.dp).initialFocus(if (r == 0 && i == 0) first else null),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(22.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = c.background, focusedContainerColor = c.pink, pressedContainerColor = c.pink,
                                contentColor = c.text, focusedContentColor = c.cocoa,
                            ),
                            border = ClickableSurfaceDefaults.border(
                                border = Border(BorderStroke(1.dp, c.highlightTop), shape = RoundedCornerShape(22.dp)),
                                focusedBorder = Border(BorderStroke(3.dp, c.rose), shape = RoundedCornerShape(22.dp)),
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                            glow = ClickableSurfaceDefaults.glow(Glow.None, Glow(c.shadow, 16.dp)),
                        ) {
                            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(a.emoji, style = GlassType.title)
                                    Spacer(Modifier.weight(1f))
                                    if (!a.ready) Box(Modifier.background(c.pink, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                        Text("Soon", style = GlassType.small, color = c.cocoa)
                                    }
                                }
                                Column {
                                    Text(a.title, style = GlassType.label)
                                    Text(a.blurb, style = GlassType.small, color = c.textSoft, maxLines = 1)
                                }
                            }
                        }
                    }
                    repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Lists

@Composable
fun ListsApp(app: GlassApp, onClose: () -> Unit) {
    val c = LocalGlass.current
    val state by app.home.state.collectAsState()
    var list by remember { mutableStateOf("Groceries") }
    val first = rememberInitialFocus()
    AppSheet("🛒", "Lists", "Tip: “Grace, add milk and eggs to the grocery list”", onClose) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Groceries", "To-do", "Chores").forEachIndexed { i, l ->
                Btn(if (l == list) "● $l" else l, Modifier.initialFocus(if (i == 0) first else null), primary = l == list) { list = l }
            }
            Spacer(Modifier.weight(1f))
            Btn("Clear done") { app.home.clearDone(list) }
        }
        Spacer(Modifier.height(14.dp))
        GlassTextField("", onCommit = { if (it.isNotBlank()) app.home.addItem(list, it.trim()) }, placeholder = "＋ Add to $list…", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val items = state.items.filter { it.list == list }.sortedBy { it.done }
            if (items.isEmpty()) Hint("Nothing here yet ♡")
            items.forEach { item ->
                RowCard(onClick = { app.home.toggleItem(item.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (item.done) "✅" else "⬜", style = GlassType.title)
                        Spacer(Modifier.width(12.dp))
                        Text(item.text, style = GlassType.body, color = if (item.done) c.textSoft else Color.Unspecified)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Timers

@Composable
fun TimersApp(app: GlassApp, onClose: () -> Unit) {
    val timers by app.services.timers.collectAsState()
    var tick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { tick = SystemClock.elapsedRealtime(); delay(500) } }
    val first = rememberInitialFocus()
    AppSheet("⏰", "Timers", "Tip: “Grace, pasta timer 10 minutes”", onClose) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(1, 5, 10, 15, 30, 60).forEachIndexed { i, m ->
                Btn("$m min", Modifier.initialFocus(if (i == 0) first else null)) { app.services.startTimer("$m-minute", Duration.ofMinutes(m.toLong())) }
            }
        }
        Spacer(Modifier.height(18.dp))
        if (timers.isEmpty()) Hint("No timers running.")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            timers.forEach { t ->
                val left = ((t.endsAt - tick) / 1000).coerceAtLeast(0)
                RowCard(onClick = { app.services.cancelTimer(t.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText("%d:%02d".format(left / 60, left % 60), style = GlassType.hero.copy(fontSize = GlassType.hero.fontSize * 0.8f, color = LocalGlass.current.text))
                        Spacer(Modifier.width(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.label, style = GlassType.title)
                            Text("Press OK to cancel", style = GlassType.small)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Countdowns

@Composable
fun CountdownsApp(app: GlassApp, onClose: () -> Unit) {
    val c = LocalGlass.current
    val state by app.home.state.collectAsState()
    var armed by remember { mutableStateOf<String?>(null) }
    val first = rememberInitialFocus()
    AppSheet("✨", "Countdowns", "Tip: “Grace, countdown to Paris on Dec 12”", onClose) {
        GlassTextField("", onCommit = { s -> app.services.handle("countdown to $s") }, placeholder = "＋ e.g. “Our anniversary on March 3”", modifier = Modifier.fillMaxWidth().initialFocus(first))
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val list = state.countdowns.filter { !it.date.isBefore(LocalDate.now()) }
            if (list.isEmpty()) Hint("No countdowns yet — something to look forward to? ♡")
            list.take(5).forEach { cd ->
                val days = ChronoUnit.DAYS.between(LocalDate.now(), cd.date)
                Surface(
                    onClick = { if (armed == cd.id) { app.home.removeCountdown(cd.id); armed = null } else armed = cd.id },
                    modifier = Modifier.width(220.dp).height(220.dp),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(26.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = c.pink, focusedContainerColor = if (armed == cd.id) c.rose else c.pink, contentColor = c.cocoa, focusedContentColor = if (armed == cd.id) Color.White else c.cocoa),
                    border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, c.rose), shape = RoundedCornerShape(26.dp))),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    glow = ClickableSurfaceDefaults.glow(Glow.None, Glow(c.shadow, 16.dp)),
                ) {
                    Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(cd.emoji, style = GlassType.title)
                        Text(if (days == 0L) "Today!" else "$days", style = GlassType.hero.copy(fontSize = GlassType.hero.fontSize * 1.2f))
                        Text(if (days == 1L) "day until" else if (days == 0L) "" else "days until", style = GlassType.small)
                        Text(cd.title, style = GlassType.label, maxLines = 1)
                        Text(if (armed == cd.id) "OK again to remove" else cd.date.format(DateTimeFormatter.ofPattern("MMM d, yyyy")), style = GlassType.small)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Watchlist

@Composable
fun WatchlistApp(app: GlassApp, onClose: () -> Unit) {
    val c = LocalGlass.current
    val state by app.home.state.collectAsState()
    val first = rememberInitialFocus()
    fun hearts(n: Int) = if (n == 0) "–" else "♥".repeat(n)
    AppSheet("🍿", "Watchlist", "Save from Date Night, or “Grace, add The Lost City to our watchlist”", onClose) {
        GlassTextField("", onCommit = { if (it.isNotBlank()) app.home.addWatch(WatchItem(title = it.trim())) }, placeholder = "＋ Add a movie or show…", modifier = Modifier.fillMaxWidth().initialFocus(first))
        Spacer(Modifier.height(12.dp))
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.watchlist.isEmpty()) Hint("Your list is empty — spin Date Night and save a pick 💕")
            state.watchlist.sortedBy { it.watched }.forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        BasicText((if (w.watched) "✅ " else "🎬 ") + w.title + (if (w.year > 0) " (${w.year})" else ""), style = GlassType.body.copy(color = c.text))
                        BasicText("Justin ${hearts(w.his)}   ·   Anna ${hearts(w.hers)}", style = GlassType.small.copy(color = c.rose))
                    }
                    Btn(if (w.watched) "Unwatch" else "Watched ✓") { app.home.updateWatch(w.id) { it.copy(watched = !it.watched) } }
                    Btn("Justin ♥") { app.home.updateWatch(w.id) { it.copy(his = (it.his + 1) % 6) } }
                    Btn("Anna ♥") { app.home.updateWatch(w.id) { it.copy(hers = (it.hers + 1) % 6) } }
                    Btn("✕") { app.home.removeWatch(w.id) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Love notes

@Composable
fun LoveNotesApp(app: GlassApp, onClose: () -> Unit) {
    val c = LocalGlass.current
    val state by app.home.state.collectAsState()
    val first = rememberInitialFocus()
    AppSheet("💌", "Love Notes", "Send one from the phone remote → Type → 💌 Love note", onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.notes.isEmpty()) Hint("No notes yet. Justin, the phone remote has a 💌 button… ♡")
            state.notes.forEachIndexed { i, n ->
                RowCard(Modifier.initialFocus(if (i == 0) first else null), onClick = {}) {
                    Column {
                        BasicText("“${n.text}”", style = GlassType.heroSub.copy(fontFamily = Fraunces, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = c.text))
                        Spacer(Modifier.height(4.dp))
                        BasicText("— ${n.from} · ${java.time.Instant.ofEpochMilli(n.at).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))}", style = GlassType.small.copy(color = c.rose))
                    }
                }
            }
            if (state.notes.isEmpty()) Btn("Close", Modifier.initialFocus(first), onClick = onClose)
        }
    }
}

// ---------------------------------------------------------------- Grace remembers

@Composable
fun MemoryApp(app: GlassApp, onClose: () -> Unit) {
    val state by app.home.state.collectAsState()
    var armed by remember { mutableStateOf<String?>(null) }
    val first = rememberInitialFocus()
    AppSheet("🧠", "Grace Remembers", "Tip: “Grace, remember that Anna's favorite flowers are peonies”", onClose) {
        GlassTextField("", onCommit = { if (it.isNotBlank()) app.home.remember(it.trim()) }, placeholder = "＋ Something Grace should remember…", modifier = Modifier.fillMaxWidth().initialFocus(first))
        Spacer(Modifier.height(12.dp))
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.memories.isEmpty()) Hint("Nothing saved yet.")
            state.memories.reversed().forEach { m ->
                RowCard(armed = armed == m.id, onClick = { if (armed == m.id) { app.home.forget(m.id); armed = null } else armed = m.id }) {
                    Text(if (armed == m.id) "Press OK again to forget" else "♡  ${m.fact}", style = GlassType.body)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Sleep timer

@Composable
fun SleepApp(app: GlassApp, onClose: () -> Unit) {
    val sleepAt by app.services.sleepAt.collectAsState()
    var tick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { tick = SystemClock.elapsedRealtime(); delay(1000) } }
    val first = rememberInitialFocus()
    AppSheet("🌙", "Sleep Timer", "Pauses what's playing and dims to the cozy clock", onClose) {
        val left = sleepAt?.let { ((it - tick) / 60_000) + 1 }
        BasicText(if (left == null) "Off" else "$left min left", style = GlassType.hero.copy(color = LocalGlass.current.text))
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(15, 30, 45, 60, 90).forEachIndexed { i, m -> Btn("$m min", Modifier.initialFocus(if (i == 1) first else null), primary = false) { app.services.sleepTimer(m) } }
            Btn("Turn off") { app.services.sleepTimer(0) }
        }
    }
}

// ---------------------------------------------------------------- Guest Wi-Fi

@Composable
fun GuestWifiApp(app: GlassApp, onClose: () -> Unit) {
    val state by app.home.state.collectAsState()
    var ssid by remember(state.wifiSsid) { mutableStateOf(state.wifiSsid) }
    var pass by remember(state.wifiPassword) { mutableStateOf(state.wifiPassword) }
    val first = rememberInitialFocus()
    fun esc(s: String) = s.replace(Regex("""([\;,:"])"""), "\\\\$1")
    AppSheet("📶", "Guest Wi-Fi", "Guests scan with their phone camera to join", onClose) {
        Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Hint("Network name")
                GlassTextField(ssid, onCommit = { ssid = it; app.home.setWifi(it, pass) }, placeholder = "e.g. Anna & Justin", modifier = Modifier.fillMaxWidth().initialFocus(first))
                Hint("Password (stored only on this TV)")
                GlassTextField(pass, onCommit = { pass = it; app.home.setWifi(ssid, it) }, placeholder = "Wi-Fi password", modifier = Modifier.fillMaxWidth())
            }
            if (ssid.isNotBlank()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    QrCode("WIFI:T:${if (pass.isEmpty()) "nopass" else "WPA"};S:${esc(ssid)};P:${esc(pass)};;", 230.dp)
                    Spacer(Modifier.height(10.dp))
                    BasicText(ssid, style = GlassType.title.copy(color = LocalGlass.current.text))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Fireplace

@Composable
fun FireplaceApp(reduceMotion: Boolean, onClose: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    val first = rememberInitialFocus()
    val t = if (reduceMotion) 0.3f else rememberInfiniteTransition(label = "fire").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "f",
    ).value
    val sparks = remember { List(40) { Triple(Random.nextFloat(), Random.nextFloat(), 0.5f + Random.nextFloat()) } }
    Box(Modifier.fillMaxSize().background(Color(0xFF120A0C))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            // warm room glow
            drawRect(Brush.radialGradient(listOf(Color(0xFF6B2A1A), Color(0xFF2A1214), Color(0xFF120A0C)), center = Offset(w / 2, h * 0.85f), radius = w * 0.7f))
            // logs
            drawRoundRect(Color(0xFF3B2418), Offset(w * 0.3f, h * 0.84f), androidx.compose.ui.geometry.Size(w * 0.4f, h * 0.05f), androidx.compose.ui.geometry.CornerRadius(30f))
            // flames: layered soft tongues that flicker
            val layers = listOf(Color(0xFFE2412B) to 1f, Color(0xFFF7862F) to 0.8f, Color(0xFFFFC94D) to 0.55f, Color(0xFFFFF1B8) to 0.3f)
            layers.forEachIndexed { li, (col, scale) ->
                for (k in 0 until 7) {
                    val phase = t * 6.28f * (1 + k * 0.17f) + k * 1.3f + li
                    val cx = w * (0.36f + k * 0.047f) + sin(phase) * w * 0.008f
                    val ht = h * 0.42f * scale * (0.7f + 0.3f * sin(phase * 1.7f + k).let { (it + 1) / 2 })
                    val bw = w * 0.07f * scale
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx - bw, h * 0.85f)
                        quadraticTo(cx - bw * 0.9f, h * 0.85f - ht * 0.55f, cx + sin(phase) * bw * 0.4f, h * 0.85f - ht)
                        quadraticTo(cx + bw * 0.9f, h * 0.85f - ht * 0.55f, cx + bw, h * 0.85f)
                        close()
                    }
                    drawPath(path, col.copy(alpha = 0.55f))
                }
            }
            // ---- Christmas tree, to the right of the fire ----
            val tx = w * 0.84f; val base = h * 0.86f; val th = h * 0.62f
            drawRect(Color(0xFF4A2C1E), Offset(tx - w * 0.012f, base - h * 0.06f), androidx.compose.ui.geometry.Size(w * 0.024f, h * 0.06f))
            val greens = listOf(Color(0xFF1F5A3A), Color(0xFF26704A), Color(0xFF2E8556))
            for (tier in 0 until 3) {
                val top = base - h * 0.06f - th * (0.3f + tier * 0.3f)
                val bottom = base - h * 0.06f - th * (tier * 0.27f)
                val half = w * (0.11f - tier * 0.028f)
                drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(tx, top); lineTo(tx + half, bottom); lineTo(tx - half, bottom); close()
                }, greens[tier])
            }
            // twinkling lights draped along the tiers
            val bulbs = listOf(Color(0xFFFF6B8B), Color(0xFFFFD166), Color(0xFF7FD3FF), Color(0xFFFFFFFF), Color(0xFFB98CFF))
            var n = 0
            for (tier in 0 until 3) {
                val y = base - h * 0.06f - th * (tier * 0.27f) - th * 0.05f
                val half = w * (0.10f - tier * 0.028f)
                for (k in 0..6) {
                    val f = k / 6f
                    val bx = tx - half + f * half * 2
                    val by = y - kotlin.math.sin(f * 3.14f) * th * 0.06f
                    val tw = 0.55f + 0.45f * sin(t * 6.28f * 2 + n * 1.7f)
                    val col = bulbs[n % bulbs.size]
                    drawCircle(col.copy(alpha = 0.25f * tw), 16f, Offset(bx, by))
                    drawCircle(col.copy(alpha = 0.6f + 0.4f * tw), 6f, Offset(bx, by))
                    n++
                }
            }
            // gold star with a soft glow
            val sy = base - h * 0.06f - th * 0.9f - 8f
            drawCircle(Color(0xFFFFD166).copy(alpha = 0.25f + 0.15f * sin(t * 6.28f)), 46f, Offset(tx, sy))
            drawPath(androidx.compose.ui.graphics.Path().apply {
                for (k in 0 until 10) {
                    val r = if (k % 2 == 0) 30f else 13f
                    val ang = -Math.PI / 2 + k * Math.PI / 5
                    val px = tx + (r * kotlin.math.cos(ang)).toFloat(); val py = sy + (r * kotlin.math.sin(ang)).toFloat()
                    if (k == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }, Color(0xFFFFD166))
            // presents
            listOf(Triple(-0.07f, Color(0xFFE85A7A), 0.07f), Triple(0.035f, Color(0xFFF4B6C8), 0.055f), Triple(-0.005f, Color(0xFFC9A36A), 0.045f)).forEach { (dx, col, sz) ->
                val px = tx + w * dx; val ps = w * sz
                drawRect(col, Offset(px - ps / 2, base - ps * 0.8f), androidx.compose.ui.geometry.Size(ps, ps * 0.8f))
                drawRect(Color.White.copy(alpha = 0.85f), Offset(px - ps * 0.07f, base - ps * 0.8f), androidx.compose.ui.geometry.Size(ps * 0.14f, ps * 0.8f))
                drawRect(Color.White.copy(alpha = 0.85f), Offset(px - ps / 2, base - ps * 0.47f), androidx.compose.ui.geometry.Size(ps, ps * 0.12f))
            }
            // rising sparks
            sparks.forEach { (x, y0, sp) ->
                val y = ((y0 + t * sp) % 1f)
                drawCircle(Color(0xFFFFC94D).copy(alpha = (1 - y) * 0.8f), 2.5f + sp, Offset(w * (0.35f + x * 0.3f), h * (0.82f - y * 0.6f)))
            }
        }
        Column(Modifier.align(Alignment.TopStart).padding(40.dp)) {
            BasicText("🔥🎄 Fireplace", style = GlassType.title.copy(color = Color(0xFFFFE7D6)))
            BasicText("Press Back to return ♡", style = GlassType.small.copy(color = Color(0xFFFFE7D6).copy(alpha = 0.6f)))
        }
        Box(Modifier.align(Alignment.TopEnd).padding(36.dp)) { Btn("Done", Modifier.initialFocus(first), onClick = onClose) }
    }
}

// ---------------------------------------------------------------- Would You Rather

private val deck = listOf(
    "Would you rather have a cozy movie night in, or a fancy dinner out?" to ("🛋️" to "🍷"),
    "Would you rather travel to Paris or to Tokyo together?" to ("🗼" to "🗾"),
    "Would you rather only watch rom-coms or only watch action movies for a year?" to ("💕" to "💥"),
    "Would you rather have breakfast in bed or a surprise picnic?" to ("🥞" to "🧺"),
    "Would you rather relive our first date or skip ahead to our next big trip?" to ("⏪" to "✈️"),
    "Would you rather have a puppy or a kitten?" to ("🐶" to "🐱"),
    "Would you rather dance in the kitchen or sing in the car?" to ("💃" to "🎤"),
    "Would you rather have a beach house or a cabin in the woods?" to ("🏖️" to "🌲"),
    "Would you rather give up dessert or give up coffee?" to ("🍰" to "☕"),
    "Would you rather binge a whole series in a weekend or watch one episode a week?" to ("📺" to "📅"),
    "Would you rather plan every detail of a trip or wing it?" to ("🗒️" to "🎲"),
    "Would you rather get a handwritten letter or a surprise song?" to ("💌" to "🎶"),
    "Would you rather cook dinner together or order our favorite takeout?" to ("🍝" to "🥡"),
    "Would you rather watch the sunrise or the sunset together?" to ("🌅" to "🌇"),
    "Would you rather have a movie made about our love story or a song written about it?" to ("🎬" to "🎵"),
    "Would you rather spend a rainy day reading or gaming?" to ("📚" to "🎮"),
    "Would you rather live in a big city or a small cozy town?" to ("🏙️" to "🏡"),
    "Would you rather have matching pajamas or matching tattoos?" to ("👕" to "🖋️"),
    "Would you rather go stargazing or go to a concert?" to ("✨" to "🎸"),
    "Would you rather redo our wedding day / first kiss, or design our dream home?" to ("💍" to "🏠"),
)

@Composable
fun WouldYouRatherApp(onClose: () -> Unit) {
    val c = LocalGlass.current
    var i by remember { mutableIntStateOf(Random.nextInt(deck.size)) }
    var picked by remember { mutableStateOf<Int?>(null) }
    val first = rememberInitialFocus()
    val (q, emo) = deck[i]
    AppSheet("🎲", "Would You Rather", "Take turns — no wrong answers ♡", onClose) {
        Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            BasicText(q, style = GlassType.hero.copy(fontSize = GlassType.hero.fontSize * 0.78f, color = c.text, textAlign = TextAlign.Center))
            Spacer(Modifier.height(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                listOf(emo.first, emo.second).forEachIndexed { k, e ->
                    Surface(
                        onClick = { picked = k },
                        modifier = Modifier.size(150.dp).initialFocus(if (k == 0) first else null),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(36.dp)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = if (picked == k) c.rose else c.pink, focusedContainerColor = c.rose),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                        glow = ClickableSurfaceDefaults.glow(Glow.None, Glow(c.shadow, 20.dp)),
                    ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(e, style = GlassType.hero) } }
                }
            }
            Spacer(Modifier.height(26.dp))
            Btn("Next question  →", primary = true) { i = (i + 1 + Random.nextInt(deck.size - 1)) % deck.size; picked = null }
        }
    }
}
