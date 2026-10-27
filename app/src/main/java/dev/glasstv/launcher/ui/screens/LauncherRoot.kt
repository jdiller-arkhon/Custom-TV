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

            val anyOverlay = ui.aiOpen || ui.settingsOpen || ui.qrOpen
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

            PairingCodeToast(Modifier.align(Alignment.TopCenter).padding(top = 32.dp))

            AnimatedVisibility(ui.ambient, enter = fadeIn(), exit = fadeOut()) {
                AmbientScreen(now, settings)
            }
        }
    }
}

/**
 * "Cozy room" home.
 *  Left  — Grace's nook: Grace herself, Anna's greeting, Grace's suggestions, the ask/search box, controls.
 *  Right — the Spotlight: a big stage for whichever app is focused (the room's light follows it),
 *          favorites floating on a gentle wave beneath, and a quieter row of everything else.
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
    val apps by app.appRepo.apps.collectAsState()
    val pinnedPkgs = settings.pinned ?: emptyList()
    val byPkg = remember(apps) { apps.associateBy { it.packageName } }
    val pinned = remember(pinnedPkgs, byPkg) { pinnedPkgs.mapNotNull { byPkg[it] } }
    val others = remember(pinnedPkgs, apps) { apps.filter { it.packageName !in pinnedPkgs } }
    val recent = remember(settings.recents, byPkg) { settings.recents.mapNotNull { byPkg[it] } }
    val favorites = if (pinned.isNotEmpty()) pinned else others.take(8)
    val rest = if (pinned.isNotEmpty()) others else others.drop(8)

    var query by remember { mutableStateOf("") }
    val results = remember(query, apps) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) emptyList() else apps.filter { it.label.lowercase().contains(q) }
            .sortedBy { if (it.label.lowercase().startsWith(q)) 0 else 1 }
    }
    // The spotlight shows the focused app; before anything is focused, the last one Anna used.
    var spotlight by remember { mutableStateOf<TvApp?>(null) }
    val shown = spotlight ?: recent.firstOrNull() ?: favorites.firstOrNull()
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(apps.isNotEmpty(), interactive) {
        // Items may not be composed on the first frame; retry briefly.
        if (apps.isNotEmpty() && interactive) repeat(10) { delay(50); if (runCatching { firstFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect }
    }
    LaunchedEffect(ui.homeTick) {
        if (ui.homeTick > 0) { query = ""; runCatching { firstFocus.requestFocus() } }
    }
    fun togglePin(pkg: String) = scope.launch {
        app.settingsRepo.setPinned(if (pkg in pinnedPkgs) pinnedPkgs - pkg else pinnedPkgs + pkg)
    }
    fun submit(q: String) {
        val text = q.trim()
        if (text.isEmpty()) return
        val hits = apps.filter { it.label.lowercase().contains(text.lowercase()) }
        if (hits.size == 1) { app.appRepo.launch(hits.first()); query = "" }
        else { ui.aiOpen = true; app.assistant.send(text); query = "" }
    }

    Row(
        modifier
            // While an overlay is up, D-pad focus can't wander back into the home screen behind it.
            .then(if (interactive) Modifier else Modifier.focusProperties { onEnter = { cancelFocusChange() } })
            .focusGroup()
            .padding(28.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        GraceNook(app, ui, settings, now, query, { query = it }, ::submit, Modifier.width(300.dp).fillMaxHeight())

        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            if (query.isNotBlank()) {
                SectionHeader("Results for “${query.trim()}”")
                if (results.isEmpty()) GlassText(
                    "No apps by that name. Press OK in the box to ask Grace instead ♡",
                    GlassType.body, Modifier.padding(8.dp), color = LocalGlass.current.textSoft,
                ) else WaveRow(app, results, null, { spotlight = it; onAccent(it) }, ::togglePin, bubble = 104.dp)
            } else {
                if (shown != null) Spotlight(app, shown, shown in recent.take(3))
                Spacer(Modifier.height(18.dp))
                SectionHeader(if (pinned.isNotEmpty()) "Your favorites ♡" else "Your apps")
                WaveRow(app, favorites, firstFocus, { spotlight = it; onAccent(it) }, ::togglePin, bubble = 104.dp)
                if (rest.isNotEmpty()) {
                    SectionHeader("Everything else")
                    rest.chunked(9).forEach { WaveRow(app, it, null, { a -> spotlight = a; onAccent(a) }, ::togglePin, bubble = 76.dp, amplitude = 0f) }
                }
            }
        }
    }
}

/** Left column: Grace front and centre, Anna's greeting, suggestions, ask/search, and the little controls. */
@Composable
private fun GraceNook(
    app: GlassApp, ui: LauncherUi, settings: Settings, now: LocalDateTime,
    query: String, onQuery: (String) -> Unit, onSubmit: (String) -> Unit, modifier: Modifier,
) {
    val c = LocalGlass.current
    val (greeting, mood) = when (now.hour) {
        in 5..11 -> "Good morning," to "Coffee and cartoons? ☕"
        in 12..16 -> "Hi there," to "A little treat of a show? ♡"
        in 17..21 -> "Cozy evening," to "Blanket-and-movie weather ♡"
        else -> "Sweet dreams soon," to "One more episode, then bed ☾"
    }
    val weather = rememberWeather(settings)
    // Grace breathes: a slow, tiny scale pulse (still when reduce motion is on).
    val breath = if (settings.reduceMotion) 1f else androidx.compose.animation.core.rememberInfiniteTransition(label = "breath").animateFloat(
        0.96f, 1.04f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(3200), androidx.compose.animation.core.RepeatMode.Reverse), label = "b",
    ).value
    Column(modifier.glassPanel(RoundedCornerShape(40.dp), 10.dp).padding(horizontal = 24.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            dev.glasstv.launcher.ui.components.CuteOrb(60.dp, Modifier.graphicsLayer { scaleX = breath; scaleY = breath })
            Spacer(Modifier.width(14.dp))
            Column {
                GlassText(now.format(DateTimeFormatter.ofPattern("h:mm")), GlassType.greeting.copy(fontSize = GlassType.greeting.fontSize * 1.25f))
                GlassText(
                    listOfNotNull(now.format(DateTimeFormatter.ofPattern("EEE, MMM d")), weather?.let { "${it.glyph} ${it.temp}°" }).joinToString("  ·  "),
                    GlassType.small, color = c.textSoft, maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        GlassText(greeting, GlassType.title, color = c.textSoft)
        GlassText(settings.greetingName, GlassType.ambientClock.copy(fontSize = GlassType.clock.fontSize * 0.72f, lineHeight = GlassType.clock.fontSize * 0.8f), maxLines = 1)
        GlassText(mood, GlassType.label, color = c.textSoft)

        Spacer(Modifier.height(12.dp))
        GlassText("${dev.glasstv.launcher.Personal.ASSISTANT} suggests", GlassType.section, color = c.textSoft)
        Spacer(Modifier.height(6.dp))
        listOf(
            "Movie night picks 🍿" to "What should we watch tonight? Suggest 4 options across moods.",
            "Something funny ☺" to "Recommend 4 feel-good comedies, a mix of films and series.",
        ).forEach { (label, prompt) ->
            PillButton(label, { ui.aiOpen = true; app.assistant.send(prompt) }, Modifier.padding(bottom = 6.dp))
        }

        Spacer(Modifier.weight(1f))
        dev.glasstv.launcher.ui.components.GlassTextField(
            value = query, onCommit = onSubmit, onChange = onQuery,
            placeholder = "⌕  Ask Grace…",
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(50),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIcon("☾") { ui.ambient = true }
            GlassButton(onClick = { ui.qrOpen = true }, shape = CircleShape, modifier = Modifier.size(48.dp)) {
                // Phone glyph drawn in-palette (unicode options render inconsistently on TV fonts).
                Box(Modifier.size(13.dp, 21.dp).border(2.2.dp, c.text, RoundedCornerShape(4.dp)), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.padding(bottom = 3.dp).size(4.dp).background(c.text, CircleShape))
                }
            }
            CircleIcon("⚙") { ui.settingsOpen = true }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(48.dp).background(Brush.linearGradient(listOf(c.rose, c.cocoa)), CircleShape),
                contentAlignment = Alignment.Center,
            ) { GlassText(settings.greetingName.take(1).uppercase(), GlassType.title, color = Color.White) }
        }
    }
}

/** The stage: big art for the focused app, its name, and a cue. Not focusable itself; OK on a bubble opens. */
@Composable
private fun Spotlight(app: GlassApp, tvApp: TvApp, isRecent: Boolean) {
    val c = LocalGlass.current
    var art by remember(tvApp.packageName) { mutableStateOf<AppArt?>(null) }
    LaunchedEffect(tvApp.packageName) { art = app.appRepo.art(tvApp) }
    val tint = art?.tint?.takeIf { it != 0 }?.let { Color(it) } ?: c.rose
    androidx.compose.animation.Crossfade(tvApp, animationSpec = androidx.compose.animation.core.tween(350), label = "spot") { shownApp ->
        Box(
            Modifier.fillMaxWidth().height(232.dp)
                .glassPanel(RoundedCornerShape(40.dp), 10.dp)
                .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.30f), Color.Transparent, c.pink.copy(alpha = 0.25f))))
        ) {
            val a = art
            if (a != null && a.isBanner) {
                Image(a.image, null, Modifier.align(Alignment.CenterEnd).fillMaxHeight().aspectRatio(16f / 9f).padding(18.dp).clip(RoundedCornerShape(28.dp)), contentScale = ContentScale.Crop)
            } else if (a != null) {
                Image(a.image, null, Modifier.align(Alignment.CenterEnd).padding(end = 64.dp).size(150.dp).clip(RoundedCornerShape(38.dp)))
            }
            Column(Modifier.align(Alignment.CenterStart).padding(start = 34.dp).width(300.dp)) {
                GlassText(if (isRecent) "Jump back in ✨" else "Up next", GlassType.section, color = c.textSoft)
                Spacer(Modifier.height(6.dp))
                GlassText(shownApp.label, GlassType.ambientClock.copy(fontSize = GlassType.clock.fontSize * 0.75f), maxLines = 2)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.background(c.cocoa, RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 8.dp)) {
                    GlassText("Press OK to open", GlassType.label, color = Color.White)
                }
            }
        }
    }
}

/** Round "bubble" app icons riding a gentle wave. Focus spotlights the app; OK opens it. */
@Composable
private fun WaveRow(
    app: GlassApp, apps: List<TvApp>, focus: FocusRequester?, onFocused: (TvApp) -> Unit,
    onLongClick: (String) -> Unit, bubble: androidx.compose.ui.unit.Dp, amplitude: Float = 14f,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        apps.forEachIndexed { i, a ->
            key(a.packageName) {
                BubbleTile(
                    app, a, bubble,
                    Modifier.offset(y = (amplitude * kotlin.math.sin(i * 0.9f)).dp),
                    focus = if (i == 0) focus else null,
                    onFocused = { onFocused(a) },
                    onLongClick = { onLongClick(a.packageName) },
                )
            }
        }
    }
}

@Composable
private fun BubbleTile(
    app: GlassApp, tvApp: TvApp, size: androidx.compose.ui.unit.Dp, modifier: Modifier,
    focus: FocusRequester?, onFocused: () -> Unit, onLongClick: () -> Unit,
) {
    var art by remember(tvApp.packageName) { mutableStateOf<AppArt?>(null) }
    LaunchedEffect(tvApp.packageName) { art = app.appRepo.art(tvApp) }
    GlassButton(
        onClick = { app.appRepo.launch(tvApp) },
        onLongClick = onLongClick,
        modifier = modifier.size(size)
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .onFocusChanged { if (it.isFocused) onFocused() },
        shape = RoundedCornerShape(size * 0.34f),
        focusScale = 1.14f,
    ) {
        val a = art
        when {
            a == null -> GlassText(tvApp.label.take(1), GlassType.title)
            a.isBanner -> Image(a.image, tvApp.label, Modifier.fillMaxSize().clip(RoundedCornerShape(size * 0.34f)), contentScale = ContentScale.Crop)
            else -> Image(a.image, tvApp.label, Modifier.fillMaxSize(0.62f).clip(RoundedCornerShape(size * 0.18f)))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    GlassText(text, GlassType.title, Modifier.padding(start = 6.dp, top = 4.dp), color = LocalGlass.current.text)
}

@Composable
private fun CircleIcon(glyph: String, onClick: () -> Unit) {
    GlassButton(onClick = onClick, shape = CircleShape, modifier = Modifier.size(48.dp)) {
        GlassText(glyph, GlassType.title)
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
        }
    }
}
