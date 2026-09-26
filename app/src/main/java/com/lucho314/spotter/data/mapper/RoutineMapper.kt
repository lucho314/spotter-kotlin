package com.lucho314.spotter.data.mapper

import com.lucho314.spotter.data.remote.dto.RoutineDayDto
import com.lucho314.spotter.data.remote.dto.RoutineDayInsertDto
import com.lucho314.spotter.data.remote.dto.RoutineDetailDto
import com.lucho314.spotter.data.remote.dto.RoutineExerciseDto
import com.lucho314.spotter.data.remote.dto.RoutineExerciseInsertDto
import com.lucho314.spotter.data.remote.dto.RoutineInsertDto
import com.lucho314.spotter.data.remote.dto.RoutineSummaryDto
import com.lucho314.spotter.domain.model.NewRoutineExercise
import com.lucho314.spotter.domain.model.RoutineDay
import com.lucho314.spotter.domain.model.RoutineDetail
import com.lucho314.spotter.domain.model.RoutineExercise
import com.lucho314.spotter.domain.model.RoutineInput
import com.lucho314.spotter.domain.model.RoutineSummary

fun RoutineDayDto.toDomain(): RoutineDay = RoutineDay(
    id = id,
    routineId = routineId,
    dayNumber = dayNumber,
    name = name,
)

fun RoutineExerciseDto.toDomain(): RoutineExercise = RoutineExercise(
    id = id,
    routineId = routineId,
    exerciseId = exerciseId,
    exercise = exercise?.toDomain(),
    sortOrder = sortOrder,
    dayNumber = dayNumber,
    targetSets = targetSets,
    targetReps = targetReps,
    restSeconds = restSeconds,
)

fun RoutineSummaryDto.toDomain(): RoutineSummary = RoutineSummary(
    id = id,
    name = name,
    description = description,
    daysPerWeek = daysPerWeek,
    exerciseCount = routineExercises.size,
    days = routineDays.map { it.toDomain() },
    createdAt = createdAt.toInstant(),
)

fun RoutineDetailDto.toDomain(): RoutineDetail = RoutineDetail(
    id = id,
    userId = userId,
    name = name,
    description = description,
    daysPerWeek = daysPerWeek,
    isArchived = isArchived,
    days = routineDays.map { it.toDomain() },
    exercises = routineExercises.map { it.toDomain() },
)

fun RoutineInput.toInsertDto(userId: String, sourceTemplateId: String? = null): RoutineInsertDto = RoutineInsertDto(
    userId = userId,
    name = name.trim(),
    description = description,
    daysPerWeek = daysPerWeek,
    sourceTemplateId = sourceTemplateId,
)

fun NewRoutineExercise.toInsertDto(routineId: String, sortOrder: Int): RoutineExerciseInsertDto = RoutineExerciseInsertDto(
    routineId = routineId,
    exerciseId = exerciseId,
    sortOrder = sortOrder,
    dayNumber = dayNumber,
    targetSets = targetSets,
    targetReps = targetReps,
    restSeconds = restSeconds,
)

fun dayInsertDto(routineId: String, dayNumber: Int, name: String): RoutineDayInsertDto = RoutineDayInsertDto(
    routineId = routineId,
    dayNumber = dayNumber,
    name = name,
)
