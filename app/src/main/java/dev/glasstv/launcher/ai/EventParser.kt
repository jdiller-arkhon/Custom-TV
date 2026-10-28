package dev.glasstv.launcher.ai

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.temporal.TemporalAdjusters

/**
 * Turns plain requests into calendar events, on-device, e.g.
 *   "add dinner with Mom Friday at 7pm", "remind me to call the vet tomorrow at noon",
 *   "schedule date night on Oct 30 at 8:30", "put dentist on 11/4 at 9am", "Anna's birthday on March 3".
 */
object EventParser {
    data class Parsed(val title: String, val start: LocalDateTime, val allDay: Boolean, val emoji: String)

    private val intent = Regex(
        """^\s*(?:hey\s+grace[,!]?\s*)?(?:please\s+)?(?:can you\s+|could you\s+)?""" +
            """(add|schedule|create|make|put|set up|book|plan|remind (?:me|us)(?: to| about| of)?|remember)\b\s*""" +
            """(?:an?\s+|the\s+)?(?:(?:new\s+)?(?:event|appointment|reminder|calendar event)\b\s*)?(?:for|called|named|titled|:)?\s*""",
        RegexOption.IGNORE_CASE,
    )
    private val toCalendar = Regex("""\b(?:to|on|in)\s+(?:my|our|the)\s+calendar\b""", RegexOption.IGNORE_CASE)

    private val days = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY, "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY, "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY,
    )
    private val months = mapOf(
        "jan" to 1, "january" to 1, "feb" to 2, "february" to 2, "mar" to 3, "march" to 3, "apr" to 4, "april" to 4, "may" to 5,
        "jun" to 6, "june" to 6, "jul" to 7, "july" to 7, "aug" to 8, "august" to 8, "sep" to 9, "sept" to 9, "september" to 9,
        "oct" to 10, "october" to 10, "nov" to 11, "november" to 11, "dec" to 12, "december" to 12,
    )
    private val monthAlt = months.keys.sortedByDescending { it.length }.joinToString("|")
    private val dayAlt = days.keys.sortedByDescending { it.length }.joinToString("|")

    /** Returns null if this isn't an add-event request or no date/time could be found. */
    fun parse(input: String, now: LocalDateTime = LocalDateTime.now()): Parsed? {
        val m = intent.find(input) ?: return null
        var rest = input.substring(m.range.last + 1)
        rest = toCalendar.replace(rest, " ")
        var date: LocalDate? = null
        var time: LocalTime? = null
        var defaultTime: LocalTime? = null
        var ambiguous = false // "at 8" / "at 8:30" with no am/pm
        val today = now.toLocalDate()

        fun take(re: Regex, f: (MatchResult) -> Unit) {
            val r = re.find(rest) ?: return
            f(r); rest = rest.removeRange(r.range).let { " $it " }
        }
        fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

        // ----- dates -----
        take(rx("""\b(?:on\s+)?(?:the\s+)?day after tomorrow\b""")) { date = today.plusDays(2) }
        if (date == null) take(rx("""\b(?:on\s+)?tomorrow(?:\s+night)?\b""")) { r -> date = today.plusDays(1); if (r.value.contains("night", true)) defaultTime = LocalTime.of(20, 0) }
        if (date == null) take(rx("""\btonight\b""")) { date = today; defaultTime = LocalTime.of(20, 0) }
        if (date == null) take(rx("""\b(?:on\s+)?today\b""")) { date = today }
        if (date == null) take(rx("""\bin\s+(\d{1,4}|a|one|two|three)\s+(day|days|week|weeks)\b""")) { r ->
            val n = when (r.groupValues[1].lowercase()) { "a", "one" -> 1; "two" -> 2; "three" -> 3; else -> r.groupValues[1].toInt() }
            date = if (r.groupValues[2].startsWith("week", true)) today.plusWeeks(n.toLong()) else today.plusDays(n.toLong())
        }
        if (date == null) take(rx("""\b(?:on\s+)?($monthAlt)\.?\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(\d{4}))?\b""")) { r ->
            date = monthDay(months.getValue(r.groupValues[1].lowercase()), r.groupValues[2].toInt(), r.groupValues[3].toIntOrNull(), today)
        }
        if (date == null) take(rx("""\b(?:on\s+)?(?:the\s+)?(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?($monthAlt)\b(?:,?\s+(\d{4}))?""")) { r ->
            date = monthDay(months.getValue(r.groupValues[2].lowercase()), r.groupValues[1].toInt(), r.groupValues[3].toIntOrNull(), today)
        }
        if (date == null) take(rx("""\b(?:on\s+)?(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b""")) { r ->
            val y = r.groupValues[3].toIntOrNull()?.let { if (it < 100) 2000 + it else it }
            date = monthDay(r.groupValues[1].toInt(), r.groupValues[2].toInt(), y, today)
        }
        if (date == null) take(rx("""\b(?:on\s+)?(?:(this|next|coming)\s+)?($dayAlt)\b""")) { r ->
            val dow = days.getValue(r.groupValues[2].lowercase())
            val mod = r.groupValues[1].lowercase()
            var d = if (mod == "this") today.with(TemporalAdjusters.nextOrSame(dow)) else today.with(TemporalAdjusters.next(dow))
            if (mod == "next" && d.isBefore(today.plusDays(2))) d = d.plusWeeks(1) // "next Fri" said on Thu means the one after tomorrow-ish
            date = d
        }
        if (date == null) take(rx("""\b(?:on\s+)?the\s+(\d{1,2})(?:st|nd|rd|th)\b""")) { r ->
            val dom = r.groupValues[1].toInt()
            val thisMonth = runCatching { today.withDayOfMonth(dom) }.getOrNull()
            date = if (thisMonth != null && !thisMonth.isBefore(today)) thisMonth else runCatching { today.plusMonths(1).withDayOfMonth(dom) }.getOrNull()
        }
        if (date == null) take(rx("""\b(?:this\s+)?weekend\b""")) { date = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)) }

        // ----- times -----
        take(rx("""\b(?:at\s+|@\s*)?(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)(?=\W|$)""")) { r ->
            var h = r.groupValues[1].toInt() % 12
            if (r.groupValues[3].lowercase().startsWith("p")) h += 12
            time = timeOf(h, r.groupValues[2].toIntOrNull() ?: 0)
        }
        if (time == null) take(rx("""\b(?:at\s+)?(noon|midday|midnight)\b""")) { r -> time = if (r.groupValues[1].lowercase() == "midnight") LocalTime.of(23, 59) else LocalTime.NOON }
        if (time == null) take(rx("""\bat\s+(\d{1,2}):(\d{2})\b""")) { r ->
            var h = r.groupValues[1].toInt()
            if (h in 1..7) h += 12 // "at 7:30" → evening
            if (h in 8..11) ambiguous = true
            time = timeOf(h % 24, r.groupValues[2].toInt())
        }
        if (time == null) take(rx("""\bat\s+(\d{1,2})\b(?!\s*(?:st|nd|rd|th|/))""")) { r ->
            var h = r.groupValues[1].toInt()
            if (h in 1..7) h += 12
            if (h in 8..11) ambiguous = true
            if (h in 0..23) time = LocalTime.of(h, 0)
        }
        if (time == null) take(rx("""\bin the (morning|afternoon|evening)\b|\b(morning|afternoon|evening)\b""")) { r ->
            time = when ((r.groupValues[1] + r.groupValues[2]).lowercase()) { "morning" -> LocalTime.of(9, 0); "afternoon" -> LocalTime.of(15, 0); else -> LocalTime.of(19, 0) }
        }
        if (time == null) time = defaultTime

        if (date == null && time == null) return null
        val allDay = time == null
        val d = date ?: if (LocalDateTime.of(today, time).isAfter(now)) today else today.plusDays(1)
        val title = cleanTitle(rest).ifBlank { "Reminder" }
        // 8–11 with no am/pm: evening for evening-ish plans ("date night at 8:30"), otherwise morning.
        if (ambiguous && Regex("dinner|date|movie|night|party|drinks|show|concert|game", RegexOption.IGNORE_CASE).containsMatchIn(title)) {
            time = time!!.plusHours(12)
        }
        return Parsed(title, LocalDateTime.of(d, time ?: LocalTime.of(9, 0)), allDay, emojiFor(title))
    }

    /** null for impossible clock times like "7:75" instead of throwing. */
    private fun timeOf(h: Int, m: Int): LocalTime? = if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null

    private fun monthDay(month: Int, day: Int, year: Int?, today: LocalDate): LocalDate? {
        val d = runCatching { LocalDate.of(year ?: today.year, Month.of(month), day) }.getOrNull() ?: return null
        return if (year == null && d.isBefore(today)) d.plusYears(1) else d
    }

    private fun cleanTitle(s: String): String {
        var t = s.replace(Regex("\\s+"), " ").trim()
        val junk = Regex("""^(?:to|a|an|the|for|on|at|that|about|we have|we've got|i have)\s+|\s+(?:on|at|for|in|by|this|next)$|[\s,.!?:;-]+$""", RegexOption.IGNORE_CASE)
        repeat(4) { t = junk.replace(t, "").trim() }
        return t.replaceFirstChar { it.uppercase() }
    }

    fun emojiFor(title: String): String {
        val t = title.lowercase()
        return listOf(
            listOf("birthday", "bday") to "🎂", listOf("anniversary") to "💍", listOf("date night", "date") to "💕",
            listOf("dinner", "lunch", "breakfast", "brunch") to "🍝", listOf("movie", "film", "show", "watch") to "🎬",
            listOf("doctor", "dentist", "vet", "appointment") to "🩺", listOf("gym", "workout", "yoga", "run") to "💪",
            listOf("flight", "trip", "travel", "airport", "vacation") to "✈️", listOf("party", "wedding", "celebrat") to "🎉",
            listOf("meeting", "work", "call") to "💼", listOf("bills", "pay", "rent") to "💳", listOf("grocer", "shopping") to "🛒",
        ).firstOrNull { (keys, _) -> keys.any { it in t } }?.second ?: "📌"
    }
}
