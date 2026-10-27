package dev.glasstv.launcher

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.glasstv.launcher.remote.QuickAction
import dev.glasstv.launcher.remote.RemoteBus
import dev.glasstv.launcher.remote.RemoteCommand
import dev.glasstv.launcher.remote.RemoteKey
import dev.glasstv.launcher.remote.RemoteService
import dev.glasstv.launcher.ui.screens.LauncherRoot

/** Overlay state shared by the activity (keys, HOME intents) and the Compose tree. */
class LauncherUi {
    var aiOpen by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var qrOpen by mutableStateOf(false)
    var ambient by mutableStateOf(false)
    var dateNight by mutableStateOf(false)
    /** Bumped when HOME is pressed while already home, so the grid can scroll back to the top. */
    var homeTick by mutableIntStateOf(0)

    fun goHome() {
        aiOpen = false; settingsOpen = false; qrOpen = false; ambient = false; dateNight = false
        homeTick++
    }
}

class MainActivity : ComponentActivity() {
    val ui = LauncherUi()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LauncherRoot(application as GlassApp, ui) }
        RemoteService.start(this)
    }

    override fun onResume() {
        super.onResume()
        foreground = this
        Idle.touch()
        (application as GlassApp).appRepo.refresh()
    }

    override fun onPause() {
        if (foreground === this) foreground = null
        ui.ambient = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) ui.goHome()
    }

    // RestrictedApi here is a known lint false positive on ComponentActivity's override.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Idle.touch()
        if (ui.ambient) {
            // Any button wakes ambient mode and is otherwise swallowed.
            if (event.action == KeyEvent.ACTION_UP) ui.ambient = false
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Phone remote → launcher: synthesize real key events so focus behaves exactly like the TV remote. */
    fun handleRemote(cmd: RemoteCommand) {
        when (cmd) {
            is RemoteCommand.Key -> when (cmd.key) {
                RemoteKey.HOME -> ui.goHome()
                RemoteKey.BACK -> if (ui.ambient) ui.ambient = false else onBackPressedDispatcher.onBackPressed()
                else -> inject(
                    when (cmd.key) {
                        RemoteKey.UP -> KeyEvent.KEYCODE_DPAD_UP
                        RemoteKey.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
                        RemoteKey.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
                        RemoteKey.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
                        else -> KeyEvent.KEYCODE_DPAD_CENTER
                    }
                )
            }
            is RemoteCommand.Text -> deliverText(cmd.text, cmd.submit, false)
            RemoteCommand.Backspace -> deliverText("", false, true)
            is RemoteCommand.Action -> applyAction(cmd.action)
            is RemoteCommand.Volume, is RemoteCommand.Launch -> Unit
        }
    }

    fun applyAction(a: QuickAction) {
        when (a) {
            QuickAction.HOME -> ui.goHome()
            QuickAction.ASSISTANT -> { ui.goHome(); ui.aiOpen = true }
            QuickAction.AMBIENT -> { ui.goHome(); ui.ambient = true }
            QuickAction.SETTINGS -> { ui.goHome(); ui.settingsOpen = true }
            QuickAction.DATE_NIGHT -> { ui.goHome(); ui.dateNight = true }
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        (application as GlassApp).takePendingAction()?.let(::applyAction)
    }

    private fun deliverText(text: String, submit: Boolean, backspace: Boolean) {
        if (ui.ambient) ui.ambient = false
        val target = RemoteBus.textTarget
        if (target != null) {
            target.onRemoteText(text, submit, backspace)
        } else {
            // No field focused: typing from the phone goes to the assistant.
            ui.aiOpen = true
            (application as GlassApp).assistant.type(text, submit, backspace)
        }
    }

    private fun inject(code: Int) {
        val t = SystemClock.uptimeMillis()
        // Through the DecorView so the event takes the exact path of a real remote press (incl. our override above).
        val root = window.decorView
        root.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        root.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }

    companion object {
        @Volatile var foreground: MainActivity? = null
            private set
    }
}
