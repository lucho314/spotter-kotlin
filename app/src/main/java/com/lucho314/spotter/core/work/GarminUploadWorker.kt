package com.lucho314.spotter.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lucho314.spotter.domain.usecase.GarminSyncOutcome
import com.lucho314.spotter.domain.usecase.UploadPendingGarminActivitiesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Runs [UploadPendingGarminActivitiesUseCase] under WorkManager's `NetworkType.CONNECTED` constraint (see [GarminUploadScheduler]). A Garmin failure here never touches Spotter's own outbox or [SyncWorkoutsWorker]. */
@HiltWorker
class GarminUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val uploadPendingGarminActivities: UploadPendingGarminActivitiesUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (uploadPendingGarminActivities()) {
        GarminSyncOutcome.RetryLater -> Result.retry()
        else -> Result.success()
    }
}
