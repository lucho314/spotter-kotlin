package com.lucho314.spotter.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.Difficulty
import com.lucho314.spotter.domain.model.NewRoutineExercise
import com.lucho314.spotter.domain.model.RoutineTemplateSummary
import com.lucho314.spotter.domain.model.TemplateDay
import com.lucho314.spotter.domain.model.TemplateDetail
import com.lucho314.spotter.domain.model.TemplateExercise
import com.lucho314.spotter.domain.model.TemplateGoal
import com.lucho314.spotter.domain.model.UNASSIGNED_DAY_NUMBER
import com.lucho314.spotter.domain.repository.RoutineRepository
import com.lucho314.spotter.testutil.FakeRoutineRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AdoptTemplateUseCaseTest {

    private val userId = "user-1"

    private fun templateExercise(exerciseId: Int, sortOrder: Int) = TemplateExercise(
        exerciseId = exerciseId,
        exercise = null,
        sortOrder = sortOrder,
        targetSets = 3,
        targetReps = 10,
        restSeconds = 90,
    )

    private fun template(dayCount: Int = 3, exercisesPerDay: Int = 2) = TemplateDetail(
        summary = RoutineTemplateSummary(
            id = "template-1",
            name = "Fuerza total",
            description = "Programa de fuerza",
            goal = TemplateGoal.STRENGTH,
            difficulty = Difficulty.INTERMEDIATE,
            daysPerWeek = dayCount,
        ),
        days = (1..dayCount).map { dayNumber ->
            TemplateDay(
                id = "day-$dayNumber",
                dayNumber = dayNumber,
                name = "Día $dayNumber",
                description = null,
                exercises = (1..exercisesPerDay).map { templateExercise(exerciseId = it, sortOrder = it - 1) },
            )
        },
    )

    @Test
    fun `creates one routine per template day, with its exercises unassigned`() = runTest {
        val routineRepository = FakeRoutineRepository()
        val useCase = AdoptTemplateUseCase(routineRepository)

        val result = useCase(userId, template(dayCount = 3))

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val createdIds = (result as AppResult.Success).value
        assertThat(createdIds).hasSize(3)
        assertThat(routineRepository.createRoutineCalls.map { it.first.name }).containsExactly(
            "Fuerza total - Día 1",
            "Fuerza total - Día 2",
            "Fuerza total - Día 3",
        ).inOrder()
        assertThat(routineRepository.createRoutineCalls.all { it.second == "template-1" }).isTrue()
        assertThat(routineRepository.addExercisesCalls).hasSize(3)
        routineRepository.addExercisesCalls.forEach { items ->
            assertThat(items).hasSize(2)
            assertThat(items.all { it.first.dayNumber == UNASSIGNED_DAY_NUMBER }).isTrue()
        }
    }

    @Test
    fun `compensates by deleting every routine created so far when a later create fails`() = runTest {
        val routineRepository = FakeRoutineRepository()
        // Fail on the 3rd createRoutine call (2 already created and adopted successfully).
        var callCount = 0
        val useCase = AdoptTemplateUseCase(object : com.lucho314.spotter.domain.repository.RoutineRepository by routineRepository {
            override suspend fun createRoutine(
                userId: String,
                input: com.lucho314.spotter.domain.model.RoutineInput,
                sourceTemplateId: String?,
            ): AppResult<String> {
                callCount++
                routineRepository.createRoutineCalls += input to sourceTemplateId
                return if (callCount < 3) {
                    AppResult.Success("routine-$callCount")
                } else {
                    AppResult.Failure(AppError.Conflict("boom"))
                }
            }
        })

        val result = useCase(userId, template(dayCount = 3))

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error).isEqualTo(AppError.Conflict("boom"))
        assertThat(routineRepository.deletedRoutineIds).containsExactly("routine-1", "routine-2")
    }

    @Test
    fun `compensates when adding a day's exercises fails after its routine was created`() = runTest {
        val routineRepository = FakeRoutineRepository()
        routineRepository.addExercisesResult = AppResult.Failure(AppError.Network)
        val useCase = AdoptTemplateUseCase(routineRepository)

        val result = useCase(userId, template(dayCount = 2))

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Network))
        // Only the first day's routine was created before the failure on its addExercises call.
        assertThat(routineRepository.deletedRoutineIds).containsExactly("new-routine-id")
    }

    @Test
    fun `actually cancelling the coroutine mid-adoption still compensates before the job completes`() = runTest {
        val routineRepository = FakeRoutineRepository()
        val addExercisesStarted = CompletableDeferred<Unit>()
        val neverCompletes = CompletableDeferred<Unit>()
        val useCase = AdoptTemplateUseCase(object : RoutineRepository by routineRepository {
            override suspend fun addExercises(userId: String, routineId: String, items: List<Pair<NewRoutineExercise, Int>>): AppResult<Unit> {
                addExercisesStarted.complete(Unit)
                neverCompletes.await() // suspends until cancelled - exactly where a real navigate-away would land.
                return AppResult.Success(Unit)
            }
        })

        val job = launch { useCase(userId, template(dayCount = 2)) }
        addExercisesStarted.await() // let the first day's routine get created and its addExercises call start.
        job.cancel()
        job.join()

        // The routine created before the cancellation is cleaned up, same as a regular Failure
        // would be - compensation must not be skipped just because this coroutine is cancelled,
        // and it must run under NonCancellable to actually get to finish (see the use case's KDoc).
        assertThat(routineRepository.deletedRoutineIds).containsExactly("new-routine-id")
    }

    @Test
    fun `createRoutine returning a Failure is unaffected by cancellation handling (sanity check)`() = runTest {
        val routineRepository = FakeRoutineRepository().apply { createRoutineResult = AppResult.Failure(AppError.Network) }
        val useCase = AdoptTemplateUseCase(routineRepository)

        val result = useCase(userId, template(dayCount = 1))

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Network))
    }

    @Test
    fun `a template with no days creates nothing`() = runTest {
        val routineRepository = FakeRoutineRepository()
        val useCase = AdoptTemplateUseCase(routineRepository)

        val result = useCase(userId, template(dayCount = 0))

        assertThat(result).isEqualTo(AppResult.Success(emptyList<String>()))
        assertThat(routineRepository.createRoutineCalls).isEmpty()
    }
}
