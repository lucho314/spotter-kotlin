package com.lucho314.spotter.data.mapper

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.database.entity.PendingWorkoutEntity
import com.lucho314.spotter.core.database.entity.PendingWorkoutSetEntity
import com.lucho314.spotter.core.database.entity.PendingWorkoutWithSets
import com.lucho314.spotter.domain.model.PendingSet
import com.lucho314.spotter.domain.model.PendingStatus
import com.lucho314.spotter.domain.model.PendingWorkout
import java.time.Instant
import org.junit.Test

class PendingWorkoutMapperTest {

    @Test
    fun `entity to domain parses status and timestamps`() {
        val workoutEntity = PendingWorkoutEntity(
            id = "w1", userId = "u1", routineId = "r1",
            startedAt = "2026-01-15T10:00:00Z", completedAt = "2026-01-15T11:00:00Z",
            notes = null, status = "FAILED", attempts = 2, lastError = "network",
            createdAtEpochMs = 0L,
        )
        val setEntity = PendingWorkoutSetEntity(
            id = "set1", workoutId = "w1", exerciseId = 42, setNumber = 1,
            weightKg = 80.0, reps = 10, isWarmup = false, completedAt = "2026-01-15T11:00:00Z",
        )

        val domain = PendingWorkoutWithSets(workoutEntity, listOf(setEntity)).toDomain()

        assertThat(domain.status).isEqualTo(PendingStatus.FAILED)
        assertThat(domain.lastError).isEqualTo("network")
        assertThat(domain.sets.single().weightKg).isWithin(1e-9).of(80.0)
    }

    @Test
    fun `domain to entity always starts attempts at 0 and derives createdAt from completedAt`() {
        val workout = PendingWorkout(
            id = "w1", userId = "u1", routineId = null,
            startedAt = Instant.ofEpochMilli(1000), completedAt = Instant.ofEpochMilli(2000),
            notes = null, sets = emptyList(), status = PendingStatus.PENDING, lastError = null,
        )

        val entity = workout.toEntity()

        assertThat(entity.attempts).isEqualTo(0)
        assertThat(entity.createdAtEpochMs).isEqualTo(2000L)
        assertThat(entity.status).isEqualTo("PENDING")
    }

    @Test
    fun `pending workouts always upload as status completed`() {
        val workout = PendingWorkout(
            id = "w1", userId = "u1", routineId = null,
            startedAt = Instant.ofEpochMilli(1000), completedAt = Instant.ofEpochMilli(2000),
            notes = "felt good", sets = emptyList(), status = PendingStatus.PENDING, lastError = null,
        )

        val dto = workout.toSessionInsertDto()

        assertThat(dto.status).isEqualTo("completed")
        assertThat(dto.notes).isEqualTo("felt good")
    }

    @Test
    fun `pending set maps to a set insert dto scoped to its session`() {
        val set = PendingSet(id = "set1", exerciseId = 42, setNumber = 1, weightKg = 80.0, reps = 10, isWarmup = false, completedAt = Instant.EPOCH)

        val dto = set.toSetInsertDto(sessionId = "session-1")

        assertThat(dto.sessionId).isEqualTo("session-1")
        assertThat(dto.exerciseId).isEqualTo(42)
    }
}
