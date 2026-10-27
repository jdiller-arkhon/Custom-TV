package dev.glasstv.launcher.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

data class ChatMessage(val role: String, val content: String)

/** A failure the UI can explain in plain language, with the fix. */
class AssistantUnavailable(val title: String, val fix: String) : Exception(title)

object OllamaClient {
    private const val SYSTEM_PROMPT =
        "You are the assistant built into a living-room TV. Replies are read from a couch 10 feet away: " +
            "answer in at most 3 short sentences or 5 short bullet points, no markdown headings, no tables. " +
            "For recommendations give titles with one-line reasons. Never invent facts about cast or plots; say if unsure."

    fun baseUrl(host: String): String {
        val h = host.trim().trimEnd('/')
        val withScheme = if (h.startsWith("http://") || h.startsWith("https://")) h else "http://$h"
        // Default Ollama port when the user typed only a hostname or IP.
        return if (Regex("""^https?://[^/]+:\d+""").containsMatchIn(withScheme)) withScheme else "$withScheme:11434"
    }

    /** Streams content chunks from /api/chat. Throws [AssistantUnavailable] with a user-facing explanation. */
    fun chat(host: String, model: String, history: List<ChatMessage>): Flow<String> = flow {
        if (host.isBlank()) throw AssistantUnavailable(
            "The assistant isn't set up yet",
            "Open Settings → Assistant and enter your PC's address, e.g. 192.168.1.50. See docs/ai-setup.md.",
        )
        val base = baseUrl(host)
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
        history.takeLast(12).forEach { messages.put(JSONObject().put("role", it.role).put("content", it.content)) }
        val body = JSONObject()
            .put("model", model)
            .put("stream", true)
            .put("messages", messages)
            .put("options", JSONObject().put("num_predict", 300).put("temperature", 0.6))
            .toString()

        val c = try {
            (URL("$base/api/chat").openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 60000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(body.toByteArray()) }
            }
        } catch (e: Exception) {
            throw explain(e, base, model)
        }
        try {
            val code = try { c.responseCode } catch (e: Exception) { throw explain(e, base, model) }
            if (code == 404) throw AssistantUnavailable(
                "The model \"$model\" isn't installed on your PC",
                "On the PC, run:  ollama pull $model",
            )
            if (code !in 200..299) {
                val err = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw AssistantUnavailable("Ollama returned an error ($code)", err.take(200).ifBlank { "Check the Ollama window on your PC." })
            }
            c.inputStream.bufferedReader().use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = try { reader.readLine() } catch (e: Exception) { throw explain(e, base, model) } ?: break
                    if (line.isBlank()) continue
                    val o = JSONObject(line)
                    o.optString("error").takeIf { it.isNotEmpty() }?.let { throw AssistantUnavailable("Ollama error", it) }
                    val chunk = o.optJSONObject("message")?.optString("content").orEmpty()
                    if (chunk.isNotEmpty()) emit(chunk)
                    if (o.optBoolean("done")) break
                }
            }
        } finally {
            c.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    private fun explain(e: Exception, base: String, model: String): AssistantUnavailable = when (e) {
        is AssistantUnavailable -> e
        is UnknownHostException -> AssistantUnavailable(
            "Can't find your PC on the network",
            "Check the PC address in Settings → Assistant ($base). Using a fixed IP is most reliable.",
        )
        is ConnectException, is NoRouteToHostException -> AssistantUnavailable(
            "Your PC isn't answering",
            "Make sure the PC is on and Ollama is running, OLLAMA_HOST=0.0.0.0 is set, and port 11434 is allowed through the firewall.",
        )
        is SocketTimeoutException -> AssistantUnavailable(
            "Your PC took too long to answer",
            "The PC may be asleep, or \"$model\" is still loading. Try again in a moment.",
        )
        is FileNotFoundException -> AssistantUnavailable("The model \"$model\" isn't installed", "On the PC, run:  ollama pull $model")
        else -> AssistantUnavailable("Couldn't reach the assistant", e.message ?: e.javaClass.simpleName)
    }
}
