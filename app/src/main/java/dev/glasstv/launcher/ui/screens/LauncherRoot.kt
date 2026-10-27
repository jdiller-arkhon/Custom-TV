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
 * "The Anna Edition" — the home screen as a luxury magazine page.
 *  Masthead: ANNA nameplate, issue line, time, weather.
 *  Cover story (left): the focused app as the feature, slowly drifting.
 *  Contents (right): favorites as a numbered table of contents; focus draws a rouge rule.
 *  Foot: "Dear Grace, …" (ask/search) and Justin's signed note in the margin.
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
    val recent = remember(settings.recents, byPkg) { settings.recents.mapNotNull { byPkg[it] } }
    val contents = (if (pinned.isNotEmpty()) pinned else others).take(9)
    val more = (if (pinned.isNotEmpty()) others else others.drop(9))

    var query by remember { mutableStateOf("") }
    val results = remember(query, apps) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) emptyList() else apps.filter { it.label.lowercase().contains(q) }
            .sortedBy { if (it.label.lowercase().startsWith(q)) 0 else 1 }.take(9)
    }
    var cover by remember { mutableStateOf<TvApp?>(null) }
    val shown = cover ?: recent.firstOrNull() ?: contents.firstOrNull()
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }

    // Print-reveal: rules draw, masthead letters settle, contents rise — replayed on each return home.
    val motion = !settings.reduceMotion
    val intro = remember { androidx.compose.animation.core.Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(ui.homeTick, motion) {
        if (!motion) { intro.snapTo(1f); return@LaunchedEffect }
        intro.snapTo(0f)
        intro.animateTo(1f, androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.LinearEasing))
    }
    val p = intro.value
    fun stage(start: Float, len: Float) = ((p - start) / len).coerceIn(0f, 1f)
    fun easeOut(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)

    LaunchedEffect(apps.isNotEmpty(), interactive) {
        // Items may not be composed on the first frame; retry briefly.
        if (apps.isNotEmpty() && interactive) repeat(10) { delay(50); if (runCatching { firstFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect }
    }
    LaunchedEffect(ui.homeTick) { if (ui.homeTick > 0) { query = ""; runCatching { firstFocus.requestFocus() } } }
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

    Column(
        modifier
            .then(if (interactive) Modifier else Modifier.focusProperties { onEnter = { cancelFocusChange() } })
            .focusGroup()
            .padding(horizontal = 56.dp, vertical = 26.dp)
    ) {
        Masthead(app, ui, settings, now, reveal = { i -> easeOut(stage(0.05f + i * 0.05f, 0.35f)) })
        Rule(easeOut(stage(0f, 0.45f)), thick = true)
        Spacer(Modifier.height(18.dp))

        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            // Cover story
            Box(Modifier.weight(1.25f).fillMaxHeight().graphicsLayer { val s = easeOut(stage(0.2f, 0.5f)); alpha = s; translationY = (1 - s) * 40f }) {
                if (shown != null) CoverStory(app, shown, isRecent = shown in recent.take(3), animate = motion)
            }
            // Contents
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text2("IN THIS ISSUE", GlassType.kicker, c.rose)
                Spacer(Modifier.height(6.dp))
                Rule(easeOut(stage(0.25f, 0.4f)))
                val list = if (query.isNotBlank()) results else contents
                if (query.isNotBlank() && results.isEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    GlassText("Nothing by that name. Press OK on the letter below and Grace will answer.", GlassType.body, color = c.textSoft)
                }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    list.forEachIndexed { i, a ->
                        key(a.packageName) {
                            ContentsRow(
                                app, i + 1, a,
                                focus = if (i == 0 && query.isBlank()) firstFocus else null,
                                onFocused = { cover = a; onAccent(a) },
                                onLongClick = { togglePin(a.packageName) },
                                modifier = Modifier.graphicsLayer {
                                    val s = easeOut(stage(0.35f + i * 0.05f, 0.3f)); alpha = s; translationY = (1 - s) * 30f
                                },
                            )
                        }
                    }
                    if (query.isBlank() && more.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text2("ALSO INSIDE", GlassType.kicker, c.textSoft)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            more.forEach { a -> key(a.packageName) { MiniIcon(app, a, onFocused = { cover = a; onAccent(a) }, onLongClick = { togglePin(a.packageName) }) } }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Rule(easeOut(stage(0.5f, 0.4f)))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.graphicsLayer { alpha = easeOut(stage(0.6f, 0.4f)) }) {
            dev.glasstv.launcher.ui.components.CuteOrb(40.dp)
            Spacer(Modifier.width(12.dp))
            Text2("Dear ${dev.glasstv.launcher.Personal.ASSISTANT},", GlassType.contents.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), c.text)
            Spacer(Modifier.width(14.dp))
            dev.glasstv.launcher.ui.components.GlassTextField(
                value = query, onCommit = ::submit, onChange = { query = it },
                placeholder = "what should we watch tonight?",
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.width(40.dp))
            dev.glasstv.launcher.ui.components.LoveNote(size = 24.sp, animate = motion)
        }
    }
}

@Composable
private fun Text2(text: String, style: androidx.compose.ui.text.TextStyle, color: Color, modifier: Modifier = Modifier) =
    androidx.compose.foundation.text.BasicText(text, modifier, style.copy(color = color), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)

/** Printed hairline that draws itself left→right as [progress] goes 0→1. */
@Composable
private fun Rule(progress: Float, thick: Boolean = false) {
    val c = LocalGlass.current
    Box(Modifier.fillMaxWidth().height(if (thick) 3.dp else 1.dp)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(if (thick) c.text else c.highlightTop))
    }
    if (thick) {
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(1.dp).background(c.text))
    }
}

@Composable
private fun Masthead(app: GlassApp, ui: LauncherUi, settings: Settings, now: LocalDateTime, reveal: (Int) -> Float) {
    val c = LocalGlass.current
    val weather = rememberWeather(settings)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        // Nameplate: each letter drops into place in turn.
        Row {
            settings.greetingName.uppercase().forEachIndexed { i, ch ->
                val r = reveal(i)
                Text2(ch.toString(), GlassType.masthead, c.text, Modifier.graphicsLayer { alpha = r; translationY = (1 - r) * -50f })
            }
        }
        Spacer(Modifier.width(26.dp))
        Column(Modifier.weight(1f).padding(bottom = 14.dp)) {
            Text2("THE ${dev.glasstv.launcher.Personal.NAME.uppercase()} EDITION", GlassType.kicker, c.rose)
            Spacer(Modifier.height(4.dp))
            Text2(
                "No. ${now.dayOfYear}  ·  ${now.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy"))}",
                GlassType.folio, c.textSoft,
            )
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 10.dp)) {
            Text2(now.format(DateTimeFormatter.ofPattern("h:mm")), GlassType.clock.copy(fontSize = GlassType.clock.fontSize * 0.75f), c.text)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (weather != null) Text2("${weather.glyph} ${weather.temp}°  ${weather.description}", GlassType.folio, c.textSoft)
                MastLink("AMBIENT") { ui.ambient = true }
                MastLink("REMOTE") { ui.qrOpen = true }
                MastLink("SETTINGS") { ui.settingsOpen = true }
            }
        }
    }
}

/** Small-caps text link; focus = rouge ink with an underline. */
@Composable
private fun MastLink(text: String, onClick: () -> Unit) {
    val c = LocalGlass.current
    var focused by remember { mutableStateOf(false) }
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { focused = it.isFocused },
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent, focusedContainerColor = Color.Transparent, pressedContainerColor = Color.Transparent,
        ),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        glow = androidx.tv.material3.ClickableSurfaceDefaults.glow(androidx.tv.material3.Glow.None, androidx.tv.material3.Glow.None),
    ) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            Text2(text, GlassType.folio, if (focused) c.rose else c.textSoft)
            Box(Modifier.height(2.dp).fillMaxWidth(if (focused) 1f else 0f).background(c.rose))
        }
    }
}

/** The feature: the app's own art, large, with a slow drift; kicker + italic title + call to action. */
@Composable
private fun CoverStory(app: GlassApp, tvApp: TvApp, isRecent: Boolean, animate: Boolean) {
    val c = LocalGlass.current
    val drift = if (!animate) 0.5f else androidx.compose.animation.core.rememberInfiniteTransition(label = "kb").animateFloat(
        0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(14000), androidx.compose.animation.core.RepeatMode.Reverse), label = "kbv",
    ).value
    androidx.compose.animation.Crossfade(tvApp, animationSpec = androidx.compose.animation.core.tween(450), label = "cover") { a ->
        var art by remember(a.packageName) { mutableStateOf<AppArt?>(null) }
        LaunchedEffect(a.packageName) { art = app.appRepo.art(a) }
        val tint = art?.tint?.takeIf { it != 0 }?.let { Color(it) } ?: c.rose
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.22f), c.pink.copy(alpha = 0.6f), c.glassFill))),
                contentAlignment = Alignment.Center,
            ) {
                val img = art
                if (img != null) {
                    // Ken Burns: a slow push-in and pan on the cover art.
                    val k = 1.0f + 0.08f * drift
                    Image(
                        img.image, a.label,
                        Modifier.then(if (img.isBanner) Modifier.fillMaxSize() else Modifier.size(170.dp))
                            .graphicsLayer { scaleX = k; scaleY = k; translationX = (drift - 0.5f) * 30f }
                            .clip(RoundedCornerShape(if (img.isBanner) 0.dp else 40.dp)),
                        contentScale = if (img.isBanner) ContentScale.Crop else ContentScale.Fit,
                    )
                }
                Text2(
                    if (isRecent) "NOW SHOWING" else "FEATURED",
                    GlassType.kicker, Color.White,
                    Modifier.align(Alignment.TopStart).padding(16.dp).background(c.rose).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text2(a.label, GlassType.coverTitle, c.text)
            Text2("Press OK to begin  →", GlassType.folio, c.textSoft)
        }
    }
}

/** "01  Netflix ……… [icon]" — focus draws a rouge rule beneath and lifts the number into rouge. */
@Composable
private fun ContentsRow(
    app: GlassApp, n: Int, tvApp: TvApp, focus: FocusRequester?, onFocused: () -> Unit, onLongClick: () -> Unit, modifier: Modifier,
) {
    val c = LocalGlass.current
    var art by remember(tvApp.packageName) { mutableStateOf<AppArt?>(null) }
    LaunchedEffect(tvApp.packageName) { art = app.appRepo.art(tvApp) }
    var focused by remember { mutableStateOf(false) }
    val line by androidx.compose.animation.core.animateFloatAsState(if (focused) 1f else 0f, androidx.compose.animation.core.tween(380), label = "rule")
    androidx.tv.material3.Surface(
        onClick = { app.appRepo.launch(tvApp) },
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth()
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() },
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent, focusedContainerColor = c.glassFill, pressedContainerColor = c.pink,
        ),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        glow = androidx.tv.material3.ClickableSurfaceDefaults.glow(androidx.tv.material3.Glow.None, androidx.tv.material3.Glow(c.shadow, 14.dp)),
    ) {
        Column(Modifier.padding(horizontal = 10.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text2("%02d".format(n), GlassType.folio, if (focused) c.rose else c.textSoft, Modifier.width(40.dp))
                Text2(tvApp.label, GlassType.contents, c.text, Modifier.weight(1f))
                val a = art
                if (a != null) Image(a.image, null, Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)), contentScale = if (a.isBanner) ContentScale.Crop else ContentScale.Fit)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.highlightTop)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(line).background(c.rose))
            }
        }
    }
}

@Composable
private fun MiniIcon(app: GlassApp, tvApp: TvApp, onFocused: () -> Unit, onLongClick: () -> Unit) {
    var art by remember(tvApp.packageName) { mutableStateOf<AppArt?>(null) }
    LaunchedEffect(tvApp.packageName) { art = app.appRepo.art(tvApp) }
    GlassButton(
        onClick = { app.appRepo.launch(tvApp) }, onLongClick = onLongClick,
        modifier = Modifier.size(52.dp).onFocusChanged { if (it.isFocused) onFocused() },
        shape = RoundedCornerShape(14.dp), focusScale = 1.15f,
    ) {
        val a = art
        if (a != null) Image(a.image, tvApp.label, Modifier.fillMaxSize(0.7f).clip(RoundedCornerShape(8.dp)))
        else GlassText(tvApp.label.take(1), GlassType.label)
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
        }
    }
}
