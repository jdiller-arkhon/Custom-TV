package dev.glasstv.launcher

import dev.glasstv.launcher.data.DateNight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DateNightTest {
    @Test fun catalogCoversEveryoneAndBothKinds() {
        DateNight.Whose.entries.forEach { w -> assertTrue(DateNight.catalog.count { it.whose == w } >= 8) }
        assertTrue(DateNight.catalog.any { it.kind == DateNight.Kind.SHOW })
        assertEquals(DateNight.catalog.size, DateNight.catalog.map { it.title }.toSet().size)
    }

    @Test fun neverRepeatsRecent() {
        val rnd = Random(7)
        val recent = ArrayDeque<String>()
        repeat(200) {
            val p = DateNight.pick(recent, rnd)
            assertTrue(p.title !in recent)
            recent.addLast(p.title); if (recent.size > 6) recent.removeFirst()
        }
    }

    @Test fun weightingIsRoughly30_30_40() {
        val rnd = Random(42)
        val counts = DateNight.Whose.entries.associateWith { 0 }.toMutableMap()
        repeat(10_000) { val p = DateNight.pick(emptyList(), rnd); counts[p.whose] = counts.getValue(p.whose) + 1 }
        assertEquals(3000.0, counts.getValue(DateNight.Whose.HIS).toDouble(), 300.0)
        assertEquals(3000.0, counts.getValue(DateNight.Whose.HERS).toDouble(), 300.0)
        assertEquals(4000.0, counts.getValue(DateNight.Whose.BOTH).toDouble(), 300.0)
    }
}
