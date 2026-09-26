package com.lucho314.spotter.testutil

import com.lucho314.spotter.data.remote.datasource.ExerciseRemoteDataSource
import com.lucho314.spotter.data.remote.dto.ExerciseDto
import com.lucho314.spotter.data.remote.dto.MuscleGroupDto

/** Throws plain exceptions (per ADR A2): the repository wraps every call with `safeCall`. */
class FakeExerciseRemoteDataSource : ExerciseRemoteDataSource {
    var catalog: List<ExerciseDto> = emptyList()
    var muscleGroups: List<MuscleGroupDto> = emptyList()
    var exercise: ExerciseDto? = null
    var getCatalogCallCount = 0

    override suspend fun getCatalog(): List<ExerciseDto> {
        getCatalogCallCount++
        return catalog
    }

    override suspend fun getMuscleGroups(): List<MuscleGroupDto> = muscleGroups

    override suspend fun getExercise(id: Int): ExerciseDto? = exercise
}
