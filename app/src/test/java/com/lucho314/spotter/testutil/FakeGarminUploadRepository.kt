package com.lucho314.spotter.testutil

import com.lucho314.spotter.domain.model.GarminActivitySnapshot
import com.lucho314.spotter.domain.model.GarminEnqueueResult
import com.lucho314.spotter.domain.model.GarminUploadRequest
import com.lucho314.spotter.domain.model.GarminUploadStatus
import com.lucho314.spotter.domain.model.GarminUploadTask
import com.lucho314.spotter.domain.repository.GarminUploadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [GarminUploadRepository] test double, mirroring `GarminUploadDao.enqueue`'s semantics. */
class FakeGarminUploadRepository : GarminUploadRepository {

    data class Row(
        var userId: String,
        var status: GarminUploadStatus,
        var attempts: Int,
        var snapshot: GarminActivitySnapshot?,
        var snapshotCorrupt: Boolean,
        var lastError: String? = null,
        var activityId: Long? = null,
        var uploadId: Long? = null,
    )

    val rows = LinkedHashMap<String, Row>()

    /** Thrown by [enqueue] if set. */
    var enqueueError: Throwable? = null

    /** Thrown by [deleteNotUploaded] if set. */
    var deleteNotUploadedError: Throwable? = null

    private val statusFlows = mutableMapOf<String, MutableStateFlow<GarminUploadStatus?>>()
    private val failedCountFlows = mutableMapOf<String, MutableStateFlow<Int>>()

    fun seed(
        workoutId: String,
        userId: String,
        status: GarminUploadStatus = GarminUploadStatus.PENDING,
        attempts: Int = 0,
        snapshot: GarminActivitySnapshot? = null,
        snapshotCorrupt: Boolean = false,
    ) {
        rows[workoutId] = Row(userId, status, attempts, snapshot, snapshotCorrupt)
        updateFlows(workoutId, userId)
    }

    override suspend fun enqueue(request: GarminUploadRequest, requeueFailed: Boolean): GarminEnqueueResult {
        enqueueError?.let { throw it }
        val existing = rows[request.workoutId]
        val result = when {
            existing == null -> {
                rows[request.workoutId] = Row(request.userId, GarminUploadStatus.PENDING, 0, request.snapshot, false)
                GarminEnqueueResult.ENQUEUED
            }
            existing.status == GarminUploadStatus.UPLOADED -> GarminEnqueueResult.ALREADY_UPLOADED
            existing.status == GarminUploadStatus.FAILED && requeueFailed -> {
                existing.status = GarminUploadStatus.PENDING
                existing.attempts = 0
                existing.lastError = null
                if (request.snapshot != null) existing.snapshot = request.snapshot
                GarminEnqueueResult.ENQUEUED
            }
            else -> {
                if (existing.snapshot == null && request.snapshot != null) existing.snapshot = request.snapshot
                GarminEnqueueResult.ALREADY_PENDING
            }
        }
        updateFlows(request.workoutId, request.userId)
        return result
    }

    override suspend fun getPending(userId: String): List<GarminUploadTask> =
        rows.filterValues { it.userId == userId && it.status == GarminUploadStatus.PENDING }
            .map { (id, row) -> GarminUploadTask(id, row.userId, row.attempts, row.snapshot, row.snapshotCorrupt) }

    override fun observeStatus(workoutId: String): Flow<GarminUploadStatus?> =
        statusFlows.getOrPut(workoutId) { MutableStateFlow(rows[workoutId]?.status) }

    override fun observeFailedCount(userId: String): Flow<Int> =
        failedCountFlows.getOrPut(userId) { MutableStateFlow(countFailed(userId)) }

    override suspend fun markUploaded(workoutId: String, activityId: Long?, uploadId: Long?) {
        val row = rows[workoutId] ?: return
        row.status = GarminUploadStatus.UPLOADED
        row.activityId = activityId
        row.uploadId = uploadId
        row.snapshot = null
        row.lastError = null
        updateFlows(workoutId, row.userId)
    }

    override suspend fun recordAttempt(workoutId: String, errorCode: String) {
        val row = rows[workoutId] ?: return
        row.attempts++
        row.lastError = errorCode
        updateFlows(workoutId, row.userId)
    }

    override suspend fun markFailed(workoutId: String, errorCode: String) {
        val row = rows[workoutId] ?: return
        row.status = GarminUploadStatus.FAILED
        row.attempts++
        row.lastError = errorCode
        updateFlows(workoutId, row.userId)
    }

    override suspend fun resetFailedToPending(userId: String): Int {
        var count = 0
        rows.forEach { (id, row) ->
            if (row.userId == userId && row.status == GarminUploadStatus.FAILED) {
                row.status = GarminUploadStatus.PENDING
                row.attempts = 0
                row.lastError = null
                count++
                updateFlows(id, userId)
            }
        }
        return count
    }

    override suspend fun deleteNotUploaded(userId: String) {
        deleteNotUploadedError?.let { throw it }
        rows.entries.removeAll { it.value.userId == userId && it.value.status != GarminUploadStatus.UPLOADED }
        failedCountFlows[userId]?.value = countFailed(userId)
    }

    private fun updateFlows(workoutId: String, userId: String) {
        statusFlows[workoutId]?.value = rows[workoutId]?.status
        failedCountFlows[userId]?.value = countFailed(userId)
    }

    private fun countFailed(userId: String) = rows.values.count { it.userId == userId && it.status == GarminUploadStatus.FAILED }
}
