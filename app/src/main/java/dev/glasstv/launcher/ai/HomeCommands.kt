package dev.glasstv.launcher.ai

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** On-device understanding for the built-in apps (lists, timers, countdowns, memory, watchlist, sleep timer). */
sealed interface HomeCommand {
    data class AddToList(val list: String, val items: List<String>) : HomeCommand
    data class ReadList(val list: String) : HomeCommand
    data class StartTimer(val label: String, val duration: Duration) : HomeCommand
    data class AddCountdown(val title: String, val date: LocalDate) : HomeCommand
    data class Remember(val fact: String) : HomeCommand
    data class Recall(val about: String?) : HomeCommand
    data class AddToWatchlist(val title: String) : HomeCommand
    data class SleepTimer(val minutes: Int) : HomeCommand
    data class OpenApp(val app: String) : HomeCommand
}

object HomeCommands {
    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    private val listName = """(grocery|groceries|shopping|to-?do|todo|chores?)"""
    private fun canonicalList(s: String) = when {
        s.startsWith("groc", true) || s.startsWith("shop", true) -> "Groceries"
        s.startsWith("chore", true) -> "Chores"
        else -> "To-do"
    }

    fun parse(input: String, now: LocalDateTime = LocalDateTime.now()): HomeCommand? {
        val t = input.trim().trimEnd('.', '!')

        rx("""^(?:please\s+)?(?:add|put)\s+(.+?)\s+(?:to|on)\s+(?:the|my|our)?\s*$listName(?:\s+list)?$""").matchEntire(t)?.let { m ->
            val items = m.groupValues[1].split(Regex("""\s*(?:,|\band\b|&)\s*""")).map { it.trim() }.filter { it.isNotEmpty() }
            return HomeCommand.AddToList(canonicalList(m.groupValues[2]), items)
        }
        rx("""^$listName(?:\s+list)?\s*:\s*(.+)$""").matchEntire(t)?.let { m ->
            val items = m.groupValues[2].split(Regex("""\s*(?:,|\band\b|&)\s*""")).map { it.trim() }.filter { it.isNotEmpty() }
            return HomeCommand.AddToList(canonicalList(m.groupValues[1]), items)
        }
        rx("""\b(?:what'?s|what is|show|read)\b.*\b(?:on\s+)?(?:the|my|our)?\s*$listName(?:\s+list)?\b""").find(t)?.let { m ->
            return HomeCommand.ReadList(canonicalList(m.groupValues[1]))
        }
        rx("""^(?:set\s+(?:a\s+)?)?sleep\s+timer\s+(?:for\s+)?(\d{1,4})\s*(?:min|mins|minutes)?$""").matchEntire(t)?.let { m ->
            return HomeCommand.SleepTimer(m.groupValues[1].toInt())
        }
        rx("""^(?:set\s+(?:a|an)?\s*|start\s+(?:a|an)?\s*)?(?:(.+?)\s+)?timer\s+(?:for\s+)?(\d{1,4})\s*(min|mins|minutes|minute|m|hours?|hrs?|h|sec|secs|seconds?|s)(?:\s+(?:for|called)\s+(.+))?$""").matchEntire(t)?.let { m ->
            val n = m.groupValues[2].toLong()
            val d = when (m.groupValues[3].lowercase().first()) { 'h' -> Duration.ofHours(n); 's' -> Duration.ofSeconds(n); else -> Duration.ofMinutes(n) }
            val label = (m.groupValues[4].ifBlank { m.groupValues[1] }).trim().removePrefix("a ").ifBlank { "Timer" }
            return HomeCommand.StartTimer(label.replaceFirstChar { it.uppercase() }, d)
        }
        rx("""^(?:add|start|make|create)?\s*(?:a\s+)?countdown\s+(?:to|until|for)\s+(.+)$""").matchEntire(t)?.let { m ->
            // Reuse the calendar parser for the date ("Paris on Dec 12", "our anniversary March 3").
            val p = EventParser.parse("add ${m.groupValues[1]}", now) ?: return null
            return HomeCommand.AddCountdown(p.title, p.start.toLocalDate())
        }
        rx("""^(?:please\s+)?remember\s+(?:that\s+)?(.+)$""").matchEntire(t)?.let { m ->
            // "remember dinner Friday at 7" is a calendar request, not a fact.
            if (EventParser.parse(t, now) != null) return null
            return HomeCommand.Remember(m.groupValues[1].trim().replaceFirstChar { it.uppercase() })
        }
        rx("""^what\s+do\s+you\s+(?:remember|know)(?:\s+about\s+(.+))?\??$""").matchEntire(t)?.let { m ->
            return HomeCommand.Recall(m.groupValues[1].trim().ifBlank { null })
        }
        rx("""^(?:add|save|put)\s+(.+?)\s+(?:to|on)\s+(?:the|my|our)?\s*watch\s*list$""").matchEntire(t)?.let { m ->
            return HomeCommand.AddToWatchlist(m.groupValues[1].trim().trim('"', '“', '”'))
        }
        rx("""^(?:open|show|start|launch)\s+(?:the\s+)?(fireplace|lists?|timers?|countdowns?|watch\s*list|memories|love notes?|guest wi-?fi|wifi|would you rather|games?|apps)$""").matchEntire(t)?.let { m ->
            return HomeCommand.OpenApp(m.groupValues[1].lowercase())
        }
        return null
    }
}
