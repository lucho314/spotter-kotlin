package com.lucho314.spotter.testutil

import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.PendingStatus
import com.lucho314.spotter.domain.model.PendingWorkout
import com.lucho314.spotter.domain.repository.PendingWorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow

class FakePendingWorkoutRepository : PendingWorkoutRepository {

    private val workouts = mutableMapOf<String, PendingWorkout>()
    private val countFlow = MutableStateFlow(0)
    private val failedFlow = MutableStateFlow<List<PendingWorkout>>(emptyList())

    /** Maps a workout id to the error [upload] should fail with; absent = succeeds. */
    val uploadErrors = mutableMapOf<String, AppError>()
    val uploadedIds = mutableListOf<String>()
    val deletedIds = mutableListOf<String>()
    val markedFailedIds = mutableListOf<String>()
    val attemptedIds = mutableListOf<String>()

    fun seed(workout: PendingWorkout) {
        workouts[workout.id] = workout
        recompute()
    }

    override fun observeCount(userId: String) = countFlow

    override fun observeFailed(userId: String) = failedFlow

    override suspend fun getPending(userId: String): List<PendingWorkout> =
        workouts.values.filter { it.userId == userId && it.status == PendingStatus.PENDING }

    override suspend fun upload(workout: PendingWorkout): AppResult<Unit> {
        uploadedIds += workout.id
        val error = uploadErrors[workout.id]
        return if (error != null) AppResult.Failure(error) else AppResult.Success(Unit)
    }

    override suspend fun delete(id: String) {
        deletedIds += id
        workouts.remove(id)
        recompute()
    }

    override suspend fun markFailed(id: String, error: String) {
        markedFailedIds += id
        workouts[id]?.let { workouts[id] = it.copy(status = PendingStatus.FAILED, lastError = error) }
        recompute()
    }

    override suspend fun resetToPending(id: String) {
        workouts[id]?.let { workouts[id] = it.copy(status = PendingStatus.PENDING, lastError = null) }
        recompute()
    }

    override suspend fun recordAttempt(id: String, error: String) {
        attemptedIds += id
        workouts[id]?.let { workouts[id] = it.copy(lastError = error) }
        recompute()
    }

    private fun recompute() {
        countFlow.value = workouts.values.count { it.status == PendingStatus.PENDING || it.status == PendingStatus.FAILED }
        failedFlow.value = workouts.values.filter { it.status == PendingStatus.FAILED }
    }
}
