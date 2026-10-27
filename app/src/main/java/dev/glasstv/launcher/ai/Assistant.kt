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
        LocalCommands.parse(prompt, apps())?.let { cmd ->
            val reply = when (cmd) {
                is LocalCommand.OpenApp -> "Opening ${cmd.label}…"
                LocalCommand.OpenSettings -> "Opening settings…"
                LocalCommand.Ambient -> "Going ambient. Press any button to wake."
                LocalCommand.Home -> "Here you go."
            }
            _state.value = _state.value.copy(
                messages = _state.value.messages + ChatMessage("user", prompt) + ChatMessage("assistant", reply),
                error = null,
            )
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

    fun clear() {
        job?.cancel()
        _state.value = AssistantState()
    }

    fun dismissError() { _state.value = _state.value.copy(error = null) }
}
