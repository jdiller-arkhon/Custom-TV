package dev.glasstv.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

data class Weather(val temp: Int, val code: Int, val isDay: Boolean) {
    val description: String get() = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67, in 80..82 -> "Rain"
        in 71..77, 85, 86 -> "Snow"
        in 95..99 -> "Storms"
        else -> ""
    }
    val glyph: String get() = when (code) {
        0 -> if (isDay) "☀" else "☾"
        1, 2 -> "⛅"
        3, 45, 48 -> "☁"
        in 51..67, in 80..82 -> "☂"
        in 71..77, 85, 86 -> "❄"
        in 95..99 -> "⚡"
        else -> "·"
    }
}

data class Place(val label: String, val lat: Double, val lon: Double)

/** Open-Meteo: free, no API key. */
object WeatherClient {
    suspend fun geocode(query: String): Place? = withContext(Dispatchers.IO) {
        runCatching {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val o = JSONObject(httpGet("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&format=json"))
            val r = o.optJSONArray("results")?.optJSONObject(0) ?: return@runCatching null
            val label = listOfNotNull(r.optString("name").ifBlank { null }, r.optString("admin1").ifBlank { null })
                .joinToString(", ")
            Place(label, r.getDouble("latitude"), r.getDouble("longitude"))
        }.getOrNull()
    }

    suspend fun current(lat: Double, lon: Double, fahrenheit: Boolean): Weather? = withContext(Dispatchers.IO) {
        runCatching {
            val unit = if (fahrenheit) "fahrenheit" else "celsius"
            val o = JSONObject(
                httpGet("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day&temperature_unit=$unit")
            ).getJSONObject("current")
            Weather(Math.round(o.getDouble("temperature_2m")).toInt(), o.getInt("weather_code"), o.optInt("is_day", 1) == 1)
        }.getOrNull()
    }
}
