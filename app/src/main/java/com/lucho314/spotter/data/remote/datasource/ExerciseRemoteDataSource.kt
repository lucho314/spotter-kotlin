package com.lucho314.spotter.data.remote.datasource

import com.lucho314.spotter.data.remote.dto.ExerciseDto
import com.lucho314.spotter.data.remote.dto.MuscleGroupDto

/**
 * Wraps every Postgrest call for the exercise catalog. A plain interface (ADR A2) so
 * [com.lucho314.spotter.data.repository.ExerciseRepositoryImpl] can be unit tested with a fake
 * that just throws plain exceptions instead of `safeCall`-wrapped results.
 */
interface ExerciseRemoteDataSource {
    suspend fun getCatalog(): List<ExerciseDto>
    suspend fun getMuscleGroups(): List<MuscleGroupDto>
    suspend fun getExercise(id: Int): ExerciseDto?
}
