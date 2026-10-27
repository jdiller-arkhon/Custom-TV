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

    GlassTheme(dark) {
        Box(Modifier.fillMaxSize()) {
            OrbBackground(settings.reduceMotion, settings.extraBlur)

            val anyOverlay = ui.aiOpen || ui.settingsOpen || ui.qrOpen
            HomeScreen(app, ui, settings, now, Modifier.fillMaxSize(), interactive = !anyOverlay && !ui.ambient)

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
 * tvOS-style home: a row of large rounded app tiles (favorites), a frosted search bar underneath,
 * then the rest of the apps. Controls live in a small cluster top-right instead of a dock.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HomeScreen(
    app: GlassApp,
    ui: LauncherUi,
    settings: Settings,
    now: LocalDateTime,
    modifier: Modifier,
    interactive: Boolean,
) {
    val apps by app.appRepo.apps.collectAsState()
    val pinnedPkgs = settings.pinned ?: emptyList()
    val byPkg = remember(apps) { apps.associateBy { it.packageName } }
    val pinned = remember(pinnedPkgs, byPkg) { pinnedPkgs.mapNotNull { byPkg[it] } }
    val others = remember(pinnedPkgs, apps) { apps.filter { it.packageName !in pinnedPkgs } }
    var query by remember { mutableStateOf("") }
    val results = remember(query, apps) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) emptyList() else apps.filter { it.label.lowercase().contains(q) }
            .sortedBy { if (it.label.lowercase().startsWith(q)) 0 else 1 }
    }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val firstTile = remember { FocusRequester() }

    LaunchedEffect(apps.isNotEmpty(), interactive) {
        // The row's first item may not be composed on the first frame; retry briefly.
        if (apps.isNotEmpty() && interactive) repeat(10) { delay(50); if (runCatching { firstTile.requestFocus() }.getOrDefault(false)) return@LaunchedEffect }
    }
    LaunchedEffect(ui.homeTick) {
        if (ui.homeTick > 0) { query = ""; gridState.scrollToItem(0); runCatching { firstTile.requestFocus() } }
    }
    fun togglePin(pkg: String) = scope.launch {
        app.settingsRepo.setPinned(if (pkg in pinnedPkgs) pinnedPkgs - pkg else pinnedPkgs + pkg)
    }
    fun submitSearch(q: String) {
        val text = q.trim()
        if (text.isEmpty()) return
        val hits = apps.filter { it.label.lowercase().contains(text.lowercase()) }
        if (hits.size == 1) { app.appRepo.launch(hits.first()); query = "" }
        else { ui.aiOpen = true; app.assistant.send(text); query = "" }
    }

    Column(
        modifier
            // While an overlay is up, D-pad focus can't wander back into the home screen behind it.
            .then(if (interactive) Modifier else Modifier.focusProperties { onEnter = { cancelFocusChange() } })
            .focusGroup()
            .padding(horizontal = 56.dp, vertical = 28.dp)
    ) {
        TopBar(settings, now, ui)
        Spacer(Modifier.height(18.dp))

        // Top row: favorites as big tvOS tiles (falls back to all apps if nothing is pinned).
        val topRow = (if (pinned.isNotEmpty()) pinned else others).take(12)
        // A plain scrolling Row (≤12 tiles): LazyRow rejected programmatic initial focus on its items.
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 14.dp),
        ) {
            topRow.forEach { a ->
                key(a.packageName) { AppTile(
                    app, a,
                    Modifier.width(164.dp),
                    focus = if (a == topRow.first()) firstTile else null,
                    onLongClick = { togglePin(a.packageName) },
                ) }
            }
        }

        Spacer(Modifier.height(6.dp))
        SearchBar(
            query = query,
            onQuery = { query = it },
            onSubmit = ::submitSearch,
            modifier = Modifier.align(Alignment.CenterHorizontally).width(640.dp),
        )
        Spacer(Modifier.height(10.dp))

        val gridApps = if (query.isNotBlank()) results else (if (pinned.isNotEmpty()) others else others.drop(12))
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            state = gridState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (query.isNotBlank() && results.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "noresults") {
                    GlassText(
                        "No apps match \"${query.trim()}\". Press OK on the search bar to ask the assistant.",
                        GlassType.body, Modifier.padding(8.dp), color = LocalGlass.current.textSoft,
                    )
                }
            }
            items(gridApps, key = { "g_" + it.packageName }, contentType = { "tile" }) { a ->
                AppTile(app, a, Modifier.fillMaxWidth(), onLongClick = { togglePin(a.packageName) })
            }
        }
    }
}

@Composable
private fun TopBar(settings: Settings, now: LocalDateTime, ui: LauncherUi) {
    val c = LocalGlass.current
    val greeting = when (now.hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Good night" }
    val weather = rememberWeather(settings)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                GlassText(now.format(DateTimeFormatter.ofPattern("h:mm")), GlassType.clock.copy(fontSize = GlassType.clock.fontSize * 0.8f), maxLines = 1)
                Spacer(Modifier.width(18.dp))
                Column(Modifier.padding(bottom = 10.dp)) {
                    GlassText(
                        if (settings.greetingName.isBlank()) greeting else "$greeting, ${settings.greetingName}",
                        GlassType.title, maxLines = 1,
                    )
                    GlassText(
                        listOfNotNull(
                            now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                            weather?.let { "${it.glyph} ${it.temp}° ${it.description}" },
                        ).joinToString("   ·   "),
                        GlassType.label, color = c.textSoft, maxLines = 1,
                    )
                }
            }
        }
        // Control cluster (replaces the dock): assistant · ambient · phone remote · settings.
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassButton(onClick = { ui.aiOpen = true }, shape = CircleShape, modifier = Modifier.size(58.dp)) {
                Box(Modifier.size(34.dp).background(Brush.radialGradient(listOf(Color.White, c.pink, c.rose, c.cocoa)), CircleShape))
            }
            CircleIcon("☾") { ui.ambient = true }
            GlassButton(onClick = { ui.qrOpen = true }, shape = CircleShape, modifier = Modifier.size(58.dp)) {
                // Phone glyph drawn in-palette (the unicode options render inconsistently on TV fonts).
                Box(Modifier.size(16.dp, 26.dp).border(2.5.dp, c.text, RoundedCornerShape(5.dp)), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.padding(bottom = 3.dp).size(4.dp).background(c.text, CircleShape))
                }
            }
            CircleIcon("⚙") { ui.settingsOpen = true }
        }
    }
}

@Composable
private fun CircleIcon(glyph: String, onClick: () -> Unit) {
    GlassButton(onClick = onClick, shape = CircleShape, modifier = Modifier.size(58.dp)) {
        GlassText(glyph, GlassType.title.copy(fontSize = GlassType.title.fontSize * 1.15f))
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
            shape = RoundedCornerShape(26.dp),
            focusScale = 1.10f,
        ) {
            val a = art
            when {
                a != null && a.isBanner -> Image(
                    a.image, tvApp.label, Modifier.fillMaxSize().clip(RoundedCornerShape(26.dp)), contentScale = ContentScale.Crop,
                )
                else -> Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(c.glassFillFocused, c.pink.copy(alpha = 0.35f))), RoundedCornerShape(26.dp),
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
