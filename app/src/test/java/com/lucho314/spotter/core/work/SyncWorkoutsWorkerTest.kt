package com.lucho314.spotter.core.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import com.lucho314.spotter.domain.model.PendingStatus
import com.lucho314.spotter.domain.model.PendingWorkout
import com.lucho314.spotter.domain.usecase.SyncPendingWorkoutsUseCase
import com.lucho314.spotter.testutil.FakeAuthRepository
import com.lucho314.spotter.testutil.FakePendingWorkoutRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val USER = AuthUser(id = "user-1", email = "a@b.com", displayName = "Ada", avatarUrl = null)

private fun pendingWorkout(id: String) = PendingWorkout(
    id = id, userId = USER.id, routineId = null, startedAt = Instant.EPOCH, completedAt = Instant.EPOCH,
    notes = null, sets = emptyList(), status = PendingStatus.PENDING, lastError = null,
)

/** [SyncWorkoutsWorker] just maps [com.lucho314.spotter.domain.usecase.SyncOutcome] to a WorkManager `Result`; the sync logic itself is [com.lucho314.spotter.domain.usecase.SyncPendingWorkoutsUseCaseTest]'s job. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncWorkoutsWorkerTest {

    private fun buildWorker(useCase: SyncPendingWorkoutsUseCase): SyncWorkoutsWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                SyncWorkoutsWorker(appContext, workerParameters, useCase)
        }
        return TestListenableWorkerBuilder<SyncWorkoutsWorker>(context).setWorkerFactory(factory).build()
    }

    @Test
    fun `Done and NoUser both succeed`() = runTest {
        val noUserWorker = buildWorker(SyncPendingWorkoutsUseCase(FakeAuthRepository(AuthState.SignedOut), FakePendingWorkoutRepository()))
        assertThat(noUserWorker.doWork()).isEqualTo(ListenableWorker.Result.success())

        val pendingRepo = FakePendingWorkoutRepository().apply { seed(pendingWorkout("w1")) }
        val doneWorker = buildWorker(SyncPendingWorkoutsUseCase(FakeAuthRepository(AuthState.SignedIn(USER)), pendingRepo))
        assertThat(doneWorker.doWork()).isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `RetryLater maps to a retry Result`() = runTest {
        val pendingRepo = FakePendingWorkoutRepository().apply {
            seed(pendingWorkout("w1"))
            uploadErrors["w1"] = AppError.Network
        }
        val worker = buildWorker(SyncPendingWorkoutsUseCase(FakeAuthRepository(AuthState.SignedIn(USER)), pendingRepo))

        assertThat(worker.doWork()).isEqualTo(ListenableWorker.Result.retry())
    }
}
