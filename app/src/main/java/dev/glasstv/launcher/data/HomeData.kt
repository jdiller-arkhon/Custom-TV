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
import java.time.LocalDate
import java.util.UUID

data class ListItem(val id: String = UUID.randomUUID().toString(), val list: String, val text: String, val done: Boolean = false)
data class Countdown(val id: String = UUID.randomUUID().toString(), val title: String, val date: LocalDate, val emoji: String)
data class Memory(val id: String = UUID.randomUUID().toString(), val fact: String, val at: Long = System.currentTimeMillis())
data class WatchItem(
    val id: String = UUID.randomUUID().toString(), val title: String, val year: Int = 0, val kind: String = "",
    val watched: Boolean = false, val his: Int = 0, val hers: Int = 0,
)
data class LoveNote(val id: String = UUID.randomUUID().toString(), val text: String, val from: String, val at: Long = System.currentTimeMillis())

/** Everything the built-in apps keep, in one small JSON file (home.json) with atomic writes. */
data class HomeState(
    val items: List<ListItem> = emptyList(),
    val countdowns: List<Countdown> = emptyList(),
    val memories: List<Memory> = emptyList(),
    val watchlist: List<WatchItem> = emptyList(),
    val notes: List<LoveNote> = emptyList(),
    val wifiSsid: String = "",
    val wifiPassword: String = "",
)

class HomeStore(context: Context, private val scope: CoroutineScope) {
    private val file = File(context.filesDir, "home.json")
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state

    init { scope.launch(Dispatchers.IO) { runCatching { _state.value = decode(file.readText()) } } }

    fun update(f: (HomeState) -> HomeState) {
        val next = f(_state.value)
        _state.value = next
        scope.launch(Dispatchers.IO) { save() }
    }

    // Convenience operations used by Grace and the app screens.
    fun addItem(list: String, text: String) = update { it.copy(items = it.items + ListItem(list = list, text = text)) }
    fun toggleItem(id: String) = update { s -> s.copy(items = s.items.map { if (it.id == id) it.copy(done = !it.done) else it }) }
    fun clearDone(list: String) = update { s -> s.copy(items = s.items.filterNot { it.list == list && it.done }) }
    fun addCountdown(c: Countdown) = update { s -> s.copy(countdowns = (s.countdowns + c).sortedBy { it.date }) }
    fun removeCountdown(id: String) = update { s -> s.copy(countdowns = s.countdowns.filterNot { it.id == id }) }
    fun remember(fact: String) = update { s -> s.copy(memories = s.memories + Memory(fact = fact)) }
    fun forget(id: String) = update { s -> s.copy(memories = s.memories.filterNot { it.id == id }) }
    fun addWatch(w: WatchItem) = update { s -> if (s.watchlist.any { it.title.equals(w.title, true) }) s else s.copy(watchlist = s.watchlist + w) }
    fun updateWatch(id: String, f: (WatchItem) -> WatchItem) = update { s -> s.copy(watchlist = s.watchlist.map { if (it.id == id) f(it) else it }) }
    fun removeWatch(id: String) = update { s -> s.copy(watchlist = s.watchlist.filterNot { it.id == id }) }
    fun addNote(n: LoveNote) = update { s -> s.copy(notes = (listOf(n) + s.notes).take(50)) }
    fun setWifi(ssid: String, pass: String) = update { it.copy(wifiSsid = ssid.trim(), wifiPassword = pass) }

    /** Writes the current state (read under the lock), so the last save to run always persists the newest data. */
    @Synchronized private fun save() {
        val tmp = File(file.parentFile, "home.json.tmp")
        tmp.writeText(encode(_state.value)); tmp.renameTo(file)
    }

    companion object {
        fun encode(s: HomeState): String = JSONObject()
            .put("items", JSONArray().apply { s.items.forEach { put(JSONObject().put("id", it.id).put("list", it.list).put("text", it.text).put("done", it.done)) } })
            .put("countdowns", JSONArray().apply { s.countdowns.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("date", it.date.toString()).put("emoji", it.emoji)) } })
            .put("memories", JSONArray().apply { s.memories.forEach { put(JSONObject().put("id", it.id).put("fact", it.fact).put("at", it.at)) } })
            .put("watchlist", JSONArray().apply { s.watchlist.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("year", it.year).put("kind", it.kind).put("watched", it.watched).put("his", it.his).put("hers", it.hers)) } })
            .put("notes", JSONArray().apply { s.notes.forEach { put(JSONObject().put("id", it.id).put("text", it.text).put("from", it.from).put("at", it.at)) } })
            .put("wifiSsid", s.wifiSsid).put("wifiPassword", s.wifiPassword)
            .toString()

        fun decode(str: String): HomeState {
            val o = JSONObject(str)
            fun arr(k: String) = o.optJSONArray(k) ?: JSONArray()
            fun <T> map(a: JSONArray, f: (JSONObject) -> T) = List(a.length()) { f(a.getJSONObject(it)) }
            return HomeState(
                items = map(arr("items")) { ListItem(it.getString("id"), it.getString("list"), it.getString("text"), it.optBoolean("done")) },
                countdowns = map(arr("countdowns")) { Countdown(it.getString("id"), it.getString("title"), LocalDate.parse(it.getString("date")), it.optString("emoji", "✨")) },
                memories = map(arr("memories")) { Memory(it.getString("id"), it.getString("fact"), it.optLong("at")) },
                watchlist = map(arr("watchlist")) { WatchItem(it.getString("id"), it.getString("title"), it.optInt("year"), it.optString("kind"), it.optBoolean("watched"), it.optInt("his"), it.optInt("hers")) },
                notes = map(arr("notes")) { LoveNote(it.getString("id"), it.getString("text"), it.optString("from", "Justin"), it.optLong("at")) },
                wifiSsid = o.optString("wifiSsid"), wifiPassword = o.optString("wifiPassword"),
            )
        }
    }
}
