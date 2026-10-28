package dev.glasstv.launcher.remote

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 4-digit pairing. A code lives for [ttlMs] and allows [maxAttempts] guesses. Anyone on the Wi-Fi can ask
 * for a fresh code, so wrong guesses also count across codes: after [maxFailures] of them pairing is locked
 * for [lockoutMs], which keeps brute-forcing the 10,000 codes impractical.
 */
class Pairing(
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
    private val ttlMs: Long = 120_000,
    private val maxAttempts: Int = 5,
    private val maxFailures: Int = 15,
    private val lockoutMs: Long = 10 * 60_000,
) {
    private var code: String? = null
    private var expiresAt = 0L
    private var attempts = 0
    private var failures = 0
    private var lockedUntil = 0L

    sealed interface Result {
        data class Paired(val token: String) : Result
        data object WrongCode : Result
        data object NoActiveCode : Result
        data object LockedOut : Result
    }

    @Synchronized
    fun lockedOut(): Boolean = clock() < lockedUntil

    @Synchronized
    fun start(): String {
        val c = "%04d".format(random.nextInt(10_000))
        code = c; expiresAt = clock() + ttlMs; attempts = 0
        return c
    }

    @Synchronized
    fun activeCode(): String? = code?.takeIf { clock() < expiresAt }

    @Synchronized
    fun cancel() { code = null }

    @Synchronized
    fun confirm(guess: String): Result {
        if (lockedOut()) { code = null; return Result.LockedOut }
        val c = code
        if (c == null || clock() >= expiresAt) { code = null; return Result.NoActiveCode }
        attempts++
        val ok = MessageDigest.isEqual(c.toByteArray(), guess.trim().toByteArray())
        if (ok) {
            code = null; failures = 0
            return Result.Paired(newToken())
        }
        if (attempts >= maxAttempts) code = null
        if (++failures >= maxFailures) { failures = 0; code = null; lockedUntil = clock() + lockoutMs; return Result.LockedOut }
        return Result.WrongCode
    }

    private fun newToken(): String {
        val b = ByteArray(32).also(random::nextBytes)
        return b.joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun hash(token: String): String =
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
