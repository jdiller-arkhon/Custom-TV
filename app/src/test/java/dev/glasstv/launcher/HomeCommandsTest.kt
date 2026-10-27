package dev.glasstv.launcher

import dev.glasstv.launcher.ai.HomeCommand
import dev.glasstv.launcher.ai.HomeCommands
import dev.glasstv.launcher.data.Countdown
import dev.glasstv.launcher.data.HomeState
import dev.glasstv.launcher.data.HomeStore
import dev.glasstv.launcher.data.ListItem
import dev.glasstv.launcher.data.WatchItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class HomeCommandsTest {
    private val now = LocalDateTime.of(2026, 10, 27, 18, 0)
    private fun p(s: String) = HomeCommands.parse(s, now)

    @Test fun lists() {
        assertEquals(HomeCommand.AddToList("Groceries", listOf("milk", "eggs", "strawberries")), p("add milk, eggs and strawberries to the grocery list"))
        assertEquals(HomeCommand.AddToList("To-do", listOf("call the bank")), p("put call the bank on my to-do list"))
        assertEquals(HomeCommand.ReadList("Groceries"), p("what's on the grocery list?"))
        assertEquals(HomeCommand.AddToList("Groceries", listOf("milk", "eggs")), p("Groceries: milk, eggs"))
    }

    @Test fun timers() {
        assertEquals(HomeCommand.StartTimer("Pasta", Duration.ofMinutes(10)), p("pasta timer 10 minutes"))
        assertEquals(HomeCommand.StartTimer("Cookies", Duration.ofMinutes(12)), p("set a timer for 12 min for cookies"))
        assertEquals(HomeCommand.StartTimer("Timer", Duration.ofHours(1)), p("set a timer for 1 hour"))
        assertEquals(HomeCommand.SleepTimer(30), p("sleep timer 30 minutes"))
    }

    @Test fun countdownMemoryWatchlist() {
        assertEquals(HomeCommand.AddCountdown("Paris", LocalDate.of(2026, 12, 12)), p("countdown to Paris on Dec 12"))
        assertEquals(HomeCommand.Remember("Anna's favorite flowers are peonies"), p("remember that Anna's favorite flowers are peonies"))
        assertEquals(HomeCommand.Recall("anna"), p("what do you remember about anna"))
        assertEquals(HomeCommand.AddToWatchlist("The Lost City"), p("add The Lost City to our watchlist"))
        assertEquals(HomeCommand.OpenApp("fireplace"), p("open the fireplace"))
    }

    @Test fun leavesOtherThingsAlone() {
        assertNull(p("remember dinner Friday at 7pm")) // calendar's job
        assertNull(p("open Netflix"))
        assertNull(p("what should we watch tonight?"))
    }

    @Test fun storeRoundTrip() {
        val s = HomeState(
            items = listOf(ListItem(list = "Groceries", text = "milk", done = true)),
            countdowns = listOf(Countdown(title = "Paris", date = LocalDate.of(2026, 12, 12), emoji = "✈️")),
            watchlist = listOf(WatchItem(title = "Date Night", year = 2010, kind = "Movie", watched = true, his = 4, hers = 5)),
            wifiSsid = "Home", wifiPassword = "p@ss",
        )
        assertEquals(s, HomeStore.decode(HomeStore.encode(s)))
    }
}
