package dev.glasstv.launcher

import dev.glasstv.launcher.remote.Pairing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingTest {
    private var now = 0L
    private val p = Pairing(clock = { now }, ttlMs = 1000, maxAttempts = 3)

    @Test fun correctCodeIssuesToken() {
        val code = p.start()
        assertTrue(code.matches(Regex("\\d{4}")))
        val r = p.confirm(code)
        assertTrue(r is Pairing.Result.Paired)
        assertEquals(64, (r as Pairing.Result.Paired).token.length)
        // Single use.
        assertEquals(Pairing.Result.NoActiveCode, p.confirm(code))
    }

    @Test fun codeExpires() {
        val code = p.start()
        now = 1000
        assertEquals(Pairing.Result.NoActiveCode, p.confirm(code))
    }

    @Test fun lockoutAfterMaxAttempts() {
        val code = p.start()
        val wrong = if (code == "0000") "1111" else "0000"
        repeat(3) { assertEquals(Pairing.Result.WrongCode, p.confirm(wrong)) }
        assertNull(p.activeCode())
        assertEquals(Pairing.Result.NoActiveCode, p.confirm(code))
    }

    @Test fun tokensAreUniqueAndHashed() {
        val a = (p.confirm(p.start()) as Pairing.Result.Paired).token
        val b = (p.confirm(p.start()) as Pairing.Result.Paired).token
        assertNotEquals(a, b)
        assertEquals(64, Pairing.hash(a).length)
        assertNotEquals(a, Pairing.hash(a))
    }

    @Test fun wrongGuessesAcrossFreshCodesLockPairing() {
        val q = Pairing(clock = { now }, maxAttempts = 5, maxFailures = 15, lockoutMs = 600_000)
        repeat(3) { q.start(); repeat(5) { q.confirm("xxxx") } }
        assertTrue(q.lockedOut())
        val code = q.start()
        assertEquals(Pairing.Result.LockedOut, q.confirm(code))
        now += 600_000
        assertTrue(q.confirm(q.start()) is Pairing.Result.Paired)
    }
}
