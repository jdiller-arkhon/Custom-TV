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
 * Apple TV–style home, personalised:
 *   top-right: greeting, clock, weather and a profile initial
 *   "Jump back in": two large cards for the apps you used last (favorites until there's history)
 *   beside them: an Assistant card
 *   a frosted search bar, then the app row (favorites), then everything else.
 */
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
    val recent = remember(settings.recents, byPkg) { settings.recents.mapNotNull { byPkg[it] } }
    val hero = remember(recent, pinned, others) { (recent + pinned + others).distinct().take(2) }
    var query by remember { mutableStateOf("") }
    val results = remember(query, apps) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) emptyList() else apps.filter { it.label.lowercase().contains(q) }
            .sortedBy { if (it.label.lowercase().startsWith(q)) 0 else 1 }
    }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(apps.isNotEmpty(), interactive) {
        // Items may not be composed on the first frame; retry briefly.
        if (apps.isNotEmpty() && interactive) repeat(10) { delay(50); if (runCatching { firstFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect }
    }
    LaunchedEffect(ui.homeTick) {
        if (ui.homeTick > 0) { query = ""; scroll.scrollTo(0); runCatching { firstFocus.requestFocus() } }
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
            .verticalScroll(scroll)
            .padding(horizontal = 56.dp, vertical = 26.dp)
    ) {
        TopBar(settings, now, ui)
        Spacer(Modifier.height(14.dp))

        if (query.isBlank()) {
            SectionHeader(if (recent.isNotEmpty()) "Jump back in" else "Up next")
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                hero.forEachIndexed { i, a ->
                    HeroCard(app, a, Modifier.weight(1f), focus = if (i == 0) firstFocus else null)
                }
                AssistantCard(app, ui, Modifier.weight(0.8f))
            }
        }

        Spacer(Modifier.height(16.dp))
        SearchBar(
            query = query,
            onQuery = { query = it },
            onSubmit = ::submitSearch,
            modifier = Modifier.align(Alignment.CenterHorizontally).width(620.dp),
        )
        Spacer(Modifier.height(8.dp))

        if (query.isNotBlank()) {
            if (results.isEmpty()) GlassText(
                "No apps match \"${query.trim()}\". Press OK in the search bar to ask the assistant.",
                GlassType.body, Modifier.padding(12.dp), color = LocalGlass.current.textSoft,
            ) else AppRow(app, results, null, ::togglePin)
        } else {
            AppRow(app, if (pinned.isNotEmpty()) pinned else others.take(10), if (hero.isEmpty()) firstFocus else null, ::togglePin)
            val rest = if (pinned.isNotEmpty()) others else others.drop(10)
            rest.chunked(6).forEach { AppRow(app, it, null, ::togglePin) }
        }
    }
}

/** A row of tvOS app tiles. Plain scrolling Row: LazyRow rejected programmatic initial focus. */
@Composable
private fun AppRow(app: GlassApp, apps: List<TvApp>, focus: FocusRequester?, onLongClick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        apps.forEachIndexed { i, a ->
            key(a.packageName) {
                AppTile(app, a, Modifier.width(150.dp), onLongClick = { onLongClick(a.packageName) }, focus = if (i == 0) focus else null)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    GlassText(text, GlassType.title, Modifier.padding(start = 4.dp), color = LocalGlass.current.text)
}

/** Large "top shelf" card: the app's own banner art, name and a soft label. */
@Composable
private fun HeroCard(app: GlassApp, tvApp: TvApp, modifier: Modifier, focus: FocusRequester?) {
    val c = LocalGlass.current
    var art by remember(tvApp.packageName) { mutableStateOf<AppArt?>(null) }
    LaunchedEffect(tvApp.packageName) { art = app.appRepo.art(tvApp) }
    GlassButton(
        onClick = { app.appRepo.launch(tvApp) },
        modifier = modifier.aspectRatio(16f / 9f).then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
        shape = RoundedCornerShape(28.dp),
        focusScale = 1.05f,
    ) {
        val a = art
        Box(Modifier.fillMaxSize()) {
            if (a != null && a.isBanner) {
                Image(a.image, null, Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)), contentScale = ContentScale.Crop)
            } else {
                Box(
                    Modifier.fillMaxSize().background(Brush.linearGradient(listOf(c.pink.copy(alpha = 0.55f), c.lilac.copy(alpha = 0.45f))), RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.TopEnd,
                ) { if (a != null) Image(a.image, null, Modifier.padding(22.dp).size(72.dp).clip(RoundedCornerShape(18.dp))) }
            }
            // Bottom caption on a soft cocoa fade, like tvOS's shelf labels.
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, c.cocoa.copy(alpha = 0.55f))), RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Column {
                    GlassText("Open", GlassType.small, color = Color.White.copy(alpha = 0.8f))
                    GlassText(tvApp.label, GlassType.title, color = Color.White, maxLines = 1)
                }
            }
        }
    }
}

/** The assistant, in the shelf: one press opens it with a suggestion ready. */
@Composable
private fun AssistantCard(app: GlassApp, ui: LauncherUi, modifier: Modifier) {
    val c = LocalGlass.current
    GlassButton(
        onClick = { ui.aiOpen = true },
        modifier = modifier.aspectRatio(16f / 11.25f),
        shape = RoundedCornerShape(28.dp),
        focusScale = 1.05f,
    ) {
        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(52.dp).background(Brush.radialGradient(listOf(Color.White, c.pink, c.rose, c.cocoa)), CircleShape))
            Column {
                GlassText("Ask Glass", GlassType.title)
                GlassText("Recommendations, recaps, or just say “open Netflix”", GlassType.label, color = c.textSoft, maxLines = 3)
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
            GlassText(
                if (settings.greetingName.isBlank()) greeting else "$greeting, ${settings.greetingName}",
                GlassType.greeting, maxLines = 1,
            )
            GlassText(
                listOfNotNull(
                    now.format(DateTimeFormatter.ofPattern("h:mm")),
                    now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                    weather?.let { "${it.glyph} ${it.temp}°" },
                ).joinToString("  ·  "),
                GlassType.label, color = c.textSoft, maxLines = 1,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIcon("☾") { ui.ambient = true }
            GlassButton(onClick = { ui.qrOpen = true }, shape = CircleShape, modifier = Modifier.size(52.dp)) {
                // Phone glyph drawn in-palette (unicode options render inconsistently on TV fonts).
                Box(Modifier.size(14.dp, 23.dp).border(2.3.dp, c.text, RoundedCornerShape(4.dp)), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.padding(bottom = 3.dp).size(4.dp).background(c.text, CircleShape))
                }
            }
            CircleIcon("⚙") { ui.settingsOpen = true }
            // Profile: the greeting name's initial, opens Settings (where the name lives).
            GlassButton(onClick = { ui.settingsOpen = true }, shape = CircleShape, modifier = Modifier.size(52.dp)) {
                Box(
                    Modifier.size(52.dp).background(Brush.linearGradient(listOf(c.rose, c.cocoa)), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { GlassText(settings.greetingName.take(1).uppercase().ifEmpty { "☺" }, GlassType.title, color = Color.White) }
            }
        }
    }
}

@Composable
private fun CircleIcon(glyph: String, onClick: () -> Unit) {
    GlassButton(onClick = onClick, shape = CircleShape, modifier = Modifier.size(52.dp)) {
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
