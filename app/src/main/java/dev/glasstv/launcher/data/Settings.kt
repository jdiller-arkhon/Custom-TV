package dev.glasstv.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode { LIGHT, DARK, AUTO }

data class PairedPhone(val id: String, val name: String, val tokenHash: String, val pairedAt: Long)

data class Settings(
    val greetingName: String = "",
    val theme: ThemeMode = ThemeMode.LIGHT,
    val reduceMotion: Boolean = false,
    val extraBlur: Boolean = false,
    val weatherPlace: String = "",
    val weatherLat: Double? = null,
    val weatherLon: Double? = null,
    val fahrenheit: Boolean = true,
    val ollamaHost: String = "",
    val ollamaModel: String = "llama3.2:3b",
    /** null = never customised; defaults are seeded from installed apps on first run. */
    val pinned: List<String>? = null,
    val pairedPhones: List<PairedPhone> = emptyList(),
    val homeOverride: Boolean = true,
    val onboarded: Boolean = false,
)

private val Context.store by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val name = stringPreferencesKey("greeting_name")
        val theme = stringPreferencesKey("theme")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val extraBlur = booleanPreferencesKey("extra_blur")
        val place = stringPreferencesKey("weather_place")
        val lat = doublePreferencesKey("weather_lat")
        val lon = doublePreferencesKey("weather_lon")
        val fahrenheit = booleanPreferencesKey("fahrenheit")
        val host = stringPreferencesKey("ollama_host")
        val model = stringPreferencesKey("ollama_model")
        val pinned = stringPreferencesKey("pinned")
        val phones = stringPreferencesKey("paired_phones")
        val homeOverride = booleanPreferencesKey("home_override")
        val onboarded = booleanPreferencesKey("onboarded")
    }

    val settings: Flow<Settings> = context.store.data.map { it.toSettings() }

    private fun Preferences.toSettings() = Settings(
        greetingName = this[K.name] ?: "",
        theme = this[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.LIGHT,
        reduceMotion = this[K.reduceMotion] ?: false,
        extraBlur = this[K.extraBlur] ?: false,
        weatherPlace = this[K.place] ?: "",
        weatherLat = this[K.lat],
        weatherLon = this[K.lon],
        fahrenheit = this[K.fahrenheit] ?: true,
        ollamaHost = this[K.host] ?: "",
        ollamaModel = this[K.model] ?: "llama3.2:3b",
        pinned = this[K.pinned]?.let { s -> s.split(',').filter { it.isNotBlank() } },
        pairedPhones = this[K.phones]?.let(::decodePhones) ?: emptyList(),
        homeOverride = this[K.homeOverride] ?: true,
        onboarded = this[K.onboarded] ?: false,
    )

    suspend fun setName(v: String) = context.store.edit { it[K.name] = v.trim().take(40) }
    suspend fun setTheme(v: ThemeMode) = context.store.edit { it[K.theme] = v.name }
    suspend fun setReduceMotion(v: Boolean) = context.store.edit { it[K.reduceMotion] = v }
    suspend fun setExtraBlur(v: Boolean) = context.store.edit { it[K.extraBlur] = v }
    suspend fun setFahrenheit(v: Boolean) = context.store.edit { it[K.fahrenheit] = v }
    suspend fun setHomeOverride(v: Boolean) = context.store.edit { it[K.homeOverride] = v }
    suspend fun setOnboarded() = context.store.edit { it[K.onboarded] = true }
    suspend fun setOllama(host: String, model: String) = context.store.edit {
        it[K.host] = host.trim()
        it[K.model] = model.trim().ifEmpty { "llama3.2:3b" }
    }
    suspend fun setWeatherPlace(place: String, lat: Double, lon: Double) = context.store.edit {
        it[K.place] = place; it[K.lat] = lat; it[K.lon] = lon
    }
    suspend fun setPinned(packages: List<String>) =
        context.store.edit { it[K.pinned] = packages.distinct().joinToString(",") }

    suspend fun addPhone(p: PairedPhone) = context.store.edit {
        val list = (it[K.phones]?.let(::decodePhones) ?: emptyList()).filter { x -> x.id != p.id } + p
        it[K.phones] = encodePhones(list.takeLast(MAX_PHONES))
    }
    suspend fun removePhone(id: String) = context.store.edit {
        val list = (it[K.phones]?.let(::decodePhones) ?: emptyList()).filter { x -> x.id != id }
        it[K.phones] = encodePhones(list)
    }

    companion object {
        const val MAX_PHONES = 10

        fun encodePhones(list: List<PairedPhone>): String = JSONArray().apply {
            list.forEach {
                put(JSONObject().put("id", it.id).put("name", it.name).put("hash", it.tokenHash).put("at", it.pairedAt))
            }
        }.toString()

        fun decodePhones(s: String): List<PairedPhone> = runCatching {
            val a = JSONArray(s)
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                PairedPhone(o.getString("id"), o.optString("name"), o.getString("hash"), o.optLong("at"))
            }
        }.getOrDefault(emptyList())
    }
}
