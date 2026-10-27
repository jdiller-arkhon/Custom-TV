package dev.glasstv.launcher.remote

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Launcher shortcuts the phone can trigger from anywhere. */
enum class QuickAction { ASSISTANT, AMBIENT, SETTINGS, HOME }

enum class RemoteKey { UP, DOWN, LEFT, RIGHT, OK, BACK, HOME, PLAY_PAUSE, REWIND, FAST_FORWARD }

sealed interface RemoteCommand {
    data class Key(val key: RemoteKey) : RemoteCommand
    data class Text(val text: String, val submit: Boolean) : RemoteCommand
    data object Backspace : RemoteCommand
    data class Volume(val direction: Int) : RemoteCommand // +1 up, -1 down, 0 toggle mute
    data class Launch(val packageName: String) : RemoteCommand
    data class Action(val action: QuickAction) : RemoteCommand
}

/** A text field that the phone keyboard should type into. */
fun interface TextTarget {
    fun onRemoteText(text: String, submit: Boolean, backspace: Boolean)
}

object RemoteBus {
    private val _commands = MutableSharedFlow<RemoteCommand>(extraBufferCapacity = 64)
    val commands: SharedFlow<RemoteCommand> = _commands
    fun send(c: RemoteCommand) { _commands.tryEmit(c) }

    /** Pairing code to show on the TV while a phone is pairing; null when idle. */
    private val _pairingCode = MutableStateFlow<String?>(null)
    val pairingCode: StateFlow<String?> = _pairingCode
    internal fun showPairingCode(code: String?) { _pairingCode.value = code }

    private val _connectedPhones = MutableStateFlow(0)
    val connectedPhones: StateFlow<Int> = _connectedPhones
    internal fun phoneConnected(delta: Int) { _connectedPhones.value = (_connectedPhones.value + delta).coerceAtLeast(0) }

    /** Most recently focused text field inside the launcher, if any. */
    @Volatile var textTarget: TextTarget? = null
}
