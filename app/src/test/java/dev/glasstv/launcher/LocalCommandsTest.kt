package dev.glasstv.launcher

import dev.glasstv.launcher.ai.LocalCommand
import dev.glasstv.launcher.ai.LocalCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalCommandsTest {
    private val apps = mapOf(
        "com.netflix.ninja" to "Netflix",
        "com.google.android.youtube.tv" to "YouTube",
        "com.google.android.youtube.tvkids" to "YouTube Kids",
        "com.disney.disneyplus" to "Disney+",
        "com.amazon.amazonvideo.livingroom" to "Prime Video",
    )

    private fun open(s: String) = (LocalCommands.parse(s, apps) as? LocalCommand.OpenApp)?.packageName

    @Test fun opensByVerb() {
        assertEquals("com.netflix.ninja", open("open Netflix"))
        assertEquals("com.netflix.ninja", open("Launch netflix please".replace(" please", "")))
        assertEquals("com.netflix.ninja", open("please open the Netflix app."))
        assertEquals("com.amazon.amazonvideo.livingroom", open("go to prime video"))
    }

    @Test fun prefersExactShortLabel() {
        assertEquals("com.google.android.youtube.tv", open("launch YouTube"))
        assertEquals("com.google.android.youtube.tvkids", open("open youtube kids"))
    }

    @Test fun handlesPlusSign() {
        assertEquals("com.disney.disneyplus", open("open disney plus"))
        assertEquals("com.disney.disneyplus", open("open Disney+"))
    }

    @Test fun builtIns() {
        assertEquals(LocalCommand.OpenSettings, LocalCommands.parse("open settings", apps))
        assertEquals(LocalCommand.Ambient, LocalCommands.parse("ambient mode", apps))
        assertEquals(LocalCommand.Home, LocalCommands.parse("go home", apps))
    }

    @Test fun questionsGoToTheModel() {
        assertNull(LocalCommands.parse("What should we watch tonight?", apps))
        assertNull(LocalCommands.parse("Who's in the cast of Severance", apps))
        assertNull(LocalCommands.parse("open the pod bay doors", apps))
    }
}
