package com.lucho314.spotter.data.mapper

import com.lucho314.spotter.data.remote.dto.ExerciseDto
import com.lucho314.spotter.data.remote.dto.MuscleGroupDto
import com.lucho314.spotter.domain.model.Difficulty
import com.lucho314.spotter.domain.model.Equipment
import com.lucho314.spotter.domain.model.Exercise
import com.lucho314.spotter.domain.model.ExerciseCategory
import com.lucho314.spotter.domain.model.MuscleGroup

fun MuscleGroupDto.toDomain(): MuscleGroup = MuscleGroup(id = id, name = name, nameEn = nameEn)

fun ExerciseDto.toDomain(): Exercise = Exercise(
    id = id,
    name = name,
    nameEn = nameEn,
    muscleGroup = muscleGroup?.toDomain(),
    equipment = Equipment.fromApi(equipment),
    imageUrl = imageUrl,
    // `gif_url` in the DB despite the name: most rows are actually .mp4/.webm URLs (section 6).
    mediaUrl = gifUrl,
    secondaryMuscles = secondaryMuscles ?: emptyList(),
    instructions = instructions ?: emptyList(),
    difficulty = Difficulty.fromApi(difficulty),
    category = ExerciseCategory.fromApi(category),
)
