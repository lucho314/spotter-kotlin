package com.lucho314.spotter.core.work

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

/** Unique work name for [SyncWorkoutsWorker] - one in-flight sync at a time, per device. */
const val SYNC_WORKOUTS_WORK_NAME = "sync_workouts"

/**
 * Enqueues [SyncWorkoutsWorker]. Called on finishing a workout, on sign-in
 * ([com.lucho314.spotter.feature.root.RootViewModel]) and when a failed row is reset to pending
 * (RN bug #3, section 7: the RN app only synced on the offline-to-online *transition*, so a
 * pending queue already there when the app opened online never got flushed).
 */
interface SyncScheduler {
    fun schedule()
    fun cancel()
}

@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext context: android.content.Context,
) : SyncScheduler {

    private val workManager = WorkManager.getInstance(context)

    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<SyncWorkoutsWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // APPEND_OR_REPLACE (not KEEP): a sync requested while one is already queued (e.g. finishing
        // a second workout before the first sync ran) must still pick up the newly-added outbox row,
        // not be silently absorbed by the older request (RN bug #2, section 7).
        workManager.enqueueUniqueWork(SYNC_WORKOUTS_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(SYNC_WORKOUTS_WORK_NAME)
    }
}
