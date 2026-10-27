package dev.glasstv.launcher

import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import dev.glasstv.launcher.a11y.HomeAccessibilityService
import dev.glasstv.launcher.ai.HomeCommand
import dev.glasstv.launcher.ai.HomeCommands
import dev.glasstv.launcher.ai.EventParser
import dev.glasstv.launcher.data.Countdown
import dev.glasstv.launcher.data.WatchItem
import dev.glasstv.launcher.remote.QuickAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

data class RunningTimer(val id: String = UUID.randomUUID().toString(), val label: String, val endsAt: Long, val total: Duration)

/** Timers, the sleep timer, and Grace's handling of the built-in apps. */
class HomeServices(private val app: GlassApp) {
    private val _timers = MutableStateFlow<List<RunningTimer>>(emptyList())
    val timers: StateFlow<List<RunningTimer>> = _timers
    private val _sleepAt = MutableStateFlow<Long?>(null)
    /** Elapsed-realtime when the sleep timer fires, or null. */
    val sleepAt: StateFlow<Long?> = _sleepAt
    private var sleepJob: Job? = null

    fun startTimer(label: String, d: Duration) {
        val t = RunningTimer(label = label, endsAt = SystemClock.elapsedRealtime() + d.toMillis(), total = d)
        _timers.value = _timers.value + t
        app.scope.launch {
            delay(d.toMillis())
            if (_timers.value.none { it.id == t.id }) return@launch
            _timers.value = _timers.value.filterNot { it.id == t.id }
            Reminders.alert(app, "⏰ ${t.label} timer is done!", "Ding ding ♡")
        }
    }

    fun cancelTimer(id: String) { _timers.value = _timers.value.filterNot { it.id == id } }

    /** After [minutes]: pause whatever is playing, then show ambient mode (cozy and dark). */
    fun sleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) { _sleepAt.value = null; return }
        _sleepAt.value = SystemClock.elapsedRealtime() + minutes * 60_000L
        sleepJob = app.scope.launch {
            delay(minutes * 60_000L)
            _sleepAt.value = null
            val audio = app.getSystemService(AudioManager::class.java)
            val t = SystemClock.uptimeMillis()
            audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0))
            audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0))
            val fg = MainActivity.foreground
            if (fg != null) fg.applyAction(QuickAction.AMBIENT)
            else { app.pendingActionSet(QuickAction.AMBIENT); HomeAccessibilityService.instance?.openLauncher() }
        }
    }

    /** Grace's on-device handler for the built-in apps. Returns her reply, or null if this isn't for them. */
    fun handle(prompt: String): String? {
        val home = app.home
        return when (val cmd = HomeCommands.parse(prompt) ?: return null) {
            is HomeCommand.AddToList -> {
                cmd.items.forEach { home.addItem(cmd.list, it) }
                "Added ${cmd.items.joinToString(", ")} to ${cmd.list} 🛒"
            }
            is HomeCommand.ReadList -> {
                val items = home.state.value.items.filter { it.list == cmd.list && !it.done }
                if (items.isEmpty()) "${cmd.list} is empty ♡" else "${cmd.list}:\n" + items.joinToString("\n") { "• ${it.text}" }
            }
            is HomeCommand.StartTimer -> { startTimer(cmd.label, cmd.duration); "⏰ ${cmd.label} timer set for ${fmt(cmd.duration)}. I'll ding when it's done!" }
            is HomeCommand.SleepTimer -> { sleepTimer(cmd.minutes); "🌙 Sleep timer on — I'll pause and dim in ${cmd.minutes} minutes. Sweet dreams ♡" }
            is HomeCommand.AddCountdown -> {
                home.addCountdown(Countdown(title = cmd.title, date = cmd.date, emoji = EventParser.emojiFor(cmd.title).takeIf { it != "📌" } ?: "✨"))
                val days = ChronoUnit.DAYS.between(LocalDate.now(), cmd.date)
                "✨ Counting down: ${days} days until ${cmd.title}!"
            }
            is HomeCommand.Remember -> { home.remember(cmd.fact); "Got it, I'll remember: ${cmd.fact} ♡" }
            is HomeCommand.Recall -> {
                val facts = home.state.value.memories.filter { cmd.about == null || it.fact.contains(cmd.about, true) }
                if (facts.isEmpty()) "I don't have anything saved about that yet." else facts.takeLast(8).joinToString("\n") { "• ${it.fact}" }
            }
            is HomeCommand.AddToWatchlist -> { home.addWatch(WatchItem(title = cmd.title)); "🍿 Saved ${cmd.title} to your watchlist." }
            is HomeCommand.OpenApp -> {
                MainActivity.foreground?.ui?.let { ui -> ui.aiOpen = false; ui.openApp = BuiltInApps.fromSpoken(cmd.app) }
                "Opening ${cmd.app}…"
            }
        }
    }

    private fun fmt(d: Duration) = when {
        d.toHours() >= 1 && d.toMinutes() % 60 == 0L -> "${d.toHours()} hour${if (d.toHours() > 1) "s" else ""}"
        d.toMinutes() >= 1 -> "${d.toMinutes()} min"
        else -> "${d.seconds} sec"
    }
}
