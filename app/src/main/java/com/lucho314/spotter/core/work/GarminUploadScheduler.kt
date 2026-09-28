package com.lucho314.spotter.core.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Unique work name for [GarminUploadWorker] - one in-flight Garmin upload run at a time, per device. */
const val GARMIN_UPLOAD_WORK_NAME = "garmin_upload"

/**
 * Enqueues [GarminUploadWorker]. Called after finishing a workout with auto-upload on
 * ([com.lucho314.spotter.domain.usecase.EnqueueGarminUploadUseCase]), from the manual "upload to
 * Garmin" action in the history, and when a failed row is reset to pending.
 */
interface GarminUploadScheduler {
    fun schedule()
    fun cancel()
}

@Singleton
class WorkManagerGarminUploadScheduler @Inject constructor(
    @ApplicationContext context: Context,
) : GarminUploadScheduler {

    private val workManager = WorkManager.getInstance(context)

    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<GarminUploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 60, TimeUnit.SECONDS)
            .build()
        // APPEND_OR_REPLACE (not KEEP): a request enqueued while one is already queued must still
        // pick up the newly-added row instead of being silently absorbed by the older request -
        // same rationale as WorkManagerSyncScheduler.
        workManager.enqueueUniqueWork(GARMIN_UPLOAD_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(GARMIN_UPLOAD_WORK_NAME)
    }
}
