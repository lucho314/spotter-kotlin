package com.lucho314.spotter.domain.repository

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.PersonalRecord
import com.lucho314.spotter.domain.model.WorkoutSet

interface ProgressRepository {
    /** Ordered by `estimated_1rm` descending. */
    suspend fun getPersonalRecords(userId: String): AppResult<List<PersonalRecord>>

    /** Ordered by `updated_at` descending, limit 1. */
    suspend fun getLatestPersonalRecord(userId: String): AppResult<PersonalRecord?>
    suspend fun countPersonalRecords(userId: String): AppResult<Int>
    suspend fun getExerciseSets(userId: String, exerciseId: Int, limit: Int = 500): AppResult<List<WorkoutSet>>
}
