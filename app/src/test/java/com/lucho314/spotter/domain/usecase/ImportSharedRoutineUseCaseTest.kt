package com.lucho314.spotter.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.ValidationReason
import com.lucho314.spotter.domain.model.ShareCode
import com.lucho314.spotter.domain.model.SharedRoutineContent
import com.lucho314.spotter.domain.model.SharedRoutineDay
import com.lucho314.spotter.domain.model.SharedRoutineExercise
import com.lucho314.spotter.testutil.FakeRoutineRepository
import com.lucho314.spotter.testutil.FakeSharingRepository
import com.lucho314.spotter.testutil.FakeTimeProvider
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ImportSharedRoutineUseCaseTest {

    private val userId = "user-1"
    private val code = requireNotNull(ShareCode.parse("K7MN3QXP"))

    private fun content(
        name: String = "Push",
        expiresAt: Instant? = null,
        days: List<SharedRoutineDay> = listOf(SharedRoutineDay(1, "Lunes")),
        exercises: List<SharedRoutineExercise> = listOf(SharedRoutineExercise(1, 1, 0, 3, 10, 90)),
    ) = SharedRoutineContent(routineName = name, description = null, daysPerWeek = null, days = days, exercises = exercises, expiresAt = expiresAt)

    private fun useCase(
        sharingRepository: FakeSharingRepository,
        routineRepository: FakeRoutineRepository = FakeRoutineRepository(),
        timeProvider: FakeTimeProvider = FakeTimeProvider(),
    ) = ImportSharedRoutineUseCase(sharingRepository, routineRepository, timeProvider)

    // --- preview ---

    @Test
    fun `preview returns NotFound when the code doesn't match any share`() = runTest {
        val sharingRepository = FakeSharingRepository().apply { sharedRoutineResult = AppResult.Success(null) }

        val result = useCase(sharingRepository).preview(code)

        assertThat(result).isEqualTo(AppResult.Failure(AppError.NotFound))
    }

    @Test
    fun `preview returns NotFound when expiresAt equals now`() = runTest {
        val timeProvider = FakeTimeProvider(instant = Instant.parse("2026-01-15T10:00:00Z"))
        val sharingRepository = FakeSharingRepository().apply {
            sharedRoutineResult = AppResult.Success(content(expiresAt = timeProvider.instant))
        }

        val result = useCase(sharingRepository, timeProvider = timeProvider).preview(code)

        assertThat(result).isEqualTo(AppResult.Failure(AppError.NotFound))
    }

    @Test
    fun `preview returns NotFound when already expired`() = runTest {
        val timeProvider = FakeTimeProvider(instant = Instant.parse("2026-01-15T10:00:00Z"))
        val sharingRepository = FakeSharingRepository().apply {
            sharedRoutineResult = AppResult.Success(content(expiresAt = timeProvider.instant.minusSeconds(60)))
        }

        val result = useCase(sharingRepository, timeProvider = timeProvider).preview(code)

        assertThat(result).isEqualTo(AppResult.Failure(AppError.NotFound))
    }

    @Test
    fun `preview succeeds for a future expiry or no expiry, with sanitized counts`() = runTest {
        val timeProvider = FakeTimeProvider(instant = Instant.parse("2026-01-15T10:00:00Z"))
        val sharingRepository = FakeSharingRepository().apply {
            sharedRoutineResult = AppResult.Success(content(expiresAt = timeProvider.instant.plusSeconds(60)))
        }

        val result = useCase(sharingRepository, timeProvider = timeProvider).preview(code)

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val preview = (result as AppResult.Success).value
        assertThat(preview.routineName).isEqualTo("Push")
        assertThat(preview.exerciseCount).isEqualTo(1)
        assertThat(preview.dayCount).isEqualTo(1)

        val noExpiry = useCase(FakeSharingRepository().apply { sharedRoutineResult = AppResult.Success(content()) }).preview(code)
        assertThat(noExpiry).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `a Network failure while fetching the share is propagated`() = runTest {
        val sharingRepository = FakeSharingRepository().apply { sharedRoutineResult = AppResult.Failure(AppError.Network) }

        val result = useCase(sharingRepository).preview(code)

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Network))
    }

    @Test
    fun `more than 100 exercises is reported as SHARED_ROUTINE_INVALID`() = runTest {
        val tooMany = (1..101).map { SharedRoutineExercise(it, 1, it, 3, 10, 90) }
        val sharingRepository = FakeSharingRepository().apply { sharedRoutineResult = AppResult.Success(content(exercises = tooMany)) }

        val result = useCase(sharingRepository).preview(code)

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Validation(ValidationReason.SHARED_ROUTINE_INVALID)))
    }

    // --- importRoutine ---

    @Test
    fun `importRoutine creates the routine with the imported name, description and days per week`() = runTest {
        val sharingRepository = FakeSharingRepository().apply { sharedRoutineResult = AppResult.Success(content()) }
        val routineRepository = FakeRoutineRepository()

        val result = useCase(sharingRepository, routineRepository).importRoutine(code, userId)

        assertThat(result).isEqualTo(AppResult.Success("new-routine-id"))
        assertThat(routineRepository.createRoutineCalls.single().first.name).isEqualTo("Push (importada)")
    }

    @Test
    fun `importRoutine adds the sanitized days, preserving dayNumber and sortOrder for exercises`() = runTest {
        val sharingRepository = FakeSharingRepository().apply { sharedRoutineResult = AppResult.Success(content()) }
        val routineRepository = FakeRoutineRepository()

        useCase(sharingRepository, routineRepository).importRoutine(code, userId)

        assertThat(routineRepository.addDaysCalls.single()).containsExactly(1 to "Lunes")
        val (exercise, sortOrder) = routineRepository.addExercisesCalls.single().single()
        assertThat(exercise.dayNumber).isEqualTo(1)
        assertThat(sortOrder).isEqualTo(0)
    }

    @Test
    fun `no days means addDays isn't called, no exercises means addExercises isn't called`() = runTest {
        val sharingRepository = FakeSharingRepository().apply {
            sharedRoutineResult = AppResult.Success(content(days = emptyList(), exercises = emptyList()))
        }
        val routineRepository = FakeRoutineRepository()

        val result = useCase(sharingRepository, routineRepository).importRoutine(code, userId)

        assertThat(result).isEqualTo(AppResult.Success("new-routine-id"))
        assertThat(routineRepository.addDaysCalls).isEmpty()
        assertThat(routineRepository.addExercisesCalls).isEmpty()
    }

    @Test
    fun `a failing addDays compensates by deleting the created routine and returns the original error`() = runTest {
        val sharingRepository = FakeSharingRepository().apply { sharedRoutineResult = AppResult.Success(content()) }
        val routineRepository = FakeRoutineRepository().apply { addDaysResult = AppResult.Failure(AppError.Network) }

        val result = useCase(sharingRepository, routineRepository).importRoutine(code, userId)

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Network))
        assertThat(routineRepository.deletedRoutineIds).containsExactly("new-routine-id")
    }

    @Test
    fun `a failing addExercises compensates by deleting the created routine`() = runTest {
        val sharingRepository = FakeSharingRepository().apply { sharedRoutineResult = AppResult.Success(content()) }
        val routineRepository = FakeRoutineRepository().apply { addExercisesResult = AppResult.Failure(AppError.Conflict()) }

        val result = useCase(sharingRepository, routineRepository).importRoutine(code, userId)

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat(routineRepository.deletedRoutineIds).containsExactly("new-routine-id")
    }

    @Test
    fun `a failing createRoutine leaves nothing to delete`() = runTest {
        val sharingRepository = FakeSharingRepository().apply { sharedRoutineResult = AppResult.Success(content()) }
        val routineRepository = FakeRoutineRepository().apply { createRoutineResult = AppResult.Failure(AppError.Network) }

        val result = useCase(sharingRepository, routineRepository).importRoutine(code, userId)

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Network))
        assertThat(routineRepository.deletedRoutineIds).isEmpty()
    }

    @Test
    fun `real cancellation mid-addExercises still compensates before the job completes`() = runTest {
        val sharingRepository = FakeSharingRepository().apply { sharedRoutineResult = AppResult.Success(content()) }
        val routineRepository = FakeRoutineRepository()
        val addExercisesStarted = CompletableDeferred<Unit>()
        val neverCompletes = CompletableDeferred<Unit>()
        routineRepository.addExercisesThrows = null
        val useCase = ImportSharedRoutineUseCase(
            sharingRepository,
            object : com.lucho314.spotter.domain.repository.RoutineRepository by routineRepository {
                override suspend fun addExercises(
                    userId: String,
                    routineId: String,
                    items: List<Pair<com.lucho314.spotter.domain.model.NewRoutineExercise, Int>>,
                ): AppResult<Unit> {
                    addExercisesStarted.complete(Unit)
                    neverCompletes.await()
                    return AppResult.Success(Unit)
                }
            },
            FakeTimeProvider(),
        )

        val job = launch { useCase.importRoutine(code, userId) }
        addExercisesStarted.await()
        job.cancel()
        job.join()

        assertThat(routineRepository.deletedRoutineIds).containsExactly("new-routine-id")
    }

    @Test
    fun `an already-expired share at import time reports NotFound without creating a routine`() = runTest {
        val timeProvider = FakeTimeProvider(instant = Instant.parse("2026-01-15T10:00:00Z"))
        val sharingRepository = FakeSharingRepository().apply {
            sharedRoutineResult = AppResult.Success(content(expiresAt = timeProvider.instant.minusSeconds(1)))
        }
        val routineRepository = FakeRoutineRepository()

        val result = useCase(sharingRepository, routineRepository, timeProvider).importRoutine(code, userId)

        assertThat(result).isEqualTo(AppResult.Failure(AppError.NotFound))
        assertThat(routineRepository.createRoutineCalls).isEmpty()
    }
}
