package dev.glasstv.launcher

/** Glass TV's own apps, shown in the Apps hub. [ready] = built; others show as "Soon". */
enum class BuiltInApp(val title: String, val emoji: String, val blurb: String, val ready: Boolean = true) {
    CALENDAR("Calendar", "📅", "Plans & reminders"),
    DATE_NIGHT("Date Night", "💕", "Pick a movie for us"),
    LISTS("Lists", "🛒", "Groceries, to-dos"),
    TIMERS("Timers", "⏰", "Kitchen timers"),
    COUNTDOWNS("Countdowns", "✨", "Days until…"),
    WATCHLIST("Watchlist", "🍿", "Saved & rated"),
    LOVE_NOTES("Love Notes", "💌", "From your phone"),
    MEMORY("Memory", "🧠", "Grace remembers"),
    SLEEP("Sleep Timer", "🌙", "Pause & dim"),
    FIREPLACE("Fireplace", "🔥", "Cozy crackle"),
    GAME_NIGHT("Game Night", "♟️", "Board games for two"),
    WOULD_YOU_RATHER("Would You Rather", "🎲", "A game for two"),
    GUEST_WIFI("Guest Wi-Fi", "📶", "Scan to join"),
    MEMORIES("Memories", "📸", "Photo slideshow", ready = false),
    CHORES("Chores", "🧺", "Chart & streaks", ready = false),
    MORNING("Morning Brief", "☀️", "Your day at a glance", ready = false),
    WEATHER("Weather", "🌦️", "7-day forecast", ready = false),
    TRIVIA("Movie Trivia", "🎬", "Quiz night", ready = false),
    BILLS("Bills", "💳", "Due-date reminders", ready = false),
    STRETCH("Stretch Break", "🧘", "5-minute reset", ready = false),
    RING("Doorbell", "🔔", "Ring pop-ups", ready = false),
}

object BuiltInApps {
    fun fromSpoken(s: String): BuiltInApp? = when {
        s.startsWith("fire") -> BuiltInApp.FIREPLACE
        s.startsWith("list") -> BuiltInApp.LISTS
        s.startsWith("timer") -> BuiltInApp.TIMERS
        s.startsWith("countdown") -> BuiltInApp.COUNTDOWNS
        s.startsWith("watch") -> BuiltInApp.WATCHLIST
        s.startsWith("memor") -> BuiltInApp.MEMORY
        s.startsWith("love") -> BuiltInApp.LOVE_NOTES
        s.contains("wi") -> BuiltInApp.GUEST_WIFI
        s.startsWith("game") || s.startsWith("board") || s.contains("checkers") || s.contains("connect four") || s.contains("tic") -> BuiltInApp.GAME_NIGHT
        s.startsWith("would") -> BuiltInApp.WOULD_YOU_RATHER
        else -> null
    }
}
