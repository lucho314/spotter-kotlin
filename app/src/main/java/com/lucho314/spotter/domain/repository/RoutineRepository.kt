package com.lucho314.spotter.domain.repository

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.NewRoutineExercise
import com.lucho314.spotter.domain.model.RoutineDetail
import com.lucho314.spotter.domain.model.RoutineExercisePatch
import com.lucho314.spotter.domain.model.RoutineInput
import com.lucho314.spotter.domain.model.RoutineSummary
import kotlinx.coroutines.flow.Flow

/**
 * All queries and mutations take [userId] and are scoped to it (section 8, B2: shared routines of
 * other users are otherwise readable, and an update/delete by id alone can silently match 0 rows
 * under RLS while still reporting success).
 */
interface RoutineRepository {
    fun observeRoutines(userId: String): Flow<List<RoutineSummary>>
    suspend fun refreshRoutines(userId: String): AppResult<Unit>

    fun observeArchivedRoutines(userId: String): Flow<List<RoutineSummary>>
    suspend fun refreshArchived(userId: String): AppResult<Unit>

    fun observeRoutine(userId: String, routineId: String): Flow<RoutineDetail?>
    suspend fun refreshRoutine(userId: String, routineId: String): AppResult<Unit>

    suspend fun createRoutine(userId: String, input: RoutineInput, sourceTemplateId: String? = null): AppResult<String>
    suspend fun updateRoutine(userId: String, routineId: String, input: RoutineInput): AppResult<Unit>
    suspend fun setArchived(userId: String, routineId: String, archived: Boolean): AppResult<Unit>

    /** Only meant for compensating a failed multi-step creation (import/adopt template). */
    suspend fun deleteRoutine(userId: String, routineId: String): AppResult<Unit>

    suspend fun addExercise(userId: String, routineId: String, input: NewRoutineExercise, sortOrder: Int): AppResult<Unit>

    /** Batched insert, e.g. when importing/adopting a whole routine at once. */
    suspend fun addExercises(userId: String, routineId: String, items: List<Pair<NewRoutineExercise, Int>>): AppResult<Unit>

    suspend fun updateRoutineExercise(userId: String, routineId: String, routineExerciseId: String, patch: RoutineExercisePatch): AppResult<Unit>
    suspend fun removeExercise(userId: String, routineId: String, routineExerciseId: String): AppResult<Unit>

    /** Updates `sort_order` sequentially; aborts on the first error. */
    suspend fun reorderExercises(userId: String, routineId: String, orderedIds: List<String>): AppResult<Unit>

    suspend fun addDays(userId: String, routineId: String, days: List<Pair<Int, String>>): AppResult<Unit>
    suspend fun renameDay(userId: String, routineId: String, dayId: String, name: String): AppResult<Unit>
    suspend fun deleteDay(userId: String, routineId: String, dayId: String): AppResult<Unit>
}
