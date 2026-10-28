package dev.glasstv.launcher.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AssistantState(
    val messages: List<ChatMessage> = emptyList(),
    val streaming: Boolean = false,
    val error: AssistantUnavailable? = null,
)

/**
 * Conversation state, kept for the life of the launcher process so closing the panel
 * doesn't lose context. Local commands are resolved before anything goes to the PC.
 */
class Assistant(
    private val scope: CoroutineScope,
    private val config: () -> Pair<String, String>,
    private val apps: () -> Map<String, String>,
    private val onCommand: (LocalCommand) -> Unit,
    private val calendar: dev.glasstv.launcher.data.CalendarStore? = null,
    private val homeHandler: ((String) -> String?)? = null,
) {
    private val _state = MutableStateFlow(AssistantState())
    val state: StateFlow<AssistantState> = _state
    private var job: Job? = null

    /** Text being composed, shared by the TV keyboard and the phone keyboard. */
    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft
    fun setDraft(v: String) { _draft.value = v.take(500) }

    /** Phone keyboard input. Submitting sends the draft. */
    fun type(text: String, submit: Boolean, backspace: Boolean) {
        when {
            backspace -> _draft.value = _draft.value.dropLast(1)
            else -> setDraft(_draft.value + text)
        }
        if (submit) { val d = _draft.value; _draft.value = ""; send(d) }
    }

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty()) return
        (homeHandler?.invoke(prompt) ?: calendarReply(prompt))?.let { reply ->
            appendLocal(prompt, reply)
            return
        }
        LocalCommands.parse(prompt, apps())?.let { cmd ->
            val reply = when (cmd) {
                is LocalCommand.OpenApp -> "Opening ${cmd.label}…"
                LocalCommand.OpenSettings -> "Opening settings…"
                LocalCommand.Ambient -> "Going ambient. Press any button to wake."
                LocalCommand.Home -> "Here you go."
                LocalCommand.OpenCalendar -> "Opening your calendar…"
                is LocalCommand.EventAdded -> "Added ${cmd.event.title}."
            }
            appendLocal(prompt, reply)
            onCommand(cmd)
            return
        }

        job?.cancel()
        val history = _state.value.messages + ChatMessage("user", prompt)
        _state.value = AssistantState(history + ChatMessage("assistant", ""), streaming = true)
        val (host, model) = config()
        job = scope.launch {
            val sb = StringBuilder()
            try {
                OllamaClient.chat(host, model, history).collect { chunk ->
                    sb.append(chunk)
                    _state.value = _state.value.copy(messages = history + ChatMessage("assistant", sb.toString()))
                }
                _state.value = _state.value.copy(streaming = false)
            } catch (e: AssistantUnavailable) {
                _state.value = AssistantState(history, streaming = false, error = e)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = AssistantState(history, false, AssistantUnavailable("Something went wrong", e.message ?: ""))
            }
        }
    }

    /** Adds an on-device exchange. A model reply still streaming would overwrite it, so that stream is stopped. */
    private fun appendLocal(prompt: String, reply: String) {
        job?.cancel()
        _state.value = _state.value.copy(
            messages = _state.value.messages + ChatMessage("user", prompt) + ChatMessage("assistant", reply),
            streaming = false, error = null,
        )
    }

    /** Calendar requests are handled on the TV (no PC needed): add events, or read the schedule back. */
    private fun calendarReply(prompt: String): String? {
        val cal = calendar ?: return null
        val now = java.time.LocalDateTime.now()
        EventParser.parse(prompt, now)?.let { p ->
            val e = dev.glasstv.launcher.data.CalEvent(title = p.title, start = p.start, allDay = p.allDay, emoji = p.emoji)
            cal.add(e)
            onCommand(LocalCommand.EventAdded(e))
            return "Added ${e.emoji} ${e.title} — ${e.whenText(now)} ♡ I'll remind you 10 minutes before."
        }
        val ask = Regex(
            """\b(what'?s|what is|what do we have|what are we doing|do we have|show|anything)\b.*\b(calendar|schedule|plans?|planned|on (?:today|tomorrow|this week)|today|tomorrow|this week|weekend)\b""",
            RegexOption.IGNORE_CASE,
        )
        if (!ask.containsMatchIn(prompt)) return null
        onCommand(LocalCommand.OpenCalendar)
        val today = now.toLocalDate()
        val end = when {
            prompt.contains("today", true) || prompt.contains("tonight", true) -> today
            prompt.contains("tomorrow", true) -> today.plusDays(1)
            prompt.contains("weekend", true) -> today.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY))
            else -> today.plusDays(7)
        }
        val start = if (prompt.contains("tomorrow", true)) today.plusDays(1) else today
        val list = cal.upcoming(now).filter { val d = it.start.toLocalDate(); !d.isBefore(start) && !d.isAfter(end) }
        return if (list.isEmpty()) "Nothing on the calendar then — a perfect excuse for Date Night 💕"
        else "Here's what's coming up:\n" + list.take(6).joinToString("\n") { "${it.emoji} ${it.title} — ${it.whenText(now)}" }
    }

    fun clear() {
        job?.cancel()
        _state.value = AssistantState()
    }

    fun dismissError() { _state.value = _state.value.copy(error = null) }
}
