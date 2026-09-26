package com.lucho314.spotter.data.mapper

import com.lucho314.spotter.data.remote.dto.SharedRoutineDto
import com.lucho314.spotter.domain.model.SharedRoutineContent
import com.lucho314.spotter.domain.model.SharedRoutineDay
import com.lucho314.spotter.domain.model.SharedRoutineExercise

/** Null if the nested `routines(...)` relation wasn't loaded (shouldn't happen given the query in [com.lucho314.spotter.data.remote.datasource.SharingRemoteDataSource]). */
fun SharedRoutineDto.toDomain(): SharedRoutineContent? {
    val routineDto = routine ?: return null
    return SharedRoutineContent(
        routineName = routineDto.name,
        description = routineDto.description,
        daysPerWeek = routineDto.daysPerWeek,
        days = routineDto.routineDays.map { SharedRoutineDay(dayNumber = it.dayNumber, name = it.name) },
        exercises = routineDto.routineExercises.map {
            SharedRoutineExercise(
                exerciseId = it.exerciseId,
                dayNumber = it.dayNumber,
                sortOrder = it.sortOrder,
                targetSets = it.targetSets,
                targetReps = it.targetReps,
                restSeconds = it.restSeconds,
            )
        },
        expiresAt = expiresAt?.toInstant(),
    )
}
