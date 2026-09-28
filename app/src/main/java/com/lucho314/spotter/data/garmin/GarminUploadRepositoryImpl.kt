package com.lucho314.spotter.data.garmin

import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.core.database.dao.GARMIN_ENQUEUE_RESULT_ALREADY_UPLOADED
import com.lucho314.spotter.core.database.dao.GARMIN_ENQUEUE_RESULT_ENQUEUED
import com.lucho314.spotter.core.database.dao.GarminUploadDao
import com.lucho314.spotter.core.database.entity.GarminUploadEntity
import com.lucho314.spotter.data.garmin.mapper.GarminSnapshotJson
import com.lucho314.spotter.data.garmin.mapper.GarminSnapshotMapper
import com.lucho314.spotter.domain.model.GarminEnqueueResult
import com.lucho314.spotter.domain.model.GarminUploadRequest
import com.lucho314.spotter.domain.model.GarminUploadStatus
import com.lucho314.spotter.domain.model.GarminUploadTask
import com.lucho314.spotter.domain.repository.GarminUploadRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Singleton
class GarminUploadRepositoryImpl @Inject constructor(
    private val dao: GarminUploadDao,
    private val json: Json,
    private val timeProvider: TimeProvider,
) : GarminUploadRepository {

    override suspend fun enqueue(request: GarminUploadRequest, requeueFailed: Boolean): GarminEnqueueResult {
        val now = timeProvider.now().toEpochMilli()
        val payload = request.snapshot?.let { json.encodeToString(GarminSnapshotJson.serializer(), GarminSnapshotMapper.toJson(it)) }
        val entity = GarminUploadEntity(
            workoutId = request.workoutId,
            userId = request.userId,
            status = "PENDING",
            attempts = 0,
            lastError = null,
            garminActivityId = null,
            garminUploadId = null,
            payloadJson = payload,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        return when (dao.enqueue(entity, requeueFailed)) {
            GARMIN_ENQUEUE_RESULT_ENQUEUED -> GarminEnqueueResult.ENQUEUED
            GARMIN_ENQUEUE_RESULT_ALREADY_UPLOADED -> GarminEnqueueResult.ALREADY_UPLOADED
            else -> GarminEnqueueResult.ALREADY_PENDING
        }
    }

    override suspend fun getPending(userId: String): List<GarminUploadTask> = dao.getPending(userId).map { it.toTask() }

    override fun observeStatus(workoutId: String): Flow<GarminUploadStatus?> =
        dao.observeStatus(workoutId).map { status -> status?.let { GarminUploadStatus.valueOf(it) } }

    override fun observeFailedCount(userId: String): Flow<Int> = dao.observeFailedCount(userId)

    override suspend fun markUploaded(workoutId: String, activityId: Long?, uploadId: Long?) {
        dao.markUploaded(workoutId, activityId, uploadId, timeProvider.now().toEpochMilli())
    }

    override suspend fun recordAttempt(workoutId: String, errorCode: String) {
        dao.recordAttempt(workoutId, errorCode, timeProvider.now().toEpochMilli())
    }

    override suspend fun markFailed(workoutId: String, errorCode: String) {
        dao.markFailed(workoutId, errorCode, timeProvider.now().toEpochMilli())
    }

    override suspend fun resetFailedToPending(userId: String): Int = dao.resetFailedToPending(userId, timeProvider.now().toEpochMilli())

    override suspend fun deleteNotUploaded(userId: String) = dao.deleteNotUploaded(userId)

    private fun GarminUploadEntity.toTask(): GarminUploadTask {
        val payload = payloadJson ?: return GarminUploadTask(workoutId, userId, attempts, snapshot = null, snapshotCorrupt = false)
        return try {
            val parsed = json.decodeFromString(GarminSnapshotJson.serializer(), payload)
            GarminUploadTask(workoutId, userId, attempts, GarminSnapshotMapper.fromJson(parsed), snapshotCorrupt = false)
        } catch (e: SerializationException) {
            GarminUploadTask(workoutId, userId, attempts, snapshot = null, snapshotCorrupt = true)
        } catch (e: IllegalArgumentException) {
            GarminUploadTask(workoutId, userId, attempts, snapshot = null, snapshotCorrupt = true)
        }
    }
}
