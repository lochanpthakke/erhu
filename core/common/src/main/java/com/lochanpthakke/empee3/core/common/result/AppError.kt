package com.lochanpthakke.empee3.core.common.result

/**
 * Typed error hierarchy for AppResult failures.
 * Every concrete error carries a human-readable message and an optional cause
 * for logging/debugging — never swallow the original Throwable.
 */
sealed class AppError(
    open val message: String,
    open val cause: Throwable? = null,
) {
    data class IoError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError(message, cause)

    data class DatabaseError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError(message, cause)

    data class NetworkError(
        override val message: String,
        val code: Int? = null,
        override val cause: Throwable? = null,
    ) : AppError(message, cause)

    data class ParseError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError(message, cause)

    data class TagWriteError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError(message, cause)

    data class ValidationError(
        override val message: String,
    ) : AppError(message)

    data class Unknown(
        override val message: String = "An unknown error occurred",
        override val cause: Throwable? = null,
    ) : AppError(message, cause)
}
