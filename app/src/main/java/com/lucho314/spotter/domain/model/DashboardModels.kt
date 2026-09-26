package com.lucho314.spotter.domain.model

import com.lucho314.spotter.core.common.AppResult
import java.time.Instant

/**
 * Each section is fetched independently ([com.lucho314.spotter.domain.usecase.GetDashboardStatsUseCase]
 * runs all three in parallel), so a failure in one doesn't block the others from showing.
 * `pendingSyncCount` isn't here: the dashboard observes it live from Room
 * ([com.lucho314.spotter.domain.repository.PendingWorkoutRepository.observeCount]) instead of a
 * one-shot snapshot.
 */
data class DashboardStats(
    val sessionsThisWeek: AppResult<Int>,
    val lastSessionAt: AppResult<Instant?>,
    val latestPr: AppResult<PersonalRecord?>,
)
