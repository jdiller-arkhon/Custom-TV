package dev.glasstv.launcher.ui.screens

import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.glasstv.launcher.BuildConfigInfo
import dev.glasstv.launcher.GlassApp
import dev.glasstv.launcher.a11y.HomeAccessibilityService
import dev.glasstv.launcher.ai.OllamaClient
import dev.glasstv.launcher.ai.AssistantUnavailable
import dev.glasstv.launcher.data.Settings
import dev.glasstv.launcher.data.ThemeMode
import dev.glasstv.launcher.data.WeatherClient
import dev.glasstv.launcher.remote.NetworkInfo
import dev.glasstv.launcher.remote.RemoteBus
import dev.glasstv.launcher.ui.components.GlassButton
import dev.glasstv.launcher.ui.components.GlassText
import dev.glasstv.launcher.ui.components.GlassTextField
import dev.glasstv.launcher.ui.components.PillButton
import dev.glasstv.launcher.ui.components.QrCode
import dev.glasstv.launcher.ui.components.glassPanel
import dev.glasstv.launcher.ui.theme.GlassType
import dev.glasstv.launcher.ui.theme.LocalGlass
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(app: GlassApp, s: Settings, onClose: () -> Unit, onShowQr: () -> Unit) {
    val c = LocalGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = app.settingsRepo
    val first = remember { FocusRequester() }
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Box(Modifier.fillMaxSize().background(c.background.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 120.dp, vertical = 32.dp)
                .glassPanel(RoundedCornerShape(36.dp), 28.dp)
                .background(c.background.copy(alpha = 0.75f))
                .focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup(),
            contentPadding = PaddingValues(horizontal = 44.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassText("Settings", GlassType.greeting.copy(fontSize = GlassType.greeting.fontSize * 1.3f), Modifier.weight(1f))
                    PillButton("Done", onClose, Modifier.focusRequester(first))
                }
            }

            section("PROFILE")
            item {
                Labeled("Greeting name") {
                    GlassTextField(s.greetingName, { scope.launch { repo.setName(it) } }, "Optional, e.g. Jess")
                }
            }

            section("APPEARANCE")
            item {
                Labeled("Theme") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ThemeMode.entries.forEach { m ->
                            PillButton(
                                when (m) { ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark"; ThemeMode.AUTO -> "Auto (dark 7pm–7am)" },
                                { scope.launch { repo.setTheme(m) } }, selected = s.theme == m,
                            )
                        }
                    }
                }
            }
            item { Toggle("Reduce motion", "Stops the drifting background.", s.reduceMotion) { scope.launch { repo.setReduceMotion(it) } } }
            if (Build.VERSION.SDK_INT >= 31) {
                item {
                    Toggle(
                        "Extra background blur", "GPU blur on the orbs. Leave off unless it stays smooth on your TV.",
                        s.extraBlur,
                    ) { scope.launch { repo.setExtraBlur(it) } }
                }
            }

            section("WEATHER")
            item { WeatherRow(app, s) }

            section("ASSISTANT")
            item { AssistantRows(app, s) }

            section("HOME BUTTON")
            item { HomeButtonRows(app, s) }

            section("FAVORITES")
            item {
                GlassText("Long-press OK on any app tile to pin or unpin it. Reorder here.", GlassType.small, color = c.textSoft)
            }
            item { PinnedEditor(app, s) }

            section("PHONE REMOTE")
            item { PhoneRows(app, s, onShowQr) }

            section("SYSTEM")
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Android TV settings", {
                        runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    })
                    PillButton("Inputs & HDMI", {
                        runCatching { context.startActivity(Intent("com.android.tv.action.VIEW_INPUTS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            .onFailure { runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                    })
                }
            }
            item { GlassText("Glass TV ${BuildConfigInfo.versionName(context)}", GlassType.small, color = c.textSoft) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String) {
    item(key = "s_$title") {
        GlassText(title, GlassType.section, Modifier.padding(top = 18.dp), color = LocalGlass.current.textSoft)
    }
}

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GlassText(label, GlassType.body, Modifier.width(240.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalGlass.current
    GlassButton(onClick = { onChange(!on) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), focusScale = 1.02f) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                GlassText(title, GlassType.body)
                GlassText(subtitle, GlassType.small, color = c.textSoft)
            }
            Box(
                Modifier.width(64.dp).height(34.dp)
                    .background(if (on) c.rose else c.text.copy(alpha = 0.15f), RoundedCornerShape(50)),
                contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
            ) { Box(Modifier.padding(4.dp).size(26.dp).background(androidx.compose.ui.graphics.Color.White, CircleShape)) }
        }
    }
}

@Composable
private fun WeatherRow(app: GlassApp, s: Settings) {
    val c = LocalGlass.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Labeled("Location") {
            GlassTextField(s.weatherPlace, { q ->
                if (q.isBlank() || q == s.weatherPlace) return@GlassTextField
                status = "Looking up \"$q\"…"
                scope.launch {
                    val p = WeatherClient.geocode(q)
                    status = if (p == null) "Couldn't find \"$q\". Try a city name, e.g. Austin." else {
                        app.settingsRepo.setWeatherPlace(p.label, p.lat, p.lon); "Set to ${p.label}"
                    }
                }
            }, "City, e.g. Austin")
        }
        Labeled("Units") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("°F", { scope.launch { app.settingsRepo.setFahrenheit(true) } }, selected = s.fahrenheit)
                PillButton("°C", { scope.launch { app.settingsRepo.setFahrenheit(false) } }, selected = !s.fahrenheit)
            }
        }
        status?.let { GlassText(it, GlassType.small, color = c.textSoft) }
    }
}

@Composable
private fun AssistantRows(app: GlassApp, s: Settings) {
    val c = LocalGlass.current
    val scope = rememberCoroutineScope()
    var test by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Labeled("PC address") {
            GlassTextField(s.ollamaHost, { scope.launch { app.settingsRepo.setOllama(it, s.ollamaModel) } }, "e.g. 192.168.1.50 or my-pc.local")
        }
        Labeled("Model") {
            GlassTextField(s.ollamaModel, { scope.launch { app.settingsRepo.setOllama(s.ollamaHost, it) } }, "llama3.2:3b")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("Test connection", {
                test = "Testing…"
                scope.launch {
                    test = try {
                        val reply = withTimeoutOrNull(30_000) {
                            OllamaClient.chat(s.ollamaHost, s.ollamaModel, listOf(dev.glasstv.launcher.ai.ChatMessage("user", "Reply with just: ready"))).firstOrNull()
                        }
                        if (reply != null) "Connected to ${OllamaClient.baseUrl(s.ollamaHost)} ✓" else "Connected, but the model didn't answer in 30 s."
                    } catch (e: AssistantUnavailable) {
                        "${e.title}. ${e.fix}"
                    }
                }
            })
            test?.let { GlassText(it, GlassType.small, Modifier.weight(1f), color = c.textSoft) }
        }
    }
}

@Composable
private fun HomeButtonRows(app: GlassApp, s: Settings) {
    val c = LocalGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled = remember(s) { HomeAccessibilityService.isEnabled(context) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassText(
            if (enabled) "● Home-button service is on." else "Home-button service is off. Turn on \"Glass TV Home button\" in Accessibility so Home opens Glass TV.",
            GlassType.body, color = if (enabled) c.rose else c.textSoft,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Open Accessibility settings", {
                runCatching {
                    context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            })
        }
        Toggle(
            "Open Glass TV on Home", "Turn off to get the stock Google TV home back without uninstalling.",
            s.homeOverride,
        ) { scope.launch { app.settingsRepo.setHomeOverride(it) } }
    }
}

@Composable
private fun PinnedEditor(app: GlassApp, s: Settings) {
    val c = LocalGlass.current
    val scope = rememberCoroutineScope()
    val apps by app.appRepo.apps.collectAsState()
    val labels = remember(apps) { apps.associate { it.packageName to it.label } }
    val pinned = (s.pinned ?: emptyList()).filter { it in labels }
    var adding by remember { mutableStateOf(false) }
    fun save(list: List<String>) = scope.launch { app.settingsRepo.setPinned(list) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        pinned.forEachIndexed { i, pkg ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassText("${i + 1}. ${labels[pkg]}", GlassType.body, Modifier.weight(1f), maxLines = 1)
                PillButton("↑", { if (i > 0) save(pinned.toMutableList().apply { add(i - 1, removeAt(i)) }) })
                PillButton("↓", { if (i < pinned.lastIndex) save(pinned.toMutableList().apply { add(i + 1, removeAt(i)) }) })
                PillButton("Remove", { save(pinned - pkg) })
            }
        }
        if (pinned.isEmpty()) GlassText("No favorites yet.", GlassType.body, color = c.textSoft)
        PillButton(if (adding) "Hide app list" else "Add favorites", { adding = !adding })
        if (adding) {
            apps.filter { it.packageName !in pinned }.forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassText(a.label, GlassType.body, Modifier.weight(1f), maxLines = 1)
                    PillButton("Add", { save(pinned + a.packageName) })
                }
            }
        }
    }
}

@Composable
private fun PhoneRows(app: GlassApp, s: Settings, onShowQr: () -> Unit) {
    val c = LocalGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val url = remember { NetworkInfo.remoteUrl(context) }
    val connected by RemoteBus.connectedPhones.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (url != null) QrCode(url, 150.dp)
            Spacer(Modifier.width(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassText(url ?: "Not connected to a network", GlassType.title)
                GlassText("$connected connected now · ${s.pairedPhones.size} paired", GlassType.small, color = c.textSoft)
                PillButton("Show pairing screen", onShowQr)
            }
        }
        s.pairedPhones.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassText(p.name, GlassType.body, Modifier.weight(1f), maxLines = 1)
                PillButton("Forget", { scope.launch { app.settingsRepo.removePhone(p.id) } })
            }
        }
    }
}
