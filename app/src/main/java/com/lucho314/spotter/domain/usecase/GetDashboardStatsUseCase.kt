package com.lucho314.spotter.domain.usecase

import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.domain.calc.WeekRange
import com.lucho314.spotter.domain.model.DashboardStats
import com.lucho314.spotter.domain.repository.ProgressRepository
import com.lucho314.spotter.domain.repository.WorkoutHistoryRepository
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Loads the dashboard's three independent sections in parallel, each keeping its own
 * [com.lucho314.spotter.core.common.AppResult] - a failure in one must not block the others.
 */
class GetDashboardStatsUseCase @Inject constructor(
    private val workoutHistoryRepository: WorkoutHistoryRepository,
    private val progressRepository: ProgressRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(userId: String): DashboardStats = coroutineScope {
        val weekStart = WeekRange.currentWeekStart(timeProvider.now(), timeProvider.zone())
        val sessionsThisWeek = async { workoutHistoryRepository.getCompletedSince(userId, weekStart) }
        val lastSessionAt = async { workoutHistoryRepository.getLastCompletedAt(userId) }
        val latestPr = async { progressRepository.getLatestPersonalRecord(userId) }
        DashboardStats(
            sessionsThisWeek = sessionsThisWeek.await(),
            lastSessionAt = lastSessionAt.await(),
            latestPr = latestPr.await(),
        )
    }
}
