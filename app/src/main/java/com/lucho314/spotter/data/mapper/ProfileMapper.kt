package com.lucho314.spotter.data.mapper

import com.lucho314.spotter.data.remote.dto.ProfileDto
import com.lucho314.spotter.domain.model.Profile
import com.lucho314.spotter.domain.model.ProfileGoal

fun ProfileDto.toDomain(): Profile = Profile(
    id = id,
    displayName = displayName,
    avatarUrl = avatarUrl,
    weightKg = weightKg,
    heightCm = heightCm,
    birthDate = birthDate?.toLocalDate(),
    goal = ProfileGoal.fromApi(fitnessGoal),
    rawGoal = fitnessGoal,
)
