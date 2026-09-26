package com.lucho314.spotter.data.remote.datasource

import com.lucho314.spotter.data.remote.dto.RoutineDayInsertDto
import com.lucho314.spotter.data.remote.dto.RoutineDetailDto
import com.lucho314.spotter.data.remote.dto.RoutineExerciseInsertDto
import com.lucho314.spotter.data.remote.dto.RoutineInsertDto
import com.lucho314.spotter.data.remote.dto.RoutineSummaryDto

/**
 * Wraps every Postgrest call for routines/routine_days/routine_exercises. Every list *and detail*
 * query filters by `user_id` (section 8, B2: shared routines of other users are otherwise
 * readable, and RLS alone would let an update/delete on a foreign row match 0 rows and still
 * report "success").
 *
 * Every single-row update/delete below returns the number of rows actually affected: under RLS, a
 * mutation for a row the caller doesn't own matches 0 rows and Postgrest still reports 2xx, so the
 * repository must check this count itself (mirrors `WorkoutHistoryRepositoryImpl.deleteSession`).
 */
interface RoutineRemoteDataSource {
    suspend fun getRoutines(userId: String): List<RoutineSummaryDto>
    suspend fun getArchivedRoutines(userId: String): List<RoutineSummaryDto>
    suspend fun getRoutineDetail(routineId: String, userId: String): RoutineDetailDto?

    /** @return the new routine's id. */
    suspend fun insertRoutine(dto: RoutineInsertDto): String
    suspend fun updateRoutine(routineId: String, userId: String, name: String, description: String?, daysPerWeek: Int?): Int
    suspend fun setArchived(routineId: String, userId: String, archived: Boolean): Int
    suspend fun deleteRoutine(routineId: String, userId: String): Int

    suspend fun insertExercise(dto: RoutineExerciseInsertDto)
    suspend fun insertExercises(dtos: List<RoutineExerciseInsertDto>)

    /** [dayNumber]/[sortOrder] `null` means "don't change it" - never encoded as an explicit JSON null. */
    suspend fun updateRoutineExercise(routineExerciseId: String, targetSets: Int, targetReps: Int, restSeconds: Int, dayNumber: Int?, sortOrder: Int?): Int
    suspend fun deleteRoutineExercise(routineExerciseId: String): Int
    suspend fun updateExerciseSortOrder(routineExerciseId: String, sortOrder: Int): Int

    suspend fun insertDays(dtos: List<RoutineDayInsertDto>)
    suspend fun renameDay(dayId: String, name: String): Int
    suspend fun deleteDay(dayId: String): Int
}
