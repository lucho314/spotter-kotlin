package com.lucho314.spotter.data.mapper

import com.lucho314.spotter.data.remote.dto.RoutineTemplateDto
import com.lucho314.spotter.data.remote.dto.TemplateDayDto
import com.lucho314.spotter.data.remote.dto.TemplateDayExerciseDto
import com.lucho314.spotter.domain.model.Difficulty
import com.lucho314.spotter.domain.model.RoutineTemplateSummary
import com.lucho314.spotter.domain.model.TemplateDay
import com.lucho314.spotter.domain.model.TemplateDetail
import com.lucho314.spotter.domain.model.TemplateExercise
import com.lucho314.spotter.domain.model.TemplateGoal

/** UI copy is Spanish (section 3): prefer the `_es` columns, falling back to the English ones. */
fun RoutineTemplateDto.toSummary(): RoutineTemplateSummary = RoutineTemplateSummary(
    id = id,
    name = nameEs.ifBlank { name },
    description = descriptionEs?.takeIf { it.isNotBlank() } ?: description,
    goal = TemplateGoal.fromApi(goal) ?: TemplateGoal.GENERAL,
    difficulty = Difficulty.fromApi(difficulty) ?: Difficulty.BEGINNER,
    daysPerWeek = daysPerWeek,
)

fun RoutineTemplateDto.toDetail(): TemplateDetail = TemplateDetail(
    summary = toSummary(),
    days = days.sortedBy { it.dayNumber }.map { it.toDomain() },
)

fun TemplateDayDto.toDomain(): TemplateDay = TemplateDay(
    id = id,
    dayNumber = dayNumber,
    name = nameEs.ifBlank { name },
    description = description,
    exercises = exercises.sortedBy { it.sortOrder }.map { it.toDomain() },
)

fun TemplateDayExerciseDto.toDomain(): TemplateExercise = TemplateExercise(
    exerciseId = exerciseId,
    exercise = exercise?.toDomain(),
    sortOrder = sortOrder,
    targetSets = targetSets,
    targetReps = targetReps,
    restSeconds = restSeconds,
)
