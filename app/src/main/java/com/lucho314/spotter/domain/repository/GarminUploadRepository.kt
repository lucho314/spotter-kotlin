package com.lucho314.spotter.domain.repository

import com.lucho314.spotter.domain.model.GarminEnqueueResult
import com.lucho314.spotter.domain.model.GarminUploadRequest
import com.lucho314.spotter.domain.model.GarminUploadStatus
import com.lucho314.spotter.domain.model.GarminUploadTask
import kotlinx.coroutines.flow.Flow

interface GarminUploadRepository {
    /** [requeueFailed]: true on the manual path (history) - a FAILED row goes back to PENDING with attempts = 0. */
    suspend fun enqueue(request: GarminUploadRequest, requeueFailed: Boolean): GarminEnqueueResult
    suspend fun getPending(userId: String): List<GarminUploadTask>
    fun observeStatus(workoutId: String): Flow<GarminUploadStatus?>
    fun observeFailedCount(userId: String): Flow<Int>
    suspend fun markUploaded(workoutId: String, activityId: Long?, uploadId: Long?)
    suspend fun recordAttempt(workoutId: String, errorCode: String)
    suspend fun markFailed(workoutId: String, errorCode: String)
    suspend fun resetFailedToPending(userId: String): Int
    suspend fun deleteNotUploaded(userId: String)
}
