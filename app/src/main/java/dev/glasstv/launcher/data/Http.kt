package dev.glasstv.launcher.data

import java.net.HttpURLConnection
import java.net.URL

internal fun httpGet(url: String, timeoutMs: Int = 8000): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = timeoutMs
    c.readTimeout = timeoutMs
    c.setRequestProperty("Accept", "application/json")
    try {
        if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
        return c.inputStream.bufferedReader().use { it.readText() }
    } finally {
        c.disconnect()
    }
}
