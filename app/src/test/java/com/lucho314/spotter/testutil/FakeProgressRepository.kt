package com.lucho314.spotter.testutil

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.PersonalRecord
import com.lucho314.spotter.domain.model.WorkoutSet
import com.lucho314.spotter.domain.repository.ProgressRepository

class FakeProgressRepository : ProgressRepository {

    var personalRecordsResult: AppResult<List<PersonalRecord>> = AppResult.Success(emptyList())
    var latestPersonalRecordResult: AppResult<PersonalRecord?> = AppResult.Success(null)
    var countPersonalRecordsResult: AppResult<Int> = AppResult.Success(0)
    var exerciseSetsResult: AppResult<List<WorkoutSet>> = AppResult.Success(emptyList())

    val getExerciseSetsCalls = mutableListOf<Triple<String, Int, Int>>()

    override suspend fun getPersonalRecords(userId: String): AppResult<List<PersonalRecord>> = personalRecordsResult

    override suspend fun getLatestPersonalRecord(userId: String): AppResult<PersonalRecord?> = latestPersonalRecordResult

    override suspend fun countPersonalRecords(userId: String): AppResult<Int> = countPersonalRecordsResult

    override suspend fun getExerciseSets(userId: String, exerciseId: Int, limit: Int): AppResult<List<WorkoutSet>> {
        getExerciseSetsCalls += Triple(userId, exerciseId, limit)
        return exerciseSetsResult
    }
}
