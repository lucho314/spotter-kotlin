package com.lucho314.spotter.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.ActiveExercise
import com.lucho314.spotter.domain.model.ActiveWorkout
import com.lucho314.spotter.domain.model.Equipment
import com.lucho314.spotter.domain.model.PendingStatus
import com.lucho314.spotter.domain.model.PendingWorkout
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.testutil.FakeActiveWorkoutRepository
import com.lucho314.spotter.testutil.FakeAuthRepository
import com.lucho314.spotter.testutil.FakeLocalDataRepository
import com.lucho314.spotter.testutil.FakeLogger
import com.lucho314.spotter.testutil.FakePendingWorkoutRepository
import com.lucho314.spotter.testutil.FakePreferencesRepository
import com.lucho314.spotter.testutil.FakeRestTimerAlarmScheduler
import com.lucho314.spotter.testutil.FakeSyncScheduler
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val USER_ID = "user-1"

class SignOutUseCaseTest {

    private val authRepository = FakeAuthRepository()
    private val localDataRepository = FakeLocalDataRepository()
    private val preferencesRepository = FakePreferencesRepository()
    private val pendingWorkoutRepository = FakePendingWorkoutRepository()
    private val activeWorkoutRepository = FakeActiveWorkoutRepository()
    private val restTimerAlarmScheduler = FakeRestTimerAlarmScheduler()
    private val syncScheduler = FakeSyncScheduler()
    private val logger = FakeLogger()

    private val useCase = SignOutUseCase(
        authRepository, localDataRepository, preferencesRepository, pendingWorkoutRepository,
        activeWorkoutRepository, restTimerAlarmScheduler, syncScheduler, logger,
    )

    @Test
    fun `by the time Room is cleared, the alarm and the sync worker are already cancelled and signOut wasn't called yet`() = runTest {
        var alarmCancelledBeforeClear = false
        var syncCancelledBeforeClear = false
        var signedOutBeforeClear = false
        localDataRepository.onClearAll = {
            alarmCancelledBeforeClear = restTimerAlarmScheduler.cancelCallCount == 1
            syncCancelledBeforeClear = syncScheduler.cancelCallCount == 1
            signedOutBeforeClear = authRepository.signOutCallCount == 0
        }

        useCase()

        assertThat(alarmCancelledBeforeClear).isTrue()
        assertThat(syncCancelledBeforeClear).isTrue()
        assertThat(signedOutBeforeClear).isTrue()
    }

    @Test
    fun `clearUserScoped is called and the weight unit survives it`() = runTest {
        preferencesRepository.setWeightUnit(WeightUnit.LB)

        useCase()

        assertThat(preferencesRepository.clearUserScopedCallCount).isEqualTo(1)
        assertThat(preferencesRepository.weightUnit.value).isEqualTo(WeightUnit.LB)
    }

    @Test
    fun `if clearAll fails, sign-out is aborted and the sync worker is rescheduled`() = runTest {
        localDataRepository.clearError = RuntimeException("boom")

        val result = useCase()

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat(authRepository.signOutCallCount).isEqualTo(0)
        assertThat(syncScheduler.scheduleCallCount).isEqualTo(1)
        assertThat(preferencesRepository.clearUserScopedCallCount).isEqualTo(0)
    }

    @Test
    fun `if signOut fails, clearAll and clearUserScoped already ran`() = runTest {
        authRepository.signOutResult = AppResult.Failure(AppError.Network)

        val result = useCase()

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Network))
        assertThat(localDataRepository.clearCallCount).isEqualTo(1)
        assertThat(preferencesRepository.clearUserScopedCallCount).isEqualTo(1)
    }

    @Test
    fun `risk counts the outbox and detects an active workout`() = runTest {
        pendingWorkoutRepository.seed(
            PendingWorkout(
                id = "pw-1", userId = USER_ID, routineId = null, startedAt = Instant.EPOCH,
                completedAt = Instant.EPOCH, notes = null, sets = emptyList(), status = PendingStatus.PENDING, lastError = null,
            ),
        )
        activeWorkoutRepository.start(
            ActiveWorkout(
                sessionId = "s1", userId = USER_ID, routineId = null, routineName = "Push", dayName = null,
                startedAt = Instant.EPOCH, weightUnit = WeightUnit.KG, currentExerciseIndex = 0, rest = null,
                exercises = listOf(
                    ActiveExercise(
                        rowId = 1L, position = 0, exerciseId = 1, name = "Press", equipment = Equipment.BARBELL,
                        mediaUrl = null, imageUrl = null, targetSets = 3, targetReps = 10, restSeconds = 90, sets = emptyList(),
                    ),
                ),
            ),
        )

        val risk = useCase.risk(USER_ID)

        assertThat(risk.unsyncedWorkouts).isEqualTo(1)
        assertThat(risk.hasActiveWorkout).isTrue()
        assertThat(risk.isEmpty).isFalse()
    }

    @Test
    fun `risk isEmpty when there is nothing pending and nothing active`() = runTest {
        val risk = useCase.risk(USER_ID)

        assertThat(risk.isEmpty).isTrue()
    }
}
