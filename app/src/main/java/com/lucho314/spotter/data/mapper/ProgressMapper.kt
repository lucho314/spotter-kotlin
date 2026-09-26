package com.lucho314.spotter.data.mapper

import com.lucho314.spotter.data.remote.dto.PersonalRecordDto
import com.lucho314.spotter.domain.model.PersonalRecord

fun PersonalRecordDto.toDomain(): PersonalRecord = PersonalRecord(
    id = id,
    exerciseId = exerciseId,
    exerciseName = exercise?.name,
    bestWeightKg = bestWeightKg,
    bestRepsAtWeight = bestRepsAtWeight,
    estimated1RmKg = estimated1Rm,
    achievedAt = achievedAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
)
