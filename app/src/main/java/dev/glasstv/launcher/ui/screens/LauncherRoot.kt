package dev.glasstv.launcher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.key
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glasstv.launcher.GlassApp
import dev.glasstv.launcher.Idle
import dev.glasstv.launcher.LauncherUi
import dev.glasstv.launcher.ai.LocalCommand
import dev.glasstv.launcher.data.AppArt
import dev.glasstv.launcher.data.Settings
import dev.glasstv.launcher.data.ThemeMode
import dev.glasstv.launcher.data.TvApp
import dev.glasstv.launcher.data.Weather
import dev.glasstv.launcher.data.WeatherClient
import dev.glasstv.launcher.remote.NetworkInfo
import dev.glasstv.launcher.remote.RemoteBus
import dev.glasstv.launcher.ui.components.GlassButton
import dev.glasstv.launcher.ui.components.GlassPanel
import dev.glasstv.launcher.ui.components.GlassText
import dev.glasstv.launcher.ui.components.OrbBackground
import dev.glasstv.launcher.ui.components.PillButton
import dev.glasstv.launcher.ui.components.QrCode
import dev.glasstv.launcher.ui.components.glassPanel
import dev.glasstv.launcher.ui.components.rememberMinuteClock
import dev.glasstv.launcher.ui.theme.GlassTheme
import dev.glasstv.launcher.ui.theme.GlassType
import dev.glasstv.launcher.ui.theme.LocalGlass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val AMBIENT_AFTER_MS = 3 * 60_000L

@Composable
fun LauncherRoot(app: GlassApp, ui: LauncherUi) {
    val settings = app.settings.collectAsStateWithLifecycle().value
    val now by rememberMinuteClock()
    if (settings == null) {
        // DataStore loads in a few ms; show the plain blush window background meanwhile.
        return
    }
    val dark = when (settings.theme) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AUTO -> now.hour >= 19 || now.hour < 7
    }

    // First launch: introduce the phone remote.
    LaunchedEffect(settings.onboarded) { if (!settings.onboarded) ui.qrOpen = true }

    // Commands the assistant resolved locally.
    LaunchedEffect(Unit) {
        app.uiCommands.collect {
            when (it) {
                LocalCommand.OpenSettings -> { ui.aiOpen = false; ui.settingsOpen = true }
                LocalCommand.Ambient -> { ui.aiOpen = false; ui.ambient = true }
                LocalCommand.Home -> ui.goHome()
                LocalCommand.OpenCalendar -> Unit // Grace answers in her panel; the Calendar button opens the full view
                is LocalCommand.EventAdded -> ui.toast = "${it.event.emoji} Added: ${it.event.title} · ${it.event.whenText()}"
                is LocalCommand.OpenApp -> Unit
            }
        }
    }

    // Ambient after 3 idle minutes on the launcher (activity pauses → timer irrelevant).
    LaunchedEffect(ui.ambient, ui.aiOpen) {
        if (ui.ambient) return@LaunchedEffect
        while (true) {
            delay(5_000)
            val streaming = app.assistant.state.value.streaming
            if (!streaming && android.os.SystemClock.uptimeMillis() - Idle.last > AMBIENT_AFTER_MS) ui.ambient = true
        }
    }

    var accent by remember { mutableStateOf<Color?>(null) }
    val scope = rememberCoroutineScope()
    GlassTheme(dark) {
        Box(Modifier.fillMaxSize()) {
            OrbBackground(settings.reduceMotion, settings.extraBlur, accent = accent)

            val anyOverlay = ui.aiOpen || ui.settingsOpen || ui.qrOpen || ui.dateNight || ui.calendarOpen || ui.appsOpen || ui.openApp != null
            HomeScreen(
                app, ui, settings, now, Modifier.fillMaxSize(), interactive = !anyOverlay && !ui.ambient,
                onAccent = { a -> scope.launch { accent = app.appRepo.art(a)?.tint?.takeIf { it != 0 }?.let { Color(it) } } },
            )

            // Scrim: soften the home screen behind the assistant so the sheet reads clearly.
            AnimatedVisibility(ui.aiOpen && !ui.ambient, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize().background(LocalGlass.current.background.copy(alpha = 0.55f)))
            }
            AnimatedVisibility(
                ui.aiOpen && !ui.ambient,
                enter = slideInHorizontally { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                AiPanel(app, onClose = { ui.aiOpen = false })
            }

            if (ui.settingsOpen) {
                SettingsScreen(app, settings, onClose = { ui.settingsOpen = false }, onShowQr = { ui.qrOpen = true })
            }

            if (ui.qrOpen) {
                RemoteQrSheet(onClose = {
                    ui.qrOpen = false
                    if (!settings.onboarded) app.scope.launch { app.settingsRepo.setOnboarded() }
                })
            }

            if (ui.dateNight && !ui.ambient) {
                DateNightSheet(
                    app, settings.reduceMotion,
                    onAskGrace = { prompt -> ui.dateNight = false; ui.aiOpen = true; app.assistant.send(prompt) },
                    onClose = { ui.dateNight = false },
                )
            }

            // Built-in apps
            if (ui.appsOpen && ui.openApp == null && !ui.ambient) {
                dev.glasstv.launcher.ui.apps.AppsHub(onOpen = { a ->
                    when (a) {
                        dev.glasstv.launcher.BuiltInApp.CALENDAR -> { ui.appsOpen = false; ui.calendarOpen = true }
                        dev.glasstv.launcher.BuiltInApp.DATE_NIGHT -> { ui.appsOpen = false; ui.dateNight = true }
                        else -> ui.openApp = a
                    }
                }, onClose = { ui.appsOpen = false })
            }
            ui.openApp?.takeIf { !ui.ambient }?.let { a ->
                val close = { ui.openApp = null }
                when (a) {
                    dev.glasstv.launcher.BuiltInApp.LISTS -> dev.glasstv.launcher.ui.apps.ListsApp(app, close)
                    dev.glasstv.launcher.BuiltInApp.TIMERS -> dev.glasstv.launcher.ui.apps.TimersApp(app, close)
                    dev.glasstv.launcher.BuiltInApp.COUNTDOWNS -> dev.glasstv.launcher.ui.apps.CountdownsApp(app, close)
                    dev.glasstv.launcher.BuiltInApp.WATCHLIST -> dev.glasstv.launcher.ui.apps.WatchlistApp(app, close)
                    dev.glasstv.launcher.BuiltInApp.LOVE_NOTES -> dev.glasstv.launcher.ui.apps.LoveNotesApp(app, close)
                    dev.glasstv.launcher.BuiltInApp.MEMORY -> dev.glasstv.launcher.ui.apps.MemoryApp(app, close)
                    dev.glasstv.launcher.BuiltInApp.SLEEP -> dev.glasstv.launcher.ui.apps.SleepApp(app, close)
                    dev.glasstv.launcher.BuiltInApp.GUEST_WIFI -> dev.glasstv.launcher.ui.apps.GuestWifiApp(app, close)
                    dev.glasstv.launcher.BuiltInApp.FIREPLACE -> dev.glasstv.launcher.ui.apps.FireplaceApp(settings.reduceMotion, close)
                    dev.glasstv.launcher.BuiltInApp.WOULD_YOU_RATHER -> dev.glasstv.launcher.ui.apps.WouldYouRatherApp(close)
                    else -> close()
                }
            }

            if (ui.calendarOpen && !ui.ambient) {
                CalendarSheet(app, onAskGrace = { ui.calendarOpen = false; ui.aiOpen = true; app.assistant.setDraft("Add ") }, onClose = { ui.calendarOpen = false })
            }

            // Pink toast (event added / reminders), auto-hides.
            val toast = ui.toast
            LaunchedEffect(toast) { if (toast != null) { delay(5000); ui.toast = null } }
            AnimatedVisibility(toast != null, Modifier.align(Alignment.TopCenter).padding(top = 28.dp), enter = fadeIn(), exit = fadeOut()) {
                val c = LocalGlass.current
                Row(
                    Modifier.shadow(16.dp, RoundedCornerShape(50), ambientColor = c.shadow, spotColor = c.shadow)
                        .background(c.glassFill, RoundedCornerShape(50)).border(1.dp, c.highlightTop, RoundedCornerShape(50))
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dev.glasstv.launcher.ui.components.CuteOrb(30.dp)
                    Spacer(Modifier.width(10.dp))
                    GlassText(toast.orEmpty(), GlassType.label, maxLines = 1)
                }
            }

            PairingCodeToast(Modifier.align(Alignment.TopCenter).padding(top = 32.dp))


            AnimatedVisibility(ui.ambient, enter = fadeIn(), exit = fadeOut()) {
                AmbientScreen(now, settings)
            }
        }
    }
}

/**
 * Cozy SaaS home (Apple × Vercel × Perplexity, in pink).
 *  Nav: "anna" wordmark with Grace · time/weather · quiet links.
 *  Hero: greeting + one big "Ask Grace" box + suggestion chips.
 *  Apps: one calm row of favorites (real icons, name below), then a smaller "More" row.
 *  Footer: Justin's note, small and sweet.
 */
@Composable
private fun HomeScreen(
    app: GlassApp,
    ui: LauncherUi,
    settings: Settings,
    now: LocalDateTime,
    modifier: Modifier,
    interactive: Boolean,
    onAccent: (TvApp) -> Unit,
) {
    val c = LocalGlass.current
    val apps by app.appRepo.apps.collectAsState()
    val pinnedPkgs = settings.pinned ?: emptyList()
    val byPkg = remember(apps) { apps.associateBy { it.packageName } }
    val pinned = remember(pinnedPkgs, byPkg) { pinnedPkgs.mapNotNull { byPkg[it] } }
    val others = remember(pinnedPkgs, apps) { apps.filter { it.packageName !in pinnedPkgs } }
    val favorites = (if (pinned.isNotEmpty()) pinned else others).take(8)
    val more = if (pinned.isNotEmpty()) others else others.drop(8)

    var query by remember { mutableStateOf("") }
    val results = remember(query, apps) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) emptyList() else apps.filter { it.label.lowercase().contains(q) }
            .sortedBy { if (it.label.lowercase().startsWith(q)) 0 else 1 }.take(8)
    }
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }

    // Gentle fade-up on each return home.
    val motion = !settings.reduceMotion
    val intro = remember { androidx.compose.animation.core.Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(ui.homeTick, motion) {
        if (!motion) { intro.snapTo(1f); return@LaunchedEffect }
        intro.snapTo(0f)
        intro.animateTo(1f, androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.LinearEasing))
    }
    val p = intro.value
    fun stage(start: Float, len: Float): Float { val x = ((p - start) / len).coerceIn(0f, 1f); return 1f - (1f - x) * (1f - x) * (1f - x) }
    fun Modifier.rise(start: Float) = graphicsLayer { val s = stage(start, 0.45f); alpha = s; translationY = (1 - s) * 24f }

    LaunchedEffect(apps.isNotEmpty(), interactive) {
        // Items may not be composed on the first frame; retry briefly.
        if (apps.isNotEmpty() && interactive) repeat(10) { delay(50); if (runCatching { firstFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect }
    }
    LaunchedEffect(ui.homeTick) { if (ui.homeTick > 0) { query = ""; runCatching { firstFocus.requestFocus() } } }
    fun togglePin(pkg: String) = scope.launch {
        app.settingsRepo.setPinned(if (pkg in pinnedPkgs) pinnedPkgs - pkg else pinnedPkgs + pkg)
    }
    fun ask(text: String) {
        val t = text.trim(); if (t.isEmpty()) return
        val hits = apps.filter { it.label.lowercase().contains(t.lowercase()) }
        if (hits.size == 1) { app.appRepo.launch(hits.first()); query = "" }
        else { ui.aiOpen = true; app.assistant.send(t); query = "" }
    }
    val (greeting, sub) = when (now.hour) {
        in 5..11 -> "Good morning" to "What should we put on with breakfast?"
        in 12..16 -> "Good afternoon" to "Time for a little treat of a show?"
        in 17..21 -> "Good evening" to "What are we watching tonight?"
        else -> "Hi, night owl" to "One more episode?"
    }

    Column(
        modifier
            .then(if (interactive) Modifier else Modifier.focusProperties { onEnter = { cancelFocusChange() } })
            .focusGroup()
            .padding(horizontal = 64.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val events by app.calendar.events.collectAsState()
        val next = remember(events, now) { app.calendar.upcoming(now).firstOrNull() }
        NavBar(settings, now, ui, Modifier.rise(0f))

        Spacer(Modifier.weight(0.55f))
        Text2("$greeting, ${settings.greetingName} ♡", GlassType.hero, c.text, Modifier.rise(0.08f))
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.rise(0.12f)) {
            Text2(sub, GlassType.heroSub, c.textSoft)
            // Up next: the soonest event, one quiet link (opens the calendar).
            if (next != null) {
                Text2("   ·  ", GlassType.heroSub, c.textSoft)
                NavLink("${next.emoji} ${next.title} · ${next.whenText(now)}", maxWidth = 460.dp) { ui.calendarOpen = true }
            }
        }
        Spacer(Modifier.height(24.dp))

        // The Perplexity-style ask box.
        Row(
            Modifier.width(700.dp).rise(0.18f)
                .shadow(18.dp, RoundedCornerShape(26.dp), ambientColor = c.shadow, spotColor = c.shadow)
                .background(c.glassFill, RoundedCornerShape(26.dp))
                .border(1.dp, c.highlightTop, RoundedCornerShape(26.dp))
                .padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dev.glasstv.launcher.ui.components.BlinkingGrace(38.dp, motion)
            Spacer(Modifier.width(12.dp))
            dev.glasstv.launcher.ui.components.GlassTextField(
                value = query, onCommit = ::ask, onChange = { query = it },
                placeholder = "Ask Grace, or search your apps…",
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.background(c.pink, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text2("OK ↵", GlassType.small, c.cocoa)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.rise(0.24f)) {
            Chip("💕  Date Night", highlight = true) { ui.dateNight = true }
            listOf(
                "🍿  Movie night" to "What should we watch tonight? Suggest 4 options across moods.",
                "☺  Something cozy" to "Recommend 4 cozy, feel-good shows or films.",
                "✨  Surprise me" to "Surprise me with one great pick and why I'd love it.",
            ).take(2).forEach { (label, prompt) -> Chip(label) { ui.aiOpen = true; app.assistant.send(prompt) } }
        }

        Spacer(Modifier.weight(0.45f))

        // Apps
        val row = if (query.isNotBlank()) results else favorites
        Row(Modifier.fillMaxWidth().rise(0.3f), verticalAlignment = Alignment.CenterVertically) {
            Text2(if (query.isNotBlank()) "Matching apps" else "Your apps", GlassType.nav, c.text)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f).height(1.dp).background(c.highlightTop))
        }
        Spacer(Modifier.height(6.dp))
        if (query.isNotBlank() && results.isEmpty()) {
            Text2("No apps called that — press OK and Grace will help ♡", GlassType.body, c.textSoft, Modifier.padding(vertical = 30.dp))
        } else Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            row.forEachIndexed { i, a ->
                key(a.packageName) {
                    AppCard(
                        app, a, 86.dp,
                        Modifier.rise(0.34f + i * 0.04f),
                        focus = if (i == 0) firstFocus else null,
                        onFocused = { onAccent(a) }, onLongClick = { togglePin(a.packageName) },
                    )
                }
            }
            // Everything else continues in the same calm row, after a hairline.
            if (query.isBlank() && more.isNotEmpty()) {
                Box(Modifier.padding(top = 18.dp).width(1.dp).height(52.dp).background(c.highlightTop))
                more.forEach { a -> key(a.packageName) { AppCard(app, a, 86.dp, Modifier.rise(0.5f), null, { onAccent(a) }, { togglePin(a.packageName) }) } }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.rise(0.6f)) {
            Text2("${dev.glasstv.launcher.Personal.LOVE_NOTE}  —  ${dev.glasstv.launcher.Personal.LOVE_FROM}", GlassType.small.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontFamily = dev.glasstv.launcher.ui.theme.Fraunces, fontSize = GlassType.small.fontSize * 1.15f), c.rose)
            Spacer(Modifier.width(6.dp))
            androidx.compose.foundation.Canvas(Modifier.size(12.dp)) { with(dev.glasstv.launcher.ui.components.HeartDraw) { heart(c.rose) } }
        }
    }
}

@Composable
private fun Text2(text: String, style: androidx.compose.ui.text.TextStyle, color: Color, modifier: Modifier = Modifier) =
    androidx.compose.foundation.text.BasicText(text, modifier, style.copy(color = color), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)

@Composable
private fun NavBar(settings: Settings, now: LocalDateTime, ui: LauncherUi, modifier: Modifier, next: dev.glasstv.launcher.data.CalEvent? = null) {
    val c = LocalGlass.current
    val weather = rememberWeather(settings)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        dev.glasstv.launcher.ui.components.CuteOrb(30.dp)
        Spacer(Modifier.width(10.dp))
        Text2(settings.greetingName.lowercase(), GlassType.wordmark, c.text)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.background(c.pink, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp)) {
            Text2("with ${dev.glasstv.launcher.Personal.ASSISTANT}", GlassType.small, c.cocoa)
        }
        Spacer(Modifier.weight(1f))
        // Up next: the soonest event, quietly in the bar (opens the calendar).
        Text2(
            listOfNotNull(now.format(DateTimeFormatter.ofPattern("h:mm")), weather?.let { "${it.glyph} ${it.temp}°" }).joinToString("   ·   "),
            GlassType.nav, c.textSoft,
        )
        Spacer(Modifier.width(22.dp))
        Box(Modifier.width(1.dp).height(22.dp).background(c.highlightTop))
        Spacer(Modifier.width(14.dp))
        NavLink("Apps") { ui.appsOpen = true }
        NavLink("Calendar") { ui.calendarOpen = true }
        NavLink("Ambient") { ui.ambient = true }
        NavLink("Phone remote") { ui.qrOpen = true }
        NavLink("Settings") { ui.settingsOpen = true }
    }
}

/** Quiet nav link; focus fills it with a soft pink pill. */
@Composable
private fun NavLink(text: String, maxWidth: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified, onClick: () -> Unit) {
    val c = LocalGlass.current
    var focused by remember { mutableStateOf(false) }
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { focused = it.isFocused },
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent, focusedContainerColor = c.pink, pressedContainerColor = c.pink,
        ),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        glow = androidx.tv.material3.ClickableSurfaceDefaults.glow(androidx.tv.material3.Glow.None, androidx.tv.material3.Glow.None),
    ) {
        Text2(text, GlassType.nav, if (focused) c.cocoa else c.textSoft, Modifier.widthIn(max = maxWidth).padding(horizontal = 14.dp, vertical = 7.dp))
    }
}

@Composable
private fun Chip(text: String, highlight: Boolean = false, onClick: () -> Unit) {
    val c = LocalGlass.current
    androidx.tv.material3.Surface(
        onClick = onClick,
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = if (highlight) c.rose else c.glassFill, focusedContainerColor = if (highlight) c.rose else c.pink, pressedContainerColor = c.pink,
            contentColor = if (highlight) Color.White else c.text, focusedContentColor = if (highlight) Color.White else c.cocoa,
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(1.dp, if (highlight) c.rose else c.highlightTop), shape = RoundedCornerShape(50)),
            focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(3.dp, if (highlight) c.cocoa.copy(alpha = 0.35f) else c.rose), shape = RoundedCornerShape(50)),
        ),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        glow = androidx.tv.material3.ClickableSurfaceDefaults.glow(androidx.tv.material3.Glow.None, androidx.tv.material3.Glow(c.shadow, 10.dp)),
    ) {
        androidx.tv.material3.Text(text, style = GlassType.label, modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp))
    }
}

/** Apple-style app card: white rounded tile, real icon, name underneath; focus = pink ring + soft lift. */
@Composable
private fun AppCard(
    app: GlassApp, tvApp: TvApp, size: androidx.compose.ui.unit.Dp, modifier: Modifier,
    focus: FocusRequester?, onFocused: () -> Unit, onLongClick: () -> Unit, showLabel: Boolean = true,
) {
    val c = LocalGlass.current
    var art by remember(tvApp.packageName) { mutableStateOf<AppArt?>(null) }
    LaunchedEffect(tvApp.packageName) { art = app.appRepo.art(tvApp) }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(size * 0.26f)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.tv.material3.Surface(
            onClick = { app.appRepo.launch(tvApp) },
            onLongClick = onLongClick,
            modifier = Modifier.size(size)
                .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() },
            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape),
            colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = c.glassFill, focusedContainerColor = c.glassFillFocused, pressedContainerColor = c.pink,
            ),
            border = androidx.tv.material3.ClickableSurfaceDefaults.border(
                border = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(1.dp, c.highlightTop), shape = shape),
                focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(3.dp, c.rose), inset = 3.dp, shape = shape),
            ),
            scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = dev.glasstv.launcher.ui.components.FOCUS_SCALE),
            glow = androidx.tv.material3.ClickableSurfaceDefaults.glow(androidx.tv.material3.Glow(c.shadow.copy(alpha = 0.10f), 6.dp), androidx.tv.material3.Glow(c.shadow, 20.dp)),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val a = art
                when {
                    a == null -> Text2(tvApp.label.take(1), GlassType.title, c.textSoft)
                    a.isBanner -> Image(a.image, tvApp.label, Modifier.fillMaxSize().clip(shape), contentScale = ContentScale.Crop)
                    else -> Image(a.image, tvApp.label, Modifier.fillMaxSize(0.6f).clip(RoundedCornerShape(size * 0.14f)))
                }
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.text.BasicText(tvApp.label, Modifier.width(size + 24.dp), GlassType.small.copy(color = if (focused) c.text else c.textSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

/** Frosted capsule search bar, tvOS style. Typing filters apps live; OK/Enter launches or asks the assistant. */
@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onSubmit: (String) -> Unit, modifier: Modifier) {
    val c = LocalGlass.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        dev.glasstv.launcher.ui.components.GlassTextField(
            value = query,
            onCommit = { onSubmit(it) },
            onChange = onQuery,
            placeholder = "⌕   Search apps, or ask anything",
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(50),
        )
    }
}

@Composable
private fun rememberWeather(settings: Settings): Weather? {
    val lat = settings.weatherLat
    val lon = settings.weatherLon
    var weather by remember(lat, lon, settings.fahrenheit) { mutableStateOf<Weather?>(null) }
    LaunchedEffect(lat, lon, settings.fahrenheit) {
        if (lat == null || lon == null) return@LaunchedEffect
        while (true) {
            WeatherClient.current(lat, lon, settings.fahrenheit)?.let { weather = it }
            delay(30 * 60_000L)
        }
    }
    return weather
}

/** tvOS app tile: rounded artwork, label only appears under the focused tile. */
@Composable
private fun AppTile(app: GlassApp, tvApp: TvApp, modifier: Modifier, onLongClick: () -> Unit, focus: FocusRequester? = null) {
    val c = LocalGlass.current
    var art by remember(tvApp.packageName) { mutableStateOf<AppArt?>(null) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(tvApp.packageName) { art = app.appRepo.art(tvApp) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        GlassButton(
            onClick = { app.appRepo.launch(tvApp) },
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f)
                .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
                .onFocusChanged { focused = it.isFocused },
            shape = RoundedCornerShape(32.dp),
            focusScale = 1.10f,
        ) {
            val a = art
            when {
                a != null && a.isBanner -> Image(
                    a.image, tvApp.label, Modifier.fillMaxSize().clip(RoundedCornerShape(32.dp)), contentScale = ContentScale.Crop,
                )
                else -> Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(c.glassFillFocused, c.pink.copy(alpha = 0.35f))), RoundedCornerShape(32.dp),
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (a != null) Image(a.image, null, Modifier.fillMaxSize(0.5f).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Fit)
                    else GlassText(tvApp.label.take(1), GlassType.greeting)
                }
            }
        }
        // Label: tvOS shows it only for the focused tile; the space is reserved so nothing jumps.
        val alpha by androidx.compose.animation.core.animateFloatAsState(if (focused) 1f else 0f, label = "label")
        GlassText(
            tvApp.label, GlassType.label,
            Modifier.padding(top = 14.dp).graphicsLayer { this.alpha = alpha }, maxLines = 1,
        )
    }
}

@Composable
private fun PairingCodeToast(modifier: Modifier) {
    val code by RemoteBus.pairingCode.collectAsState()
    val c = LocalGlass.current
    AnimatedVisibility(code != null, modifier, enter = fadeIn(), exit = fadeOut()) {
        GlassPanel(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 40.dp, vertical = 22.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GlassText("Enter this code on your phone", GlassType.label, color = c.textSoft)
                GlassText(code.orEmpty().toCharArray().joinToString("  "), GlassType.clock)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RemoteQrSheet(onClose: () -> Unit) {
    val context = LocalContext.current
    val c = LocalGlass.current
    val url = remember { NetworkInfo.remoteUrl(context) }
    val connected by RemoteBus.connectedPhones.collectAsState()
    val close = remember { FocusRequester() }
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { runCatching { close.requestFocus() } }
    Box(Modifier.fillMaxSize().background(c.background.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        GlassPanel(
            Modifier.focusProperties { onExit = { cancelFocusChange() } }.focusGroup(),
            padding = androidx.compose.foundation.layout.PaddingValues(40.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (url != null) QrCode(url, 220.dp) else Box(Modifier.size(248.dp))
                Spacer(Modifier.width(40.dp))
                Column(Modifier.width(420.dp)) {
                    GlassText("Use your phone as a remote", GlassType.greeting)
                    Spacer(Modifier.height(12.dp))
                    GlassText(
                        if (url != null) "Scan the code with your phone camera, or open $url in its browser. Your phone must be on the same Wi-Fi."
                        else "The TV isn't connected to a network yet. Connect to Wi-Fi or Ethernet to use the phone remote.",
                        GlassType.body, color = c.textSoft,
                    )
                    Spacer(Modifier.height(8.dp))
                    GlassText("A 4-digit code will appear here to pair it. No app needed.", GlassType.body, color = c.textSoft)
                    if (connected > 0) {
                        Spacer(Modifier.height(8.dp))
                        GlassText("● $connected phone${if (connected == 1) "" else "s"} connected", GlassType.label, color = c.rose)
                    }
                    Spacer(Modifier.height(24.dp))
                    PillButton("Done", onClose, Modifier.focusRequester(close))
                }
            }
        }
    }
}

@Composable
private fun AmbientScreen(now: LocalDateTime, settings: Settings) {
    val c = LocalGlass.current
    // Ambient fully replaces the home screen: its own orbs, then the clock.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        OrbBackground(settings.reduceMotion, settings.extraBlur)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlassText(now.format(DateTimeFormatter.ofPattern("h:mm")), GlassType.ambientClock, maxLines = 1)
            GlassText(
                now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), GlassType.greeting,
                color = c.textSoft, maxLines = 1,
            )
            Spacer(Modifier.height(28.dp))
            dev.glasstv.launcher.ui.components.LoveNote(size = 34.sp, animate = !settings.reduceMotion, center = true)
            val app = (androidx.compose.ui.platform.LocalContext.current.applicationContext as GlassApp)
            val events by app.calendar.events.collectAsState()
            remember(events, now) { app.calendar.upcoming(now).firstOrNull() }?.let { e ->
                Spacer(Modifier.height(22.dp))
                GlassText("${e.emoji}  ${e.title} · ${e.whenText(now)}", GlassType.title, color = c.textSoft, maxLines = 1)
            }
        }
    }
}
