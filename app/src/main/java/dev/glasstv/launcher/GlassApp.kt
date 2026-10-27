package dev.glasstv.launcher

import android.app.Application
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import dev.glasstv.launcher.a11y.HomeAccessibilityService
import dev.glasstv.launcher.ai.Assistant
import dev.glasstv.launcher.ai.LocalCommand
import dev.glasstv.launcher.data.AppRepository
import dev.glasstv.launcher.data.Settings
import dev.glasstv.launcher.data.SettingsRepository
import dev.glasstv.launcher.remote.QuickAction
import dev.glasstv.launcher.remote.RemoteBus
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import dev.glasstv.launcher.remote.RemoteCommand
import dev.glasstv.launcher.remote.RemoteKey
import dev.glasstv.launcher.remote.RemoteServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GlassApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var settingsRepo: SettingsRepository
    lateinit var appRepo: AppRepository
    lateinit var remoteServer: RemoteServer
    lateinit var assistant: Assistant

    /** null until DataStore has loaded, so the first frame never flashes the wrong theme. */
    lateinit var settings: StateFlow<Settings?>

    /** Commands the assistant resolves locally that the UI must act on (settings, ambient, home). */
    val uiCommands = MutableSharedFlow<LocalCommand>(extraBufferCapacity = 8)

    override fun onCreate() {
        super.onCreate()
        settingsRepo = SettingsRepository(this)
        settings = settingsRepo.settings.stateIn(scope, SharingStarted.Eagerly, null)
        appRepo = AppRepository(this, scope)
        remoteServer = RemoteServer(
            this, settingsRepo,
            settingsRepo.settings.map { it.pairedPhones }.stateIn(scope, SharingStarted.Eagerly, emptyList()),
            scope,
            shortcuts = ::shortcutsJson,
        )
        assistant = Assistant(
            scope,
            config = { settingsNow().let { it.ollamaHost to it.ollamaModel } },
            apps = { appRepo.apps.value.associate { it.packageName to it.label } },
            onCommand = { cmd ->
                if (cmd is LocalCommand.OpenApp) appRepo.launch(cmd.packageName) else uiCommands.tryEmit(cmd)
            },
        )
        seedDefaultPins()
        scope.launch { RemoteBus.commands.collect(::route) }
    }

    fun settingsNow(): Settings = settings.value ?: Settings()

    private fun seedDefaultPins() = scope.launch {
        val s = settingsRepo.settings.first()
        if (s.pinned != null) return@launch
        // Wait for the first app scan, then pin whichever defaults are installed.
        combine(appRepo.apps, settingsRepo.settings) { apps, st -> apps to st }
            .first { (apps, _) -> apps.isNotEmpty() }
        settingsRepo.setPinned(appRepo.defaultPins())
    }

    /** Phone-remote routing: the launcher gets full control; other apps get what Android allows. */
    private fun route(cmd: RemoteCommand) {
        Idle.touch()
        val audio = getSystemService(AudioManager::class.java)
        when {
            cmd is RemoteCommand.Volume -> audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                when (cmd.direction) { 1 -> AudioManager.ADJUST_RAISE; -1 -> AudioManager.ADJUST_LOWER; else -> AudioManager.ADJUST_TOGGLE_MUTE },
                AudioManager.FLAG_SHOW_UI,
            )
            cmd is RemoteCommand.Key && cmd.key in mediaKeys -> {
                // Media keys go to the active media session (Netflix, YouTube, Spotify…) — no special permission.
                val code = mediaKeys.getValue(cmd.key)
                val t = SystemClock.uptimeMillis()
                audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
                audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
            }
            cmd is RemoteCommand.Launch -> {
                // Only launch packages that are actually installed launchable apps.
                if (appRepo.apps.value.none { it.packageName == cmd.packageName }) return
                val a11y = HomeAccessibilityService.instance
                if (MainActivity.foreground == null && a11y != null) a11y.launchPackage(cmd.packageName)
                else appRepo.launch(cmd.packageName)
            }
            cmd is RemoteCommand.Action && MainActivity.foreground == null -> {
                // Bring the launcher forward, then apply the action once it resumes.
                pendingAction = cmd.action
                HomeAccessibilityService.instance?.openLauncher()
            }
            MainActivity.foreground != null -> MainActivity.foreground?.handleRemote(cmd)
            else -> HomeAccessibilityService.instance?.handle(cmd)
        }
    }

    @Volatile private var pendingAction: QuickAction? = null
    fun takePendingAction(): QuickAction? = pendingAction.also { pendingAction = null }

    private suspend fun shortcutsJson(): JSONArray {
        val apps = appRepo.apps.value
        val pinned = settingsNow().pinned.orEmpty()
        val ordered = pinned.mapNotNull { p -> apps.firstOrNull { it.packageName == p } } +
            apps.filter { it.packageName !in pinned && it.isLeanback }
        val out = JSONArray()
        for (a in ordered.take(24)) {
            val icon = withContext(Dispatchers.Default) { appRepo.iconDataUrl(a) }
            out.put(JSONObject().put("pkg", a.packageName).put("label", a.label).put("pinned", a.packageName in pinned).put("icon", icon ?: ""))
        }
        return out
    }

    private val mediaKeys = mapOf(
        RemoteKey.PLAY_PAUSE to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        RemoteKey.REWIND to KeyEvent.KEYCODE_MEDIA_REWIND,
        RemoteKey.FAST_FORWARD to KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
    )
}

/** Last user interaction, for ambient mode. */
object Idle {
    @Volatile var last: Long = SystemClock.uptimeMillis()
        private set
    fun touch() { last = SystemClock.uptimeMillis() }
}
