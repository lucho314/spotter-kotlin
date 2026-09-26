package com.lucho314.spotter.domain.usecase

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.map
import com.lucho314.spotter.domain.calc.ExerciseProgressAggregator
import com.lucho314.spotter.domain.model.ExerciseProgressPoint
import com.lucho314.spotter.domain.repository.ProgressRepository
import javax.inject.Inject

/** Sessions kept in a progress chart, most recent first (ascending date after aggregation). */
const val PROGRESS_MAX_SESSIONS = 12

/** Aggregates one exercise's raw sets into one point per session for its progress chart. */
class GetExerciseProgressUseCase @Inject constructor(
    private val progressRepository: ProgressRepository,
) {
    suspend operator fun invoke(userId: String, exerciseId: Int): AppResult<List<ExerciseProgressPoint>> =
        progressRepository.getExerciseSets(userId, exerciseId, limit = 500)
            .map { ExerciseProgressAggregator.aggregate(it, PROGRESS_MAX_SESSIONS) }
}
