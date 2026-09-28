package com.lucho314.spotter.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import com.lucho314.spotter.domain.model.GarminActivitySnapshot
import com.lucho314.spotter.domain.model.GarminConnectionState
import com.lucho314.spotter.domain.model.GarminError
import com.lucho314.spotter.domain.model.GarminResult
import com.lucho314.spotter.domain.model.GarminSetSnapshot
import com.lucho314.spotter.domain.model.GarminUploadOutcome
import com.lucho314.spotter.domain.model.GarminUploadStatus
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.domain.model.WorkoutSessionDetail
import com.lucho314.spotter.domain.model.WorkoutSet
import com.lucho314.spotter.testutil.FakeAuthRepository
import com.lucho314.spotter.testutil.FakeExerciseRepository
import com.lucho314.spotter.testutil.FakeGarminAccountRepository
import com.lucho314.spotter.testutil.FakeGarminActivityRepository
import com.lucho314.spotter.testutil.FakeGarminUploadRepository
import com.lucho314.spotter.testutil.FakePreferencesRepository
import com.lucho314.spotter.testutil.FakeTimeProvider
import com.lucho314.spotter.testutil.FakeWorkoutHistoryRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val USER_ID = "user-1"
private val USER = AuthUser(id = USER_ID, email = "a@b.com", displayName = "Ada", avatarUrl = null)
private val CONNECTED = GarminConnectionState.Connected(displayName = "Ada", autoUpload = true, needsReconnect = false)

class UploadPendingGarminActivitiesUseCaseTest {

    private val authRepository = FakeAuthRepository(AuthState.SignedIn(USER))
    private val garminAccountRepository = FakeGarminAccountRepository(CONNECTED)
    private val garminUploadRepository = FakeGarminUploadRepository()
    private val garminActivityRepository = FakeGarminActivityRepository()
    private val workoutHistoryRepository = FakeWorkoutHistoryRepository()
    private val exerciseRepository = FakeExerciseRepository()
    private val preferencesRepository = FakePreferencesRepository()
    private val timeProvider = FakeTimeProvider()

    private val useCase = UploadPendingGarminActivitiesUseCase(
        authRepository, garminAccountRepository, garminUploadRepository, garminActivityRepository,
        workoutHistoryRepository, exerciseRepository, preferencesRepository, timeProvider,
    )

    private fun snapshot(workoutId: String = "w1") = GarminActivitySnapshot(
        workoutId = workoutId, userId = USER_ID, startedAt = Instant.ofEpochSecond(1_700_000_000),
        completedAt = Instant.ofEpochSecond(1_700_000_100), weightUnit = WeightUnit.KG,
        sets = listOf(
            GarminSetSnapshot(
                exerciseId = 1, exerciseName = "Press", equipment = null, exerciseOrder = 0, setNumber = 1,
                weightKg = 80.0, reps = 10, isWarmup = false, completedAt = Instant.ofEpochSecond(1_700_000_050),
            ),
        ),
    )

    @Test
    fun `nobody signed in reports NoUser`() = runTest {
        authRepository.state.value = AuthState.SignedOut
        assertThat(useCase()).isEqualTo(GarminSyncOutcome.NoUser)
    }

    @Test
    fun `auth state stuck Loading reports RetryLater`() = runTest {
        authRepository.state.value = AuthState.Loading
        assertThat(useCase()).isEqualTo(GarminSyncOutcome.RetryLater)
    }

    @Test
    fun `not connected reports NotConnected and leaves rows untouched`() = runTest {
        garminAccountRepository.connection.value = GarminConnectionState.NotConnected
        garminUploadRepository.seed("w1", USER_ID, GarminUploadStatus.PENDING, snapshot = snapshot())

        assertThat(useCase()).isEqualTo(GarminSyncOutcome.NotConnected)
        assertThat(garminUploadRepository.rows["w1"]!!.status).isEqualTo(GarminUploadStatus.PENDING)
    }

    @Test
    fun `needsReconnect reports NeedsReconnect without touching rows`() = runTest {
        garminAccountRepository.connection.value = CONNECTED.copy(needsReconnect = true)
        garminUploadRepository.seed("w1", USER_ID, GarminUploadStatus.PENDING, snapshot = snapshot())

        assertThat(useCase()).isEqualTo(GarminSyncOutcome.NeedsReconnect)
        assertThat(garminUploadRepository.rows["w1"]!!.status).isEqualTo(GarminUploadStatus.PENDING)
    }

    @Test
    fun `a successful upload marks the row UPLOADED`() = runTest {
        garminUploadRepository.seed("w1", USER_ID, GarminUploadStatus.PENDING, snapshot = snapshot())
        garminActivityRepository.defaultResult = GarminResult.Success(GarminUploadOutcome.Uploaded(11L, 22L))

        val outcome = useCase()

        assertThat(outcome).isEqualTo(GarminSyncOutcome.Done)
        val row = garminUploadRepository.rows["w1"]!!
        assertThat(row.status).isEqualTo(GarminUploadStatus.UPLOADED)
        assertThat(row.activityId).isEqualTo(11L)
    }

    @Test
    fun `AlreadyExists also marks the row UPLOADED`() = runTest {
        garminUploadRepository.seed("w1", USER_ID, GarminUploadStatus.PENDING, snapshot = snapshot())
        garminActivityRepository.defaultResult = GarminResult.Success(GarminUploadOutcome.AlreadyExists(33L))

        useCase()

        assertThat(garminUploadRepository.rows["w1"]!!.status).isEqualTo(GarminUploadStatus.UPLOADED)
    }

    @Test
    fun `a Network failure records an attempt and reports RetryLater`() = runTest {
        garminUploadRepository.seed("w1", USER_ID, GarminUploadStatus.PENDING, attempts = 0, snapshot = snapshot())
        garminActivityRepository.defaultResult = GarminResult.Failure(GarminError.Network)

        val outcome = useCase()

        assertThat(outcome).isEqualTo(GarminSyncOutcome.RetryLater)
        val row = garminUploadRepository.rows["w1"]!!
        assertThat(row.status).isEqualTo(GarminUploadStatus.PENDING)
        assertThat(row.attempts).isEqualTo(1)
    }

    @Test
    fun `the 10th transient failure marks the row FAILED`() = runTest {
        garminUploadRepository.seed("w1", USER_ID, GarminUploadStatus.PENDING, attempts = 9, snapshot = snapshot())
        garminActivityRepository.defaultResult = GarminResult.Failure(GarminError.Network)

        useCase()

        val row = garminUploadRepository.rows["w1"]!!
        assertThat(row.status).isEqualTo(GarminUploadStatus.FAILED)
    }

    @Test
    fun `InvalidFile fails the row immediately, regardless of attempts`() = runTest {
        garminUploadRepository.seed("w1", USER_ID, GarminUploadStatus.PENDING, attempts = 0, snapshot = snapshot())
        garminActivityRepository.defaultResult = GarminResult.Failure(GarminError.InvalidFile(400))

        useCase()

        val row = garminUploadRepository.rows["w1"]!!
        assertThat(row.status).isEqualTo(GarminUploadStatus.FAILED)
        assertThat(row.lastError).isEqualTo("invalid_file:400")
    }

    @Test
    fun `RateLimited stops the loop - a second row is never attempted`() = runTest {
        garminUploadRepository.seed("w1", USER_ID, GarminUploadStatus.PENDING, snapshot = snapshot("w1"))
        garminUploadRepository.seed("w2", USER_ID, GarminUploadStatus.PENDING, snapshot = snapshot("w2"))
        garminActivityRepository.defaultResult = GarminResult.Failure(GarminError.RateLimited)

        val outcome = useCase()

        assertThat(outcome).isEqualTo(GarminSyncOutcome.RetryLater)
        assertThat(garminActivityRepository.uploadCalls).hasSize(1)
        assertThat(garminUploadRepository.rows["w2"]!!.attempts).isEqualTo(0)
    }

    @Test
    fun `ReauthRequired stops the loop and reports NeedsReconnect, leaving the row PENDING`() = runTest {
        garminUploadRepository.seed("w1", USER_ID, GarminUploadStatus.PENDING, snapshot = snapshot())
        garminActivityRepository.defaultResult = GarminResult.Failure(GarminError.ReauthRequired)

        val outcome = useCase()

        assertThat(outcome).isEqualTo(GarminSyncOutcome.NeedsReconnect)
        assertThat(garminUploadRepository.rows["w1"]!!.status).isEqualTo(GarminUploadStatus.PENDING)
    }

    @Test
    fun `a null payload is resolved from history - NotFound marks the row FAILED with not_found`() = runTest {
        garminUploadRepository.seed("w1", USER_ID, GarminUploadStatus.PENDING, snapshot = null)
        workoutHistoryRepository.sessionResult = AppResult.Failure(AppError.NotFound)

        useCase()

        val row = garminUploadRepository.rows["w1"]!!
        assertThat(row.status).isEqualTo(GarminUploadStatus.FAILED)
        assertThat(row.lastError).isEqualTo("not_found")
    }

    @Test
    fun `a null payload resolved from history on a network error records an attempt and retries`() = runTest {
        garminUploadRepository.seed("w1", USER_ID, GarminUploadStatus.PENDING, snapshot = null)
        workoutHistoryRepository.sessionResult = AppResult.Failure(AppError.Network)

        val outcome = useCase()

        assertThat(outcome).isEqualTo(GarminSyncOutcome.RetryLater)
        assertThat(garminUploadRepository.rows["w1"]!!.lastError).isEqualTo("history_network")
    }

    @Test
    fun `a corrupt snapshot is marked FAILED with decode, without calling the remote`() = runTest {
        garminUploadRepository.seed("w1", USER_ID, GarminUploadStatus.PENDING, snapshotCorrupt = true)

        useCase()

        val row = garminUploadRepository.rows["w1"]!!
        assertThat(row.status).isEqualTo(GarminUploadStatus.FAILED)
        assertThat(row.lastError).isEqualTo("decode")
        assertThat(garminActivityRepository.uploadCalls).isEmpty()
    }

    @Test
    fun `an empty snapshot (no sets) is marked FAILED with empty`() = runTest {
        val empty = snapshot().copy(sets = emptyList())
        garminUploadRepository.seed("w1", USER_ID, GarminUploadStatus.PENDING, snapshot = empty)

        useCase()

        val row = garminUploadRepository.rows["w1"]!!
        assertThat(row.status).isEqualTo(GarminUploadStatus.FAILED)
        assertThat(row.lastError).isEqualTo("empty")
    }

    @Test
    fun `a failing getExercise does not block the upload`() = runTest {
        garminUploadRepository.seed("w1", USER_ID, GarminUploadStatus.PENDING, snapshot = snapshot())
        exerciseRepository.getExerciseResult = AppResult.Failure(AppError.NotFound)
        garminActivityRepository.defaultResult = GarminResult.Success(GarminUploadOutcome.Uploaded(1L, 2L))

        val outcome = useCase()

        assertThat(outcome).isEqualTo(GarminSyncOutcome.Done)
        assertThat(garminUploadRepository.rows["w1"]!!.status).isEqualTo(GarminUploadStatus.UPLOADED)
    }
}
