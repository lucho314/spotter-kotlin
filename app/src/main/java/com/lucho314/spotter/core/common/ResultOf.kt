package com.lucho314.spotter.core.common

import kotlin.coroutines.cancellation.CancellationException

/**
 * Generic analogue of `core.network.safeCall` for domain-layer suspend blocks that never touch the
 * network directly (Room-only, in-memory...) - `domain` must never import `core.network`
 * ([com.lucho314.spotter.domain.usecase.FinishWorkoutUseCase] and
 * [com.lucho314.spotter.domain.usecase.SignOutUseCase] used to, review carry-over 11). Unlike
 * `safeCall`, there's no supabase-kt/Ktor-specific exception mapping to do here, so any failure
 * simply becomes [AppError.Unknown]. [CancellationException] is always rethrown so coroutine
 * cancellation keeps working.
 */
suspend fun <T> resultOf(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    AppResult.Failure(AppError.Unknown(e))
}
