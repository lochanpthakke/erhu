package com.lochanpthakke.empee3.core.common.result

sealed interface AppResult<out T> {
    data class Success<T>(
        val data: T,
    ) : AppResult<T>

    data class Error(
        val error: AppError,
    ) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.fold(
    onSuccess: (T) -> R,
    onError: (AppError) -> R,
): R =
    when (this) {
        is AppResult.Success -> onSuccess(data)
        is AppResult.Error -> onError(error)
    }

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> =
    when (this) {
        is AppResult.Success -> AppResult.Success(transform(data))
        is AppResult.Error -> this
    }

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(data)
    return this
}

inline fun <T> AppResult<T>.onError(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Error) action(error)
    return this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data

fun <T> AppResult<T>.getOrDefault(default: T): T = (this as? AppResult.Success)?.data ?: default

/**
 * Runs [block], catching any Throwable and mapping it into an AppResult.Error
 * via [mapError]. Use this at the boundary of every suspend one-shot operation
 * (scans, writes, network calls, tag write-back, backup/restore) instead of
 * hand-rolling try/catch at each call site.
 */
@Suppress("TooGenericExceptionCaught") // Intentional: this is the catch-all boundary for one-shot ops
suspend fun <T> appResultOf(
    mapError: (Throwable) -> AppError = { AppError.Unknown(cause = it) },
    block: suspend () -> T,
): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (t: Throwable) {
        AppResult.Error(mapError(t))
    }
