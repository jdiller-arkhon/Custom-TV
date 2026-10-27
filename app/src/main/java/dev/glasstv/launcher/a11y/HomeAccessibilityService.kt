package dev.glasstv.launcher.a11y

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.glasstv.launcher.GlassApp
import dev.glasstv.launcher.MainActivity
import dev.glasstv.launcher.remote.RemoteCommand
import dev.glasstv.launcher.remote.RemoteKey

/**
 * Makes Glass TV the effective home screen without modifying the system (the Projectivy approach):
 *  1. If the firmware delivers KEYCODE_HOME to accessibility services, we consume it and open Glass TV.
 *  2. Otherwise, when the stock home *activity* comes to the front, we immediately bring Glass TV forward.
 *     Only the exact HOME activity is matched, so the stock launcher's settings panel / inputs still work.
 * Turning this service off in Settings → Accessibility fully restores stock behaviour.
 *
 * It also executes phone-remote commands while another app is in the foreground.
 */
class HomeAccessibilityService : AccessibilityService() {
    private var stockHome: ComponentName? = null
    private var lastLaunch = 0L

    override fun onServiceConnected() {
        instance = this
        stockHome = resolveStockHome()
        // Turn the TV on → straight to Anna's home, even before anyone presses Home.
        if (SystemClock.elapsedRealtime() < 5 * 60_000L && (application as GlassApp).settingsNow().homeOverride) openLauncher()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    private val overrideEnabled get() = (application as GlassApp).settingsNow().homeOverride

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME || !overrideEnabled) return false
        if (event.action == KeyEvent.ACTION_UP) openLauncher()
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || !overrideEnabled) return
        val home = stockHome ?: return
        if (event.packageName?.toString() == home.packageName && event.className?.toString() == home.className) {
            openLauncher()
        }
    }

    fun openLauncher() {
        val now = SystemClock.uptimeMillis()
        if (now - lastLaunch < 800) return
        lastLaunch = now
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        )
    }

    /** Accessibility services may start activities from the background; plain apps can't (API 29+). */
    fun launchPackage(pkg: String) {
        val i = packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg) ?: return
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        (application as GlassApp).appRepo.onLaunched?.invoke(pkg)
    }

    /** The system's HOME activity other than ours, e.g. com.google.android.apps.tv.launcherx/...HomeActivity. */
    private fun resolveStockHome(): ComponentName? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.queryIntentActivities(intent, 0)
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
            .firstOrNull { it.packageName != packageName && it.packageName != "com.android.settings" && !it.packageName.contains("fallbackhome", true) }
    }

    /** Remote commands while another app is focused. Returns false if not possible. */
    fun handle(cmd: RemoteCommand): Boolean = when (cmd) {
        is RemoteCommand.Key -> when (cmd.key) {
            RemoteKey.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            RemoteKey.HOME -> { openLauncher(); true }
            RemoteKey.UP -> moveFocus(View.FOCUS_UP)
            RemoteKey.DOWN -> moveFocus(View.FOCUS_DOWN)
            RemoteKey.LEFT -> moveFocus(View.FOCUS_LEFT)
            RemoteKey.RIGHT -> moveFocus(View.FOCUS_RIGHT)
            RemoteKey.OK -> focused()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
            else -> false
        }
        is RemoteCommand.Text -> typeText(cmd.text, append = true)
        RemoteCommand.Backspace -> backspace()
        is RemoteCommand.Volume, is RemoteCommand.Launch, is RemoteCommand.Action -> false
    }

    /**
     * Best effort only: works in apps built from standard Android views. Apps that draw their own UI
     * (Netflix, YouTube, Disney+, most players) don't expose focusable nodes, so this does nothing there.
     * See docs/phone-remote.md.
     */
    private fun moveFocus(direction: Int): Boolean {
        val node = focused() ?: return false
        val next = node.focusSearch(direction) ?: return false
        return next.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    }

    private fun focused(): AccessibilityNodeInfo? =
        rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

    private fun typeText(text: String, append: Boolean): Boolean {
        val node = focused()?.takeIf { it.isEditable } ?: return false
        val current = if (append && !node.isShowingHintText) node.text?.toString().orEmpty() else ""
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, current + text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun backspace(): Boolean {
        val node = focused()?.takeIf { it.isEditable } ?: return false
        val current = if (node.isShowingHintText) "" else node.text?.toString().orEmpty()
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, current.dropLast(1))
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    companion object {
        @Volatile var instance: HomeAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(context, HomeAccessibilityService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
