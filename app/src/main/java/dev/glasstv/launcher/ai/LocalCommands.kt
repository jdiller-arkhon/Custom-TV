package dev.glasstv.launcher.ai

/** Commands resolved on the TV without calling the model. */
sealed interface LocalCommand {
    data class OpenApp(val packageName: String, val label: String) : LocalCommand
    data object OpenSettings : LocalCommand
    data object Ambient : LocalCommand
    data object Home : LocalCommand
}

object LocalCommands {
    private val verb = Regex("""^\s*(?:please\s+)?(open|launch|start|play|go\s+to|switch\s+to|put\s+on)\s+(.+?)[\s.!?]*$""", RegexOption.IGNORE_CASE)
    private val filler = Regex("""^(?:the\s+)?(.*?)(?:\s+app)?$""", RegexOption.IGNORE_CASE)

    /** @param apps package name to user-visible label. */
    fun parse(input: String, apps: Map<String, String>): LocalCommand? {
        val text = input.trim().lowercase()
        when (text.trimEnd('.', '!', '?')) {
            "settings", "open settings" -> return LocalCommand.OpenSettings
            "ambient", "ambient mode", "screensaver" -> return LocalCommand.Ambient
            "home", "go home" -> return LocalCommand.Home
        }
        val m = verb.matchEntire(text) ?: return null
        val target = filler.matchEntire(m.groupValues[2].trim())?.groupValues?.get(1)?.trim().orEmpty()
        if (target.isEmpty()) return null
        if (target == "settings") return LocalCommand.OpenSettings
        return bestMatch(target, apps)?.let { (pkg, label) -> LocalCommand.OpenApp(pkg, label) }
    }

    internal fun bestMatch(target: String, apps: Map<String, String>): Pair<String, String>? {
        val t = normalize(target)
        if (t.isEmpty()) return null
        var best: Pair<String, String>? = null
        var bestScore = 0
        for ((pkg, label) in apps) {
            val l = normalize(label)
            val score = when {
                l == t -> 100
                l.startsWith(t) || t.startsWith(l) -> 80
                l.contains(t) -> 60
                l.split(' ').any { it.isNotEmpty() && t.split(' ').contains(it) } -> 40
                else -> 0
            }
            // Shorter labels win ties ("YouTube" over "YouTube Kids" for "youtube").
            if (score > bestScore || (score == bestScore && score > 0 && label.length < (best?.second?.length ?: Int.MAX_VALUE))) {
                best = pkg to label; bestScore = score
            }
        }
        return best
    }

    private fun normalize(s: String) = s.lowercase()
        .replace("+", " plus")
        .replace(Regex("[^a-z0-9 ]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}
