package dev.glasstv.launcher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val firstTile = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }

    LaunchedEffect(apps.isNotEmpty(), interactive) {
        if (apps.isNotEmpty() && interactive) {
            delay(32)
            runCatching { firstTile.requestFocus() }
            initialFocusDone = true
        }
    }
    LaunchedEffect(ui.homeTick) {
        if (ui.homeTick > 0) { gridState.scrollToItem(0); runCatching { firstTile.requestFocus() } }
    }

    fun togglePin(pkg: String) = scope.launch {
        app.settingsRepo.setPinned(if (pkg in pinnedPkgs) pinnedPkgs - pkg else pinnedPkgs + pkg)
    }

    Column(
        modifier
            // While an overlay is up, D-pad focus can't wander back into the grid behind it.
            .focusProperties { onEnter = { if (!interactive) cancelFocusChange() } }
            .focusGroup()
            .padding(horizontal = 48.dp, vertical = 28.dp)
    ) {
        TopBar(settings, now, onSettings = { ui.settingsOpen = true })
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            state = gridState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (pinned.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "h_fav", contentType = "header") { SectionHeader("FAVORITES") }
                items(pinned, key = { "p_" + it.packageName }, contentType = { "tile" }) { a ->
                    val mod = if (a == pinned.first()) Modifier.focusRequester(firstTile) else Modifier
                    AppTile(app, a, mod, onLongClick = { togglePin(a.packageName) })
                }
            }
            if (others.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "h_all", contentType = "header") {
                    SectionHeader(if (pinned.isEmpty()) "APPS" else "ALL APPS")
                }
                items(others, key = { "a_" + it.packageName }, contentType = { "tile" }) { a ->
                    val mod = if (pinned.isEmpty() && a == others.first()) Modifier.focusRequester(firstTile) else Modifier
                    AppTile(app, a, mod, onLongClick = { togglePin(a.packageName) })
                }
            }
        }
        Dock(ui, Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun SectionHeader(text: String) {
    GlassText(text, GlassType.section, Modifier.padding(start = 4.dp, top = 4.dp), color = LocalGlass.current.textSoft)
}

@Composable
private fun TopBar(settings: Settings, now: LocalDateTime, onSettings: () -> Unit) {
    val c = LocalGlass.current
    val greeting = when (now.hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Good night" }
    val weather = rememberWeather(settings)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            GlassText(
                if (settings.greetingName.isBlank()) greeting else "$greeting, ${settings.greetingName}",
                GlassType.greeting, color = c.textSoft, maxLines = 1,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                GlassText(now.format(DateTimeFormatter.ofPattern("h:mm")), GlassType.clock, maxLines = 1)
                Spacer(Modifier.width(20.dp))
                GlassText(
                    now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), GlassType.title,
                    Modifier.padding(bottom = 16.dp), color = c.textSoft, maxLines = 1,
                )
            }
        }
        if (weather != null) {
            GlassPanel(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassText(weather.glyph, GlassType.title.copy(fontSize = GlassType.clock.fontSize * 0.45f))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        GlassText("${weather.temp}°", GlassType.title)
                        GlassText(listOf(weather.description, settings.weatherPlace.substringBefore(',')).filter { it.isNotBlank() }.joinToString(" · "), GlassType.small, color = c.textSoft, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.width(20.dp))
        }
        GlassButton(onClick = onSettings, shape = CircleShape, modifier = Modifier.size(64.dp)) {
            GlassText("⚙", GlassType.title.copy(fontSize = GlassType.title.fontSize * 1.3f))
        }
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

@Composable
private fun AppTile(app: GlassApp, tvApp: TvApp, modifier: Modifier, onLongClick: () -> Unit) {
    val c = LocalGlass.current
    var art by remember(tvApp.packageName) { mutableStateOf<AppArt?>(null) }
    LaunchedEffect(tvApp.packageName) { art = app.appRepo.art(tvApp) }
    GlassButton(
        onClick = { app.appRepo.launch(tvApp) },
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
        shape = RoundedCornerShape(22.dp),
    ) {
        val a = art
        when {
            a != null && a.isBanner -> Image(
                a.image, tvApp.label, Modifier.fillMaxSize().padding(6.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop,
            )
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (a != null) Image(a.image, null, Modifier.size(56.dp))
                Spacer(Modifier.height(8.dp))
                GlassText(tvApp.label, GlassType.label, Modifier.padding(horizontal = 10.dp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun Dock(ui: LauncherUi, modifier: Modifier) {
    val c = LocalGlass.current
    Row(
        modifier.glassPanel(RoundedCornerShape(40.dp)).padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassButton(onClick = { ui.aiOpen = true }, shape = CircleShape, modifier = Modifier.size(60.dp)) {
            Box(
                Modifier.size(40.dp).background(
                    Brush.radialGradient(listOf(Color.White, c.pink, c.rose, c.cocoa)), CircleShape,
                )
            )
        }
        PillButton("Ambient", { ui.ambient = true })
        PillButton("Settings", { ui.settingsOpen = true })
        PillButton("Phone remote", { ui.qrOpen = true })
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
    Box(Modifier.fillMaxSize().background(c.background.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlassText(now.format(DateTimeFormatter.ofPattern("h:mm")), GlassType.ambientClock, maxLines = 1)
            GlassText(
                now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), GlassType.greeting,
                color = c.textSoft, maxLines = 1,
            )
        }
    }
}
