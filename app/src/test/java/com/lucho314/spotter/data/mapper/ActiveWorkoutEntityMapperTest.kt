package com.lucho314.spotter.data.mapper

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.database.entity.ActiveExerciseEntity
import com.lucho314.spotter.core.database.entity.ActiveExerciseWithSets
import com.lucho314.spotter.core.database.entity.ActiveSessionEntity
import com.lucho314.spotter.core.database.entity.ActiveSessionWithExercises
import com.lucho314.spotter.core.database.entity.ActiveSetEntity
import com.lucho314.spotter.domain.model.ActiveExercise
import com.lucho314.spotter.domain.model.ActiveSet
import com.lucho314.spotter.domain.model.ActiveWorkout
import com.lucho314.spotter.domain.model.Equipment
import com.lucho314.spotter.domain.model.RestTimer
import com.lucho314.spotter.domain.model.WeightUnit
import java.time.Instant
import org.junit.Test

class ActiveWorkoutEntityMapperTest {

    @Test
    fun `entity to domain round-trips rest timer nulls`() {
        val session = ActiveSessionEntity(
            id = "s1",
            userId = "u1",
            routineId = "r1",
            routineName = "Push",
            dayName = "Lunes",
            startedAtEpochMs = 1_000L,
            weightUnit = "KG",
            currentExerciseIndex = 0,
            restEndsAtEpochMs = null,
            restTotalSeconds = null,
        )
        val exerciseEntity = ActiveExerciseEntity(
            id = 7L,
            sessionId = "s1",
            position = 0,
            exerciseId = 42,
            name = "Bench",
            equipment = "barbell",
            mediaUrl = null,
            imageUrl = null,
            targetSets = 3,
            targetReps = 10,
            restSeconds = 90,
        )
        val setEntity = ActiveSetEntity(
            id = "set1",
            activeExerciseId = 7L,
            setNumber = 1,
            weightText = "80",
            repsText = "10",
            isWarmup = false,
            completedAtEpochMs = null,
        )
        val withExercises = ActiveSessionWithExercises(session, listOf(ActiveExerciseWithSets(exerciseEntity, listOf(setEntity))))

        val domain = withExercises.toDomain()

        assertThat(domain.rest).isNull()
        assertThat(domain.weightUnit).isEqualTo(WeightUnit.KG)
        assertThat(domain.exercises.single().equipment).isEqualTo(Equipment.BARBELL)
        assertThat(domain.exercises.single().sets.single().isCompleted).isFalse()
    }

    @Test
    fun `a present rest timer maps both fields together`() {
        val session = ActiveSessionEntity(
            id = "s1", userId = "u1", routineId = null, routineName = "Free", dayName = null,
            startedAtEpochMs = 0L, weightUnit = "LB", currentExerciseIndex = 1,
            restEndsAtEpochMs = 5_000L, restTotalSeconds = 90,
        )
        val withExercises = ActiveSessionWithExercises(session, emptyList())

        val domain = withExercises.toDomain()

        assertThat(domain.rest).isEqualTo(RestTimer(Instant.ofEpochMilli(5_000L), 90))
    }

    @Test
    fun `domain to entity uses rowId 0 as the not-yet-persisted placeholder`() {
        val activeSet = ActiveSet(id = "set1", setNumber = 1, weightText = "80", repsText = "10", isWarmup = false, completedAt = null)
        val exercise = ActiveExercise(
            rowId = 0L,
            position = 0,
            exerciseId = 42,
            name = "Bench",
            equipment = Equipment.BARBELL,
            mediaUrl = null,
            imageUrl = null,
            targetSets = 3,
            targetReps = 10,
            restSeconds = 90,
            sets = listOf(activeSet),
        )

        val entity = exercise.toEntity(sessionId = "s1")

        assertThat(entity.id).isEqualTo(0L)
        assertThat(entity.sessionId).isEqualTo("s1")
        assertThat(entity.equipment).isEqualTo("barbell")
    }

    @Test
    fun `ActiveWorkout maps to a session entity preserving the weight unit name`() {
        val workout = ActiveWorkout(
            sessionId = "s1",
            userId = "u1",
            routineId = "r1",
            routineName = "Push",
            dayName = "Lunes",
            startedAt = Instant.ofEpochMilli(1000),
            weightUnit = WeightUnit.LB,
            currentExerciseIndex = 0,
            rest = null,
            exercises = emptyList(),
        )

        val entity = workout.toSessionEntity()

        assertThat(entity.weightUnit).isEqualTo("LB")
        assertThat(entity.startedAtEpochMs).isEqualTo(1000L)
    }
}
