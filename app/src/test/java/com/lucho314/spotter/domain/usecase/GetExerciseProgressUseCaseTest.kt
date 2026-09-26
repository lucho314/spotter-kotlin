package com.lucho314.spotter.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.WorkoutSet
import com.lucho314.spotter.testutil.FakeProgressRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetExerciseProgressUseCaseTest {

    private val progressRepository = FakeProgressRepository()
    private val useCase = GetExerciseProgressUseCase(progressRepository)

    private fun set(sessionId: String, dayOffset: Long) = WorkoutSet(
        id = "$sessionId-set", sessionId = sessionId, exerciseId = 1, exerciseName = "Press",
        setNumber = 1, weightKg = 80.0, reps = 10, rpe = null, isWarmup = false,
        completedAt = Instant.EPOCH.plusSeconds(dayOffset * 86_400),
    )

    @Test
    fun `aggregates per session and keeps at most 12, ascending`() = runTest {
        progressRepository.exerciseSetsResult = AppResult.Success((1..15L).map { set("session-$it", it) })

        val result = useCase("user-1", exerciseId = 1)

        val points = (result as AppResult.Success).value
        assertThat(points).hasSize(12)
        assertThat(points.map { it.date }).isEqualTo(points.map { it.date }.sorted())
        assertThat(points.first().sessionId).isEqualTo("session-4") // oldest 3 dropped
    }

    @Test
    fun `requests up to 500 sets`() = runTest {
        useCase("user-1", exerciseId = 1)

        assertThat(progressRepository.getExerciseSetsCalls.single()).isEqualTo(Triple("user-1", 1, 500))
    }

    @Test
    fun `a failure is propagated`() = runTest {
        progressRepository.exerciseSetsResult = AppResult.Failure(AppError.Network)

        val result = useCase("user-1", exerciseId = 1)

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Network))
    }
}
