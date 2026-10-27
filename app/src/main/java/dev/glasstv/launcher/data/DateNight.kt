package dev.glasstv.launcher.data

import kotlin.random.Random

/** Date Night: a random movie or show that fits Justin (action, comedy), Anna (romance, girly), or both. */
object DateNight {
    enum class Whose(val label: String) { HIS("Justin's pick"), HERS("Anna's pick"), BOTH("Meet in the middle") }
    enum class Kind(val label: String) { MOVIE("Movie"), SHOW("Show") }

    data class Pick(val title: String, val year: Int, val kind: Kind, val genre: String, val whose: Whose, val why: String)

    private fun m(t: String, y: Int, g: String, w: Whose, why: String) = Pick(t, y, Kind.MOVIE, g, w, why)
    private fun s(t: String, y: Int, g: String, w: Whose, why: String) = Pick(t, y, Kind.SHOW, g, w, why)

    val catalog: List<Pick> = listOf(
        // Justin: action & comedy
        m("John Wick", 2014, "Action", Whose.HIS, "Slick, stylish and nonstop — and it all starts with a puppy."),
        m("Mad Max: Fury Road", 2015, "Action", Whose.HIS, "Two hours of jaw-dropping chase scenes."),
        m("Top Gun: Maverick", 2022, "Action", Whose.HIS, "Big-screen thrills with a surprising amount of heart."),
        m("Mission: Impossible – Fallout", 2018, "Action", Whose.HIS, "Insane stunts, one after another."),
        m("The Dark Knight", 2008, "Action", Whose.HIS, "A modern classic — gripping start to finish."),
        m("Gladiator", 2000, "Action", Whose.HIS, "Epic, emotional and endlessly rewatchable."),
        s("Reacher", 2022, "Action", Whose.HIS, "Bingeable, punchy episodes — easy to say 'one more'."),
        s("Jack Ryan", 2018, "Action", Whose.HIS, "Spy-thriller tension, episode after episode."),
        m("Superbad", 2007, "Comedy", Whose.HIS, "Ridiculous, quotable, guaranteed laughs."),
        m("Step Brothers", 2008, "Comedy", Whose.HIS, "Pure silliness — perfect for a lazy night."),
        m("The Hangover", 2009, "Comedy", Whose.HIS, "Chaos you'll be quoting for weeks."),
        m("21 Jump Street", 2012, "Comedy", Whose.HIS, "Action-comedy buddy cop fun."),
        m("Game Night", 2018, "Comedy", Whose.HIS, "A couple's game night gone wildly wrong — very fitting."),
        s("Brooklyn Nine-Nine", 2013, "Comedy", Whose.HIS, "Short, feel-good episodes with nonstop jokes."),
        s("The Office", 2005, "Comedy", Whose.HIS, "Cozy comfort comedy — and there's a love story too."),
        s("Ted Lasso", 2020, "Comedy", Whose.HIS, "Warm, funny and full of heart."),
        // Anna: romance & girly
        m("The Notebook", 2004, "Romance", Whose.HERS, "The ultimate swoon. Tissues recommended."),
        m("Pride & Prejudice", 2005, "Romance", Whose.HERS, "Gorgeous, slow-burn romance — that hand flex."),
        m("Crazy Rich Asians", 2018, "Romance", Whose.HERS, "Glamorous, funny and very romantic."),
        m("La La Land", 2016, "Romance", Whose.HERS, "Dreamy music, beautiful colours, all the feelings."),
        m("To All the Boys I've Loved Before", 2018, "Romance", Whose.HERS, "Sweet, cozy teen romance."),
        m("Legally Blonde", 2001, "Girly comedy", Whose.HERS, "Pink, iconic and secretly empowering."),
        m("Mamma Mia!", 2008, "Romance", Whose.HERS, "Sunshine, ABBA and a big sing-along."),
        m("The Devil Wears Prada", 2006, "Girly comedy", Whose.HERS, "Fashion, sharp wit and great outfits."),
        s("Bridgerton", 2020, "Romance", Whose.HERS, "Lavish, dramatic, impossible to stop at one."),
        s("Emily in Paris", 2020, "Romance", Whose.HERS, "Light, stylish, Parisian daydream."),
        s("Gilmore Girls", 2000, "Girly", Whose.HERS, "The coziest show ever made."),
        // Both: where action/comedy meets romance
        m("Mr. & Mrs. Smith", 2005, "Action romance", Whose.BOTH, "Married assassins — explosions and chemistry."),
        m("Knight and Day", 2010, "Action rom-com", Whose.BOTH, "Spy chases with a romantic twist."),
        m("The Lost City", 2022, "Action rom-com", Whose.BOTH, "A romance novelist on a jungle adventure — laughs and sparks."),
        m("Date Night", 2010, "Action comedy", Whose.BOTH, "A date that turns into a chase. It had to be on this list."),
        m("Crazy, Stupid, Love.", 2011, "Rom-com", Whose.BOTH, "Funny, sweet, with a great twist."),
        m("10 Things I Hate About You", 1999, "Rom-com", Whose.BOTH, "Witty, charming and a little rebellious."),
        m("The Proposal", 2009, "Rom-com", Whose.BOTH, "Laugh-out-loud funny and genuinely sweet."),
        m("Deadpool", 2016, "Action comedy", Whose.BOTH, "Wild action, nonstop jokes — and a real love story underneath."),
        m("Set It Up", 2018, "Rom-com", Whose.BOTH, "Light, clever and easy to love."),
        m("Anyone But You", 2023, "Rom-com", Whose.BOTH, "Sunny, steamy and very funny."),
        m("Ghosted", 2023, "Action romance", Whose.BOTH, "A first date turns into a spy mission."),
        m("Ticket to Paradise", 2022, "Rom-com", Whose.BOTH, "Gorgeous scenery and a funny pair."),
        s("New Girl", 2011, "Rom-com", Whose.BOTH, "Hilarious friends and a slow-burn romance."),
        s("Mr. & Mrs. Smith (series)", 2024, "Action romance", Whose.BOTH, "Spy missions as a married couple — date-night in a show."),
    )

    /** Whose turn: 30% Justin, 30% Anna, 40% both. Never repeats any of the [recent] titles. */
    fun pick(recent: Collection<String> = emptyList(), random: Random = Random.Default): Pick {
        val roll = random.nextInt(100)
        val whose = when { roll < 30 -> Whose.HIS; roll < 60 -> Whose.HERS; else -> Whose.BOTH }
        val pool = catalog.filter { it.whose == whose && it.title !in recent }
            .ifEmpty { catalog.filter { it.title !in recent } }
            .ifEmpty { catalog }
        return pool[random.nextInt(pool.size)]
    }
}
