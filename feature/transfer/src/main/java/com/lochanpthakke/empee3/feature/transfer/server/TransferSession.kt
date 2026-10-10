package com.lochanpthakke.empee3.feature.transfer.server

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

/** Outcome of entering the pairing code in the browser. */
sealed interface PairResult {
    data class Paired(
        val token: String,
    ) : PairResult

    data object WrongCode : PairResult

    /** Too many wrong codes: pairing by code is off for the rest of this session (the QR code still works). */
    data object Locked : PairResult

    data object Closed : PairResult
}

/**
 * One transfer session, from Start to Stop. It holds two credentials, both random from
 * [SecureRandom]: a 256-bit [token] (in the QR code, never typed) and a 6-digit [pairingCode]
 * for typing the address by hand, which trades for the token and locks after a few wrong tries.
 * Nothing is valid after [close] or after [expiresAtMs]; a new session gets new credentials.
 * Credentials are never logged ([toString] hides them).
 */
class TransferSession private constructor(
    val token: String,
    val pairingCode: String,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    private val maxPairAttempts: Int,
) {
    @Volatile
    private var closed = false
    private val failedPairs = AtomicInteger()

    fun isOpen(nowMs: Long): Boolean = !closed && nowMs < expiresAtMs

    /** True only for the exact token of an open session (constant-time comparison). */
    fun isAuthorized(
        candidate: String?,
        nowMs: Long,
    ): Boolean = isOpen(nowMs) && candidate != null && constantTimeEquals(candidate, token)

    fun pair(
        code: String?,
        nowMs: Long,
    ): PairResult =
        when {
            !isOpen(nowMs) -> PairResult.Closed
            failedPairs.get() >= maxPairAttempts -> PairResult.Locked
            code != null && constantTimeEquals(code.trim(), pairingCode) -> PairResult.Paired(token)
            failedPairs.incrementAndGet() >= maxPairAttempts -> PairResult.Locked
            else -> PairResult.WrongCode
        }

    fun close() {
        closed = true
    }

    override fun toString(): String = "TransferSession(created=$createdAtMs, expires=$expiresAtMs, closed=$closed)"

    companion object {
        private const val TOKEN_BYTES = 32
        private const val CODE_RANGE = 1_000_000
        private const val CODE_LENGTH = 6
        const val DEFAULT_MAX_PAIR_ATTEMPTS = 5

        /** Hard limit on a session's life, whatever happens: 3 hours. */
        const val DEFAULT_LIFETIME_MS = 3L * 60 * 60 * 1000

        fun create(
            nowMs: Long,
            random: SecureRandom = SecureRandom(),
            lifetimeMs: Long = DEFAULT_LIFETIME_MS,
            maxPairAttempts: Int = DEFAULT_MAX_PAIR_ATTEMPTS,
        ): TransferSession {
            val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
            val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            val code = random.nextInt(CODE_RANGE).toString().padStart(CODE_LENGTH, '0')
            return TransferSession(token, code, nowMs, nowMs + lifetimeMs, maxPairAttempts)
        }

        private fun constantTimeEquals(
            a: String,
            b: String,
        ): Boolean = MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    }
}
