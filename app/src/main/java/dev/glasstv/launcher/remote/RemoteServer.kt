package dev.glasstv.launcher.remote

import android.content.Context
import android.util.Log
import dev.glasstv.launcher.data.PairedPhone
import dev.glasstv.launcher.data.SettingsRepository
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoWSD.WebSocket
import fi.iki.elonen.NanoWSD.WebSocketFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class RemoteServer(
    private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val pairedPhones: StateFlow<List<PairedPhone>>,
    private val scope: CoroutineScope,
    /** Favorites (then other apps) as {pkg,label,icon(data URL)} for the phone's shortcut grid. */
    private val shortcuts: suspend () -> org.json.JSONArray,
) : NanoWSD(PORT) {

    private val pairing = Pairing()
    private val lastPairStart = AtomicLong(0)
    private val page: ByteArray by lazy { context.assets.open("remote/index.html").use { it.readBytes() } }

    fun ensureStarted() {
        if (isAlive) return
        try {
            start(SOCKET_TIMEOUT_MS, true)
            Log.i(TAG, "Phone remote listening on :$PORT")
        } catch (e: IOException) {
            Log.e(TAG, "Remote server failed to start", e)
        }
    }

    fun cancelPairing() {
        pairing.cancel(); RemoteBus.showPairingCode(null)
    }

    override fun serveHttp(session: IHTTPSession): Response {
        val uri = session.uri
        return when {
            session.method == Method.GET && (uri == "/" || uri == "/index.html") ->
                newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", page.inputStream(), page.size.toLong())
                    .withSecurityHeaders()
            session.method == Method.POST && uri == "/api/pair/start" -> startPairing()
            session.method == Method.POST && uri == "/api/pair/confirm" -> confirmPairing(session)
            else -> json(Response.Status.NOT_FOUND, JSONObject().put("error", "not found"))
        }
    }

    private fun startPairing(): Response {
        val now = System.currentTimeMillis()
        // Throttle so nobody can spam the TV with pairing popups.
        if (now - lastPairStart.get() < 3000 && pairing.activeCode() != null) {
            return json(Response.Status.OK, JSONObject().put("ok", true))
        }
        lastPairStart.set(now)
        val code = pairing.start()
        RemoteBus.showPairingCode(code)
        scope.launch {
            delay(120_000)
            if (pairing.activeCode() == null) RemoteBus.showPairingCode(null)
        }
        return json(Response.Status.OK, JSONObject().put("ok", true))
    }

    private fun confirmPairing(session: IHTTPSession): Response {
        val body = readJson(session) ?: return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "bad request"))
        val name = body.optString("name").take(40).ifBlank { "Phone" }
        return when (val r = pairing.confirm(body.optString("code"))) {
            is Pairing.Result.Paired -> {
                RemoteBus.showPairingCode(null)
                val id = UUID.randomUUID().toString()
                scope.launch { settingsRepo.addPhone(PairedPhone(id, name, Pairing.hash(r.token), System.currentTimeMillis())) }
                json(Response.Status.OK, JSONObject().put("token", r.token).put("id", id))
            }
            Pairing.Result.WrongCode -> {
                if (pairing.activeCode() == null) RemoteBus.showPairingCode(null)
                json(Response.Status.FORBIDDEN, JSONObject().put("error", "wrong_code"))
            }
            Pairing.Result.NoActiveCode -> {
                RemoteBus.showPairingCode(null)
                json(Response.Status.FORBIDDEN, JSONObject().put("error", "expired"))
            }
        }
    }

    private fun readJson(session: IHTTPSession): JSONObject? = runCatching {
        val len = session.headers["content-length"]?.toIntOrNull() ?: return null
        if (len !in 1..4096) return null
        val buf = ByteArray(len)
        var read = 0
        while (read < len) {
            val n = session.inputStream.read(buf, read, len - read)
            if (n < 0) break
            read += n
        }
        JSONObject(String(buf, 0, read))
    }.getOrNull()

    override fun openWebSocket(handshake: IHTTPSession): WebSocket {
        // Reject cross-site pages in the phone's browser from driving the TV.
        val origin = handshake.headers["origin"]
        val host = handshake.headers["host"]
        val sameOrigin = origin == null || (host != null && origin.substringAfter("://") == host)
        return Socket(handshake, sameOrigin)
    }

    private fun isAuthorized(token: String): Boolean {
        if (token.length != 64) return false
        val h = Pairing.hash(token)
        return pairedPhones.value.any { MessageDigest.isEqual(it.tokenHash.toByteArray(), h.toByteArray()) }
    }

    private inner class Socket(handshake: IHTTPSession, private val sameOrigin: Boolean) : WebSocket(handshake) {
        private var token: String? = null
        private var counted = false

        override fun onOpen() {
            if (!sameOrigin) runCatching { close(WebSocketFrame.CloseCode.PolicyViolation, "origin", false) }
        }

        override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
            if (counted) RemoteBus.phoneConnected(-1)
            counted = false
        }

        override fun onMessage(frame: WebSocketFrame) {
            val msg = runCatching { JSONObject(frame.textPayload) }.getOrNull() ?: return
            val t = token
            if (t == null || !isAuthorized(t)) {
                // Also re-checked on every message so revoking a phone in Settings takes effect immediately.
                if (msg.optString("t") == "auth" && isAuthorized(msg.optString("token"))) {
                    token = msg.optString("token")
                    if (!counted) { counted = true; RemoteBus.phoneConnected(+1) }
                    reply(JSONObject().put("t", "ok"))
                    scope.launch { runCatching { reply(JSONObject().put("t", "apps").put("apps", shortcuts())) } }
                } else {
                    reply(JSONObject().put("t", "unauthorized"))
                    runCatching { close(WebSocketFrame.CloseCode.PolicyViolation, "unauthorized", false) }
                }
                return
            }
            when (msg.optString("t")) {
                "ping" -> reply(JSONObject().put("t", "pong"))
                "key" -> keyOf(msg.optString("k"))?.let { RemoteBus.send(RemoteCommand.Key(it)) }
                "text" -> msg.optString("v").take(500).let {
                    RemoteBus.send(RemoteCommand.Text(it, msg.optBoolean("submit")))
                }
                "backspace" -> RemoteBus.send(RemoteCommand.Backspace)
                "launch" -> msg.optString("pkg").takeIf { it.isNotBlank() }?.let { RemoteBus.send(RemoteCommand.Launch(it)) }
                "action" -> runCatching { QuickAction.valueOf(msg.optString("a").uppercase()) }.getOrNull()
                    ?.let { RemoteBus.send(RemoteCommand.Action(it)) }
                "apps" -> scope.launch { runCatching { reply(JSONObject().put("t", "apps").put("apps", shortcuts())) } }
                "vol" -> RemoteBus.send(RemoteCommand.Volume(when (msg.optString("d")) { "up" -> 1; "down" -> -1; else -> 0 }))
            }
        }

        private fun reply(o: JSONObject) { runCatching { send(o.toString()) } }
        override fun onPong(pong: WebSocketFrame?) {}
        override fun onException(exception: IOException?) {}
    }

    private fun keyOf(k: String) = when (k) {
        "up" -> RemoteKey.UP; "down" -> RemoteKey.DOWN; "left" -> RemoteKey.LEFT; "right" -> RemoteKey.RIGHT
        "ok" -> RemoteKey.OK; "back" -> RemoteKey.BACK; "home" -> RemoteKey.HOME
        "playpause" -> RemoteKey.PLAY_PAUSE; "rew" -> RemoteKey.REWIND; "ff" -> RemoteKey.FAST_FORWARD
        else -> null
    }

    private fun json(status: Response.Status, o: JSONObject): Response =
        newFixedLengthResponse(status, "application/json", o.toString()).withSecurityHeaders()

    private fun Response.withSecurityHeaders(): Response = apply {
        addHeader("Cache-Control", "no-store")
        addHeader("X-Content-Type-Options", "nosniff")
        addHeader("X-Frame-Options", "DENY")
        addHeader("Referrer-Policy", "no-referrer")
        addHeader("Content-Security-Policy", "default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self' ws: ; img-src 'self' data:")
    }

    companion object {
        const val PORT = 8765
        private const val SOCKET_TIMEOUT_MS = 30_000
        private const val TAG = "GlassRemote"
    }
}
