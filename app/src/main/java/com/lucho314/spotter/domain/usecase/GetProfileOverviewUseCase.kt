package com.lucho314.spotter.domain.usecase

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.ProfileOverview
import com.lucho314.spotter.domain.model.ProfileStats
import com.lucho314.spotter.domain.repository.ProfileRepository
import com.lucho314.spotter.domain.repository.ProgressRepository
import com.lucho314.spotter.domain.repository.WorkoutHistoryRepository
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Loads the profile plus its three stat counts in parallel. The profile itself is required (a
 * failure there fails the whole thing); a failed count instead leaves [ProfileOverview.stats]
 * `null` with the first such error kept for the message, so a stats hiccup never hides the profile.
 */
class GetProfileOverviewUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val workoutHistoryRepository: WorkoutHistoryRepository,
    private val progressRepository: ProgressRepository,
) {
    suspend operator fun invoke(userId: String): AppResult<ProfileOverview> = coroutineScope {
        val profileDeferred = async { profileRepository.getProfile(userId) }
        val sessionsDeferred = async { workoutHistoryRepository.countCompleted(userId) }
        val prsDeferred = async { progressRepository.countPersonalRecords(userId) }
        val routinesDeferred = async { profileRepository.countActiveRoutines(userId) }

        val profileResult = profileDeferred.await()
        if (profileResult is AppResult.Failure) return@coroutineScope profileResult

        val profile = (profileResult as AppResult.Success).value
        val sessionsResult = sessionsDeferred.await()
        val prsResult = prsDeferred.await()
        val routinesResult = routinesDeferred.await()

        val firstError = listOf(sessionsResult, prsResult, routinesResult)
            .filterIsInstance<AppResult.Failure>()
            .firstOrNull()
            ?.error

        val stats = if (firstError == null) {
            ProfileStats(
                totalSessions = (sessionsResult as AppResult.Success).value,
                totalPrs = (prsResult as AppResult.Success).value,
                totalRoutines = (routinesResult as AppResult.Success).value,
            )
        } else {
            null
        }

        AppResult.Success(ProfileOverview(profile = profile, stats = stats, statsError = firstError))
    }
}
