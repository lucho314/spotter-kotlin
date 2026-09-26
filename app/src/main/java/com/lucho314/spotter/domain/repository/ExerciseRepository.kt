package com.lucho314.spotter.domain.repository

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.Exercise
import com.lucho314.spotter.domain.model.MuscleGroup
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    fun observeCatalog(): Flow<List<Exercise>>
    fun observeMuscleGroups(): Flow<List<MuscleGroup>>

    /** Refreshes the cached catalog from the network if older than a 24h TTL, or always if [force]. */
    suspend fun refreshCatalog(force: Boolean = false): AppResult<Unit>

    /** Looks the exercise up in the cached catalog first, falling back to the network. */
    suspend fun getExercise(id: Int): AppResult<Exercise>
}
