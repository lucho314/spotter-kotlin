package com.lucho314.spotter.data.remote.datasource

import com.lucho314.spotter.data.remote.dto.ExerciseDto
import com.lucho314.spotter.data.remote.dto.MuscleGroupDto
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import javax.inject.Inject
import javax.inject.Singleton

private const val EXERCISE_COLUMNS = "*, muscle_groups(*)"

@Singleton
class SupabaseExerciseRemoteDataSource @Inject constructor(
    private val postgrest: Postgrest,
) : ExerciseRemoteDataSource {

    override suspend fun getCatalog(): List<ExerciseDto> =
        postgrest.from("exercises").select(Columns.raw(EXERCISE_COLUMNS)).decodeList()

    override suspend fun getMuscleGroups(): List<MuscleGroupDto> =
        postgrest.from("muscle_groups").select().decodeList()

    override suspend fun getExercise(id: Int): ExerciseDto? =
        postgrest.from("exercises").select(Columns.raw(EXERCISE_COLUMNS)) {
            filter { eq("id", id) }
        }.decodeSingleOrNull()
}
