package dev.glasstv.launcher

import dev.glasstv.launcher.ai.OllamaClient
import dev.glasstv.launcher.data.PairedPhone
import dev.glasstv.launcher.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class MiscTest {
    @Test fun ollamaBaseUrl() {
        assertEquals("http://192.168.1.50:11434", OllamaClient.baseUrl("192.168.1.50"))
        assertEquals("http://my-pc.local:11434", OllamaClient.baseUrl(" my-pc.local/ "))
        assertEquals("http://10.0.0.2:8080", OllamaClient.baseUrl("http://10.0.0.2:8080"))
        assertEquals("https://box:11434", OllamaClient.baseUrl("https://box"))
    }

    @Test fun phonesRoundTrip() {
        val list = listOf(PairedPhone("id1", "iPhone", "abc", 5), PairedPhone("id2", "Pixel \"x\"", "def", 6))
        assertEquals(list, SettingsRepository.decodePhones(SettingsRepository.encodePhones(list)))
        assertEquals(emptyList<PairedPhone>(), SettingsRepository.decodePhones("not json"))
    }
}
