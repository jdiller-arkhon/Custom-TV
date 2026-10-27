package dev.glasstv.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime

/**
 * Event reminders, 10 minutes ahead. While Glass TV is on screen they appear as a pink toast; otherwise
 * as a system notification (shown over other apps on Google TV). Runs in the app process, which the
 * phone-remote foreground service keeps alive.
 */
object Reminders {
    private const val CHANNEL = "reminders"
    private val fired = mutableSetOf<String>()

    fun start(app: GlassApp) {
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Calendar reminders", NotificationManager.IMPORTANCE_HIGH))
        app.scope.launch {
            while (true) {
                val now = LocalDateTime.now()
                app.calendar.events.value.forEach { e ->
                    val mins = Duration.between(now, e.start).toMinutes()
                    if (mins in 0..10 && fired.add(e.id)) notify(app, nm, "${e.emoji} ${e.title}", if (mins <= 0) "Starting now" else "In $mins min · ${e.whenText(now)}")
                }
                delay(30_000)
            }
        }
    }

    /** Toast on Glass TV, or a notification over other apps. */
    fun alert(app: GlassApp, title: String, text: String) = notify(app, app.getSystemService(NotificationManager::class.java), title, text)

    private fun notify(ctx: Context, nm: NotificationManager, title: String, text: String) {
        MainActivity.foreground?.let { it.ui.toast = "$title — $text"; return }
        val n = Notification.Builder(ctx, CHANNEL)
            .setContentTitle(title).setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(title.hashCode(), n) }
    }
}
