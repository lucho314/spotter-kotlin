package com.lucho314.spotter.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import com.lucho314.spotter.domain.model.PendingStatus
import com.lucho314.spotter.domain.model.PendingWorkout
import com.lucho314.spotter.testutil.FakeAuthRepository
import com.lucho314.spotter.testutil.FakePendingWorkoutRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test

private val USER = AuthUser(id = "user-1", email = "a@b.com", displayName = "Ada", avatarUrl = null)

private fun pendingWorkout(id: String, userId: String = USER.id) = PendingWorkout(
    id = id, userId = userId, routineId = null, startedAt = Instant.EPOCH, completedAt = Instant.EPOCH,
    notes = null, sets = emptyList(), status = PendingStatus.PENDING, lastError = null,
)

class SyncPendingWorkoutsUseCaseTest {

    private val authRepository = FakeAuthRepository(AuthState.SignedIn(USER))
    private val pendingWorkoutRepository = FakePendingWorkoutRepository()
    private val useCase = SyncPendingWorkoutsUseCase(authRepository, pendingWorkoutRepository)

    @Test
    fun `no signed-in user reports NoUser without touching the outbox`() = runTest {
        val signedOutAuth = FakeAuthRepository(AuthState.SignedOut)
        val result = SyncPendingWorkoutsUseCase(signedOutAuth, pendingWorkoutRepository)()

        assertThat(result).isEqualTo(SyncOutcome.NoUser)
        assertThat(pendingWorkoutRepository.uploadedIds).isEmpty()
    }

    @Test
    fun `a successful upload deletes the outbox row`() = runTest {
        pendingWorkoutRepository.seed(pendingWorkout("w1"))

        val result = useCase()

        assertThat(result).isEqualTo(SyncOutcome.Done)
        assertThat(pendingWorkoutRepository.deletedIds).containsExactly("w1")
    }

    @Test
    fun `a network error retries later and keeps the row, recording the attempt`() = runTest {
        pendingWorkoutRepository.seed(pendingWorkout("w1"))
        pendingWorkoutRepository.uploadErrors["w1"] = AppError.Network

        val result = useCase()

        assertThat(result).isEqualTo(SyncOutcome.RetryLater)
        assertThat(pendingWorkoutRepository.deletedIds).isEmpty()
        assertThat(pendingWorkoutRepository.markedFailedIds).isEmpty()
        assertThat(pendingWorkoutRepository.attemptedIds).containsExactly("w1")
    }

    @Test
    fun `a 42501 permission error is marked permanently FAILED`() = runTest {
        pendingWorkoutRepository.seed(pendingWorkout("w1"))
        pendingWorkoutRepository.uploadErrors["w1"] = AppError.Server(code = "42501")

        val result = useCase()

        assertThat(result).isEqualTo(SyncOutcome.Done)
        assertThat(pendingWorkoutRepository.markedFailedIds).containsExactly("w1")
        assertThat(pendingWorkoutRepository.attemptedIds).isEmpty()
    }

    @Test
    fun `rows belonging to a different user are left untouched`() = runTest {
        pendingWorkoutRepository.seed(pendingWorkout("mine", userId = USER.id))
        pendingWorkoutRepository.seed(pendingWorkout("other", userId = "user-2"))

        useCase()

        assertThat(pendingWorkoutRepository.uploadedIds).containsExactly("mine")
    }

    @Test
    fun `uploading the same workout twice never fails (idempotent upload)`() = runTest {
        pendingWorkoutRepository.seed(pendingWorkout("w1"))

        useCase() // uploaded & deleted
        pendingWorkoutRepository.seed(pendingWorkout("w1")) // simulates it reappearing (e.g. re-queued)
        val second = useCase()

        assertThat(second).isEqualTo(SyncOutcome.Done)
        assertThat(pendingWorkoutRepository.uploadedIds).containsExactly("w1", "w1")
    }
}
