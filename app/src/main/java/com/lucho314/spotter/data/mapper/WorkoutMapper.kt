package com.lucho314.spotter.data.mapper

import com.lucho314.spotter.data.remote.dto.WorkoutSessionDto
import com.lucho314.spotter.data.remote.dto.WorkoutSetDto
import com.lucho314.spotter.domain.model.WorkoutSessionDetail
import com.lucho314.spotter.domain.model.WorkoutSessionSummary
import com.lucho314.spotter.domain.model.WorkoutSet

fun WorkoutSetDto.toDomain(): WorkoutSet = WorkoutSet(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    exerciseName = exercise?.name,
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    rpe = rpe,
    isWarmup = isWarmup,
    completedAt = completedAt.toInstant(),
)

fun WorkoutSessionDto.toSummary(): WorkoutSessionSummary = WorkoutSessionSummary(
    id = id,
    routineId = routineId,
    routineName = routine?.name,
    startedAt = startedAt.toInstant(),
    completedAt = completedAt?.toInstant(),
)

fun WorkoutSessionDto.toDetail(): WorkoutSessionDetail = WorkoutSessionDetail(
    id = id,
    routineName = routine?.name,
    startedAt = startedAt.toInstant(),
    completedAt = completedAt?.toInstant(),
    notes = notes,
    sets = sets.map { it.toDomain() },
)
