package com.lucho314.spotter.core.common

/** Result of an operation at a repository/use case boundary: either [Success] or [Failure]. */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(f: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(f(value))
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(f: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) f(value)
    return this
}

inline fun <T> AppResult<T>.onFailure(f: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) f(error)
    return this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.value

/**
 * Collapses a nullable success value into [AppError.NotFound]. Handy for repository methods that
 * fetch a single row by id: the network call can succeed with a `null` row (nothing matched).
 */
fun <T> AppResult<T?>.notNullOrNotFound(): AppResult<T> = when (this) {
    is AppResult.Success -> value?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.NotFound)
    is AppResult.Failure -> this
}

/**
 * Collapses a row-count result into [Unit], with [AppError.NotFound] when 0 rows were affected.
 * Under RLS, an update/delete for a row the caller doesn't own matches 0 rows while still
 * reporting a 2xx response - this makes that case explicit instead of a silent no-op "success".
 */
fun AppResult<Int>.requirePositiveOrNotFound(): AppResult<Unit> = when (this) {
    is AppResult.Success -> if (value > 0) AppResult.Success(Unit) else AppResult.Failure(AppError.NotFound)
    is AppResult.Failure -> this
}
