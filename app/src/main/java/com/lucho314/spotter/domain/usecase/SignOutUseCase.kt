package com.lucho314.spotter.domain.usecase

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.Logger
import com.lucho314.spotter.core.common.resultOf
import com.lucho314.spotter.core.notifications.RestTimerAlarmScheduler
import com.lucho314.spotter.core.work.GarminUploadScheduler
import com.lucho314.spotter.core.work.SyncScheduler
import com.lucho314.spotter.domain.repository.ActiveWorkoutRepository
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.repository.GarminAccountRepository
import com.lucho314.spotter.domain.repository.LocalDataRepository
import com.lucho314.spotter.domain.repository.PendingWorkoutRepository
import com.lucho314.spotter.domain.repository.PreferencesRepository
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val TAG = "SignOutUseCase"

/** What the user would lose by signing out right now, shown as a warning before confirming. */
data class SignOutRisk(val unsyncedWorkouts: Int, val hasActiveWorkout: Boolean) {
    val isEmpty: Boolean get() = unsyncedWorkouts == 0 && !hasActiveWorkout
}

/**
 * Signs the user out, cleaning up everything that must not survive it (carry-over 1). Order
 * matters:
 * 1. Cancel the rest-timer alarm and the sync worker - both are this user's, and must stop before
 *    anything else changes under them.
 * 2. Clear every local (Room) table - **before** [AuthRepository.signOut]: if it ran after, a
 *    window would open between the sign-out and the clear where a *different* user could sign in
 *    and [com.lucho314.spotter.feature.root.RootViewModel] could re-schedule sync against the
 *    still-there previous user's outbox.
 * 3. Sign out.
 * 4. Clear per-user preferences (after sign-out, so `RootViewModel` doesn't see `needsOnboarding`
 *    flip while [AuthRepository.authState] is still `SignedIn`).
 *
 * Steps 2-4 run under [NonCancellable]: [AuthRepository.authState] flipping to signed-out
 * unmounts the authenticated `NavHost`, which would cancel the calling `viewModelScope` mid-way.
 */
class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val localDataRepository: LocalDataRepository,
    private val preferencesRepository: PreferencesRepository,
    private val pendingWorkoutRepository: PendingWorkoutRepository,
    private val activeWorkoutRepository: ActiveWorkoutRepository,
    private val restTimerAlarmScheduler: RestTimerAlarmScheduler,
    private val syncScheduler: SyncScheduler,
    private val logger: Logger,
    private val garminUploadScheduler: GarminUploadScheduler,
    private val garminAccountRepository: GarminAccountRepository,
) {
    suspend fun risk(userId: String): SignOutRisk = SignOutRisk(
        unsyncedWorkouts = pendingWorkoutRepository.observeCount(userId).first(),
        hasActiveWorkout = activeWorkoutRepository.getActive(userId) != null,
    )

    suspend operator fun invoke(): AppResult<Unit> {
        restTimerAlarmScheduler.cancel()
        syncScheduler.cancel()
        garminUploadScheduler.cancel()
        return withContext(NonCancellable) {
            when (val cleared = resultOf { localDataRepository.clearAll() }) {
                is AppResult.Failure -> {
                    // Not cleared: keep the session as-is and get both workers running again -
                    // otherwise they'd stay cancelled (step 1) until the process restarts.
                    syncScheduler.schedule()
                    garminUploadScheduler.schedule()
                    return@withContext cleared
                }
                is AppResult.Success -> Unit
            }
            // Best-effort, after Room is cleared and before the actual sign-out: the tokens'
            // ownerUserId is the real safety net (a different user signing in sees NotConnected
            // regardless), so a failure here must not block signing out.
            try {
                garminAccountRepository.disconnect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(TAG, "garmin disconnect failed: ${e::class.simpleName}")
            }
            val result = authRepository.signOut()
            try {
                preferencesRepository.clearUserScoped()
            } catch (e: IOException) {
                logger.w(TAG, "clearUserScoped failed: ${e::class.simpleName}")
            }
            result
        }
    }
}
