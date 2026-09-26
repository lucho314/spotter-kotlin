package com.lucho314.spotter.domain.calc

import com.lucho314.spotter.domain.model.ExerciseProgressPoint
import com.lucho314.spotter.domain.model.WorkoutSet

/**
 * Aggregates raw sets (bug 14: the RN app plotted one point per *set*, from the 30 oldest rows) into
 * one point per session, keeping only the most recent [maxSessions] in ascending date order.
 */
object ExerciseProgressAggregator {

    fun aggregate(sets: List<WorkoutSet>, maxSessions: Int): List<ExerciseProgressPoint> {
        val workingSets = sets.filterNot { it.isWarmup }
        val points = workingSets.groupBy { it.sessionId }.mapNotNull { (sessionId, sessionSets) ->
            if (sessionSets.isEmpty()) return@mapNotNull null
            ExerciseProgressPoint(
                sessionId = sessionId,
                date = sessionSets.minOf { it.completedAt },
                bestE1RmKg = sessionSets.maxOf { WorkoutMath.epley1Rm(it.weightKg, it.reps) },
                topWeightKg = sessionSets.maxOf { it.weightKg },
                volumeKg = WorkoutMath.volumeKg(sessionSets, includeWarmups = false),
            )
        }
        return points.sortedBy { it.date }.takeLast(maxSessions)
    }
}
