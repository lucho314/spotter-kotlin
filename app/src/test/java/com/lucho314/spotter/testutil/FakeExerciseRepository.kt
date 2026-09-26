package com.lucho314.spotter.testutil

import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.Exercise
import com.lucho314.spotter.domain.model.MuscleGroup
import com.lucho314.spotter.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [ExerciseRepository] test double. */
class FakeExerciseRepository : ExerciseRepository {

    private val catalogFlow = MutableStateFlow<List<Exercise>>(emptyList())
    private val muscleGroupsFlow = MutableStateFlow<List<MuscleGroup>>(emptyList())

    var refreshCatalogResult: AppResult<Unit> = AppResult.Success(Unit)
    var getExerciseResult: AppResult<Exercise> = AppResult.Failure(AppError.NotFound)

    fun setCatalog(exercises: List<Exercise>) {
        catalogFlow.value = exercises
    }

    fun setMuscleGroups(groups: List<MuscleGroup>) {
        muscleGroupsFlow.value = groups
    }

    override fun observeCatalog(): Flow<List<Exercise>> = catalogFlow

    override fun observeMuscleGroups(): Flow<List<MuscleGroup>> = muscleGroupsFlow

    override suspend fun refreshCatalog(force: Boolean): AppResult<Unit> = refreshCatalogResult

    override suspend fun getExercise(id: Int): AppResult<Exercise> =
        getExerciseResult.let { result ->
            when (result) {
                is AppResult.Success -> if (result.value.id == id) result else AppResult.Failure(AppError.NotFound)
                is AppResult.Failure -> result
            }
        }
}
