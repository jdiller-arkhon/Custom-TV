package dev.glasstv.launcher.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class CalEvent(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val start: LocalDateTime,
    val allDay: Boolean = false,
    val emoji: String = "📌",
    val createdBy: String = "Grace",
) {
    fun whenText(now: LocalDateTime = LocalDateTime.now()): String {
        val d = start.toLocalDate(); val today = now.toLocalDate()
        val day = when (d) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> d.format(DateTimeFormatter.ofPattern(if (d.year == today.year) "EEE, MMM d" else "EEE, MMM d, yyyy"))
        }
        return if (allDay) day else "$day · ${start.format(DateTimeFormatter.ofPattern("h:mm a"))}"
    }
}

/** On-device calendar (events.json in app storage). Works without any account or network. */
class CalendarStore(context: Context, private val scope: CoroutineScope) {
    private val file = File(context.filesDir, "events.json")
    private val _events = MutableStateFlow<List<CalEvent>>(emptyList())
    val events: StateFlow<List<CalEvent>> = _events

    init { scope.launch(Dispatchers.IO) { _events.value = load() } }

    fun add(e: CalEvent) = update { (it + e).sortedBy { x -> x.start } }
    fun remove(id: String) = update { it.filterNot { x -> x.id == id } }

    fun upcoming(now: LocalDateTime = LocalDateTime.now(), limit: Int = 50): List<CalEvent> =
        _events.value.filter { if (it.allDay) !it.start.toLocalDate().isBefore(now.toLocalDate()) else it.start.isAfter(now.minusMinutes(30)) }
            .take(limit)

    private fun update(f: (List<CalEvent>) -> List<CalEvent>) {
        val next = f(_events.value)
        _events.value = next
        scope.launch(Dispatchers.IO) { save(next) }
    }

    private fun load(): List<CalEvent> = runCatching { decode(file.readText()) }.getOrDefault(emptyList())

    @Synchronized private fun save(list: List<CalEvent>) {
        // Write-then-rename so a power cut never leaves a half-written file.
        val tmp = File(file.parentFile, "events.json.tmp")
        tmp.writeText(encode(list)); tmp.renameTo(file)
    }

    companion object {
        fun encode(list: List<CalEvent>): String = JSONArray().apply {
            list.forEach {
                put(JSONObject().put("id", it.id).put("title", it.title).put("start", it.start.toString())
                    .put("allDay", it.allDay).put("emoji", it.emoji).put("by", it.createdBy))
            }
        }.toString()

        fun decode(s: String): List<CalEvent> {
            val a = JSONArray(s)
            return List(a.length()) { i ->
                val o = a.getJSONObject(i)
                CalEvent(o.getString("id"), o.getString("title"), LocalDateTime.parse(o.getString("start")),
                    o.optBoolean("allDay"), o.optString("emoji", "📌"), o.optString("by", "Grace"))
            }.sortedBy { it.start }
        }
    }
}
