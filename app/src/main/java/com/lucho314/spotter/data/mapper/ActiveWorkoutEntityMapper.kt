package com.lucho314.spotter.data.mapper

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

fun ActiveSessionWithExercises.toDomain(): ActiveWorkout = ActiveWorkout(
    sessionId = session.id,
    userId = session.userId,
    routineId = session.routineId,
    routineName = session.routineName,
    dayName = session.dayName,
    startedAt = Instant.ofEpochMilli(session.startedAtEpochMs),
    // `WeightUnit.valueOf` throws if the stored value is somehow corrupt; that should never happen
    // since it's only ever written from `WeightUnit.name` below.
    weightUnit = WeightUnit.valueOf(session.weightUnit),
    currentExerciseIndex = session.currentExerciseIndex,
    rest = restTimerOf(session.restEndsAtEpochMs, session.restTotalSeconds),
    exercises = exercises.sortedBy { it.exercise.position }.map { it.toDomain() },
)

private fun restTimerOf(endsAtEpochMs: Long?, totalSeconds: Int?): RestTimer? =
    if (endsAtEpochMs != null && totalSeconds != null) {
        RestTimer(endsAt = Instant.ofEpochMilli(endsAtEpochMs), totalSeconds = totalSeconds)
    } else {
        null
    }

fun ActiveExerciseWithSets.toDomain(): ActiveExercise = ActiveExercise(
    rowId = exercise.id,
    position = exercise.position,
    exerciseId = exercise.exerciseId,
    name = exercise.name,
    equipment = Equipment.fromApi(exercise.equipment),
    mediaUrl = exercise.mediaUrl,
    imageUrl = exercise.imageUrl,
    targetSets = exercise.targetSets,
    targetReps = exercise.targetReps,
    restSeconds = exercise.restSeconds,
    sets = sets.sortedBy { it.setNumber }.map { it.toDomain() },
    note = exercise.note,
)

fun ActiveSetEntity.toDomain(): ActiveSet = ActiveSet(
    id = id,
    setNumber = setNumber,
    weightText = weightText,
    repsText = repsText,
    isWarmup = isWarmup,
    completedAt = completedAtEpochMs?.let { Instant.ofEpochMilli(it) },
)

fun ActiveWorkout.toSessionEntity(): ActiveSessionEntity = ActiveSessionEntity(
    id = sessionId,
    userId = userId,
    routineId = routineId,
    routineName = routineName,
    dayName = dayName,
    startedAtEpochMs = startedAt.toEpochMilli(),
    weightUnit = weightUnit.name,
    currentExerciseIndex = currentExerciseIndex,
    restEndsAtEpochMs = rest?.endsAt?.toEpochMilli(),
    restTotalSeconds = rest?.totalSeconds,
)

/** [ActiveExercise.rowId] `0` means "not yet persisted": Room's autoGenerate treats it as unset. */
fun ActiveExercise.toEntity(sessionId: String): ActiveExerciseEntity = ActiveExerciseEntity(
    id = rowId,
    sessionId = sessionId,
    position = position,
    exerciseId = exerciseId,
    name = name,
    equipment = equipment.apiValue,
    mediaUrl = mediaUrl,
    imageUrl = imageUrl,
    targetSets = targetSets,
    targetReps = targetReps,
    restSeconds = restSeconds,
    note = note,
)

fun ActiveSet.toEntity(activeExerciseId: Long): ActiveSetEntity = ActiveSetEntity(
    id = id,
    activeExerciseId = activeExerciseId,
    setNumber = setNumber,
    weightText = weightText,
    repsText = repsText,
    isWarmup = isWarmup,
    completedAtEpochMs = completedAt?.toEpochMilli(),
)
