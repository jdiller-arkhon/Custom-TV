package dev.glasstv.launcher.remote

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 4-digit pairing. A code lives for [ttlMs] and allows [maxAttempts] guesses, so a
 * stranger on the Wi-Fi gets at most 5 tries out of 10,000 per code the owner chooses to show.
 */
class Pairing(
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
    private val ttlMs: Long = 120_000,
    private val maxAttempts: Int = 5,
) {
    private var code: String? = null
    private var expiresAt = 0L
    private var attempts = 0

    sealed interface Result {
        data class Paired(val token: String) : Result
        data object WrongCode : Result
        data object NoActiveCode : Result
    }

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
        val c = code
        if (c == null || clock() >= expiresAt) { code = null; return Result.NoActiveCode }
        attempts++
        val ok = MessageDigest.isEqual(c.toByteArray(), guess.trim().toByteArray())
        if (ok) {
            code = null
            return Result.Paired(newToken())
        }
        if (attempts >= maxAttempts) code = null
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
