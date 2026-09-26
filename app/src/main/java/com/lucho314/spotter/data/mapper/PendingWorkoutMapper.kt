package com.lucho314.spotter.data.mapper

import com.lucho314.spotter.core.database.entity.PendingWorkoutEntity
import com.lucho314.spotter.core.database.entity.PendingWorkoutSetEntity
import com.lucho314.spotter.core.database.entity.PendingWorkoutWithSets
import com.lucho314.spotter.data.remote.dto.WorkoutSessionInsertDto
import com.lucho314.spotter.data.remote.dto.WorkoutSetInsertDto
import com.lucho314.spotter.domain.model.PendingSet
import com.lucho314.spotter.domain.model.PendingStatus
import com.lucho314.spotter.domain.model.PendingWorkout

private const val COMPLETED_STATUS = "completed"

fun PendingWorkoutWithSets.toDomain(): PendingWorkout = PendingWorkout(
    id = workout.id,
    userId = workout.userId,
    routineId = workout.routineId,
    startedAt = workout.startedAt.toInstant(),
    completedAt = workout.completedAt.toInstant(),
    notes = workout.notes,
    sets = sets.sortedBy { it.setNumber }.map { it.toDomain() },
    status = PendingStatus.valueOf(workout.status),
    lastError = workout.lastError,
)

fun PendingWorkoutSetEntity.toDomain(): PendingSet = PendingSet(
    id = id,
    exerciseId = exerciseId,
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    isWarmup = isWarmup,
    completedAt = completedAt.toInstant(),
)

/**
 * `attempts` always starts at 0: this is only used when a workout first lands in the outbox
 * (`ActiveWorkoutRepositoryImpl.moveToOutbox`), never to update an existing row. `createdAtEpochMs`
 * uses [PendingWorkout.completedAt] rather than "now" so this mapper stays a pure function of its
 * input; it only drives FIFO sync ordering, for which the workout's own completion time works just
 * as well.
 */
fun PendingWorkout.toEntity(): PendingWorkoutEntity = PendingWorkoutEntity(
    id = id,
    userId = userId,
    routineId = routineId,
    startedAt = startedAt.toTimestampString(),
    completedAt = completedAt.toTimestampString(),
    notes = notes,
    status = status.name,
    attempts = 0,
    lastError = lastError,
    createdAtEpochMs = completedAt.toEpochMilli(),
)

fun PendingSet.toEntity(workoutId: String): PendingWorkoutSetEntity = PendingWorkoutSetEntity(
    id = id,
    workoutId = workoutId,
    exerciseId = exerciseId,
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    isWarmup = isWarmup,
    completedAt = completedAt.toTimestampString(),
)

/** Pending workouts only ever represent finished sessions (ADR A3-b), so status is always `completed`. */
fun PendingWorkout.toSessionInsertDto(): WorkoutSessionInsertDto = WorkoutSessionInsertDto(
    id = id,
    userId = userId,
    routineId = routineId,
    status = COMPLETED_STATUS,
    startedAt = startedAt.toTimestampString(),
    completedAt = completedAt.toTimestampString(),
    notes = notes,
)

fun PendingSet.toSetInsertDto(sessionId: String): WorkoutSetInsertDto = WorkoutSetInsertDto(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    isWarmup = isWarmup,
    completedAt = completedAt.toTimestampString(),
)
