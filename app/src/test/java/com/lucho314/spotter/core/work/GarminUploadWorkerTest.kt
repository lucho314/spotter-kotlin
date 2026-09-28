package com.lucho314.spotter.core.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.GarminConnectionState
import com.lucho314.spotter.domain.usecase.UploadPendingGarminActivitiesUseCase
import com.lucho314.spotter.testutil.FakeAuthRepository
import com.lucho314.spotter.testutil.FakeExerciseRepository
import com.lucho314.spotter.testutil.FakeGarminAccountRepository
import com.lucho314.spotter.testutil.FakeGarminActivityRepository
import com.lucho314.spotter.testutil.FakeGarminUploadRepository
import com.lucho314.spotter.testutil.FakePreferencesRepository
import com.lucho314.spotter.testutil.FakeTimeProvider
import com.lucho314.spotter.testutil.FakeWorkoutHistoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Mirrors [SyncWorkoutsWorkerTest]: this worker just maps [com.lucho314.spotter.domain.usecase.GarminSyncOutcome] to a WorkManager `Result`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GarminUploadWorkerTest {

    private fun useCase(authState: AuthState) = UploadPendingGarminActivitiesUseCase(
        FakeAuthRepository(authState),
        FakeGarminAccountRepository(GarminConnectionState.NotConnected),
        FakeGarminUploadRepository(),
        FakeGarminActivityRepository(),
        FakeWorkoutHistoryRepository(),
        FakeExerciseRepository(),
        FakePreferencesRepository(),
        FakeTimeProvider(),
    )

    private fun buildWorker(useCase: UploadPendingGarminActivitiesUseCase): GarminUploadWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                GarminUploadWorker(appContext, workerParameters, useCase)
        }
        return TestListenableWorkerBuilder<GarminUploadWorker>(context).setWorkerFactory(factory).build()
    }

    @Test
    fun `NoUser and NotConnected both succeed`() = runTest {
        val worker = buildWorker(useCase(AuthState.SignedOut))
        assertThat(worker.doWork()).isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `RetryLater (auth state stuck Loading) maps to a retry Result`() = runTest {
        val worker = buildWorker(useCase(AuthState.Loading))
        assertThat(worker.doWork()).isEqualTo(ListenableWorker.Result.retry())
    }
}
