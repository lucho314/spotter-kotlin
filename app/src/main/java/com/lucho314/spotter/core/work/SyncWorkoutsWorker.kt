package com.lucho314.spotter.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lucho314.spotter.domain.usecase.SyncOutcome
import com.lucho314.spotter.domain.usecase.SyncPendingWorkoutsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Runs [SyncPendingWorkoutsUseCase] under WorkManager's `NetworkType.CONNECTED` constraint (see [SyncScheduler]). */
@HiltWorker
class SyncWorkoutsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncPendingWorkoutsUseCase: SyncPendingWorkoutsUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (syncPendingWorkoutsUseCase()) {
        SyncOutcome.Done, SyncOutcome.NoUser -> Result.success()
        SyncOutcome.RetryLater -> Result.retry()
    }
}
