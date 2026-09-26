package com.lucho314.spotter.data.mapper

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.data.remote.dto.IdDto
import com.lucho314.spotter.data.remote.dto.RoutineDayDto
import com.lucho314.spotter.data.remote.dto.RoutineExerciseDto
import com.lucho314.spotter.data.remote.dto.RoutineSummaryDto
import com.lucho314.spotter.domain.model.NewRoutineExercise
import com.lucho314.spotter.domain.model.RoutineInput
import java.time.Instant
import org.junit.Test

class RoutineMapperTest {

    @Test
    fun `RoutineSummaryDto exerciseCount comes from the routine_exercises id list size`() {
        val dto = RoutineSummaryDto(
            id = "r1",
            userId = "u1",
            name = "Push",
            isArchived = false,
            createdAt = "2026-01-15T10:00:00+00:00",
            updatedAt = "2026-01-15T10:00:00+00:00",
            routineExercises = listOf(IdDto("re1"), IdDto("re2"), IdDto("re3")),
        )

        val domain = dto.toDomain()

        assertThat(domain.exerciseCount).isEqualTo(3)
        assertThat(domain.createdAt).isEqualTo(Instant.parse("2026-01-15T10:00:00Z"))
    }

    @Test
    fun `RoutineDayDto maps 1-1`() {
        val dto = RoutineDayDto(id = "d1", routineId = "r1", dayNumber = 2, name = "Martes")

        val domain = dto.toDomain()

        assertThat(domain.dayNumber).isEqualTo(2)
        assertThat(domain.name).isEqualTo("Martes")
    }

    @Test
    fun `RoutineExerciseDto maps its nested exercise`() {
        val dto = RoutineExerciseDto(
            id = "re1",
            routineId = "r1",
            exerciseId = 42,
            sortOrder = 0,
            dayNumber = 1,
            targetSets = 4,
            targetReps = 8,
            restSeconds = 120,
        )

        val domain = dto.toDomain()

        assertThat(domain.exercise).isNull()
        assertThat(domain.targetSets).isEqualTo(4)
    }

    @Test
    fun `RoutineInput toInsertDto trims the name and carries the source template id`() {
        val input = RoutineInput(name = "  Push Day  ", description = "desc", daysPerWeek = 3)

        val dto = input.toInsertDto(userId = "u1", sourceTemplateId = "t1")

        assertThat(dto.name).isEqualTo("Push Day")
        assertThat(dto.userId).isEqualTo("u1")
        assertThat(dto.sourceTemplateId).isEqualTo("t1")
    }

    @Test
    fun `NewRoutineExercise toInsertDto carries the routineId and sortOrder given by the caller`() {
        val input = NewRoutineExercise(exerciseId = 42, dayNumber = 2, targetSets = 3, targetReps = 10, restSeconds = 90)

        val dto = input.toInsertDto(routineId = "r1", sortOrder = 5)

        assertThat(dto.routineId).isEqualTo("r1")
        assertThat(dto.sortOrder).isEqualTo(5)
        assertThat(dto.dayNumber).isEqualTo(2)
    }
}
