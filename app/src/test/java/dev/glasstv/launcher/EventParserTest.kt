package dev.glasstv.launcher

import dev.glasstv.launcher.ai.EventParser
import dev.glasstv.launcher.data.CalEvent
import dev.glasstv.launcher.data.CalendarStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class EventParserTest {
    // Tuesday 27 Oct 2026, 6:00 PM
    private val now = LocalDateTime.of(2026, 10, 27, 18, 0)
    private fun p(s: String) = EventParser.parse(s, now)

    @Test fun weekdayWithTime() {
        val e = p("add dinner with Mom Friday at 7pm")!!
        assertEquals("Dinner with Mom", e.title)
        assertEquals(LocalDateTime.of(2026, 10, 30, 19, 0), e.start)
        assertEquals("🍝", e.emoji)
    }

    @Test fun remindTomorrowNoon() {
        val e = p("Remind me to call the vet tomorrow at noon")!!
        assertEquals("Call the vet", e.title)
        assertEquals(LocalDateTime.of(2026, 10, 28, 12, 0), e.start)
    }

    @Test fun monthDayAndMinutes() {
        val e = p("schedule date night on Oct 30 at 8:30")!!
        assertEquals("Date night", e.title)
        assertEquals(LocalDateTime.of(2026, 10, 30, 20, 30), e.start)
        assertEquals("💕", e.emoji)
    }

    @Test fun numericDateMorning() {
        val e = p("put dentist on 11/4 at 9am")!!
        assertEquals("Dentist", e.title)
        assertEquals(LocalDateTime.of(2026, 11, 4, 9, 0), e.start)
    }

    @Test fun allDayPastDateRollsToNextYear() {
        val e = p("add Anna's birthday on March 3 to our calendar")!!
        assertEquals("Anna's birthday", e.title)
        assertTrue(e.allDay)
        assertEquals(LocalDateTime.of(2027, 3, 3, 9, 0), e.start)
        assertEquals("🎂", e.emoji)
    }

    @Test fun timeOnlyIsTodayIfLaterElseTomorrow() {
        assertEquals(LocalDateTime.of(2026, 10, 27, 21, 0), p("remind me to take out the trash at 9pm")!!.start)
        assertEquals(LocalDateTime.of(2026, 10, 28, 7, 0), p("remind me to stretch at 7am")!!.start)
    }

    @Test fun relativeAndTonight() {
        assertEquals(LocalDateTime.of(2026, 11, 3, 15, 0), p("add gym in 7 days in the afternoon")!!.start)
        assertEquals(LocalDateTime.of(2026, 10, 27, 20, 0), p("add movie tonight")!!.start)
    }

    @Test fun notAnEvent() {
        assertNull(p("what should we watch tonight?"))
        assertNull(p("open Netflix"))
        assertNull(p("add something nice")) // no date or time
    }

    @Test fun storeRoundTrip() {
        val list = listOf(CalEvent(title = "A", start = now, allDay = false, emoji = "🎬"), CalEvent(title = "B", start = now.plusDays(1), allDay = true))
        assertEquals(list, CalendarStore.decode(CalendarStore.encode(list)))
    }
}
