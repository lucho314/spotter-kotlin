package com.lucho314.spotter.testutil

import com.lucho314.spotter.data.remote.datasource.RoutineRemoteDataSource
import com.lucho314.spotter.data.remote.dto.RoutineDayInsertDto
import com.lucho314.spotter.data.remote.dto.RoutineDetailDto
import com.lucho314.spotter.data.remote.dto.RoutineExerciseInsertDto
import com.lucho314.spotter.data.remote.dto.RoutineInsertDto
import com.lucho314.spotter.data.remote.dto.RoutineSummaryDto

/** Throws plain exceptions (per ADR A2): the repository wraps every call with `safeCall`. */
class FakeRoutineRemoteDataSource : RoutineRemoteDataSource {

    var routines: List<RoutineSummaryDto> = emptyList()
    var archivedRoutines: List<RoutineSummaryDto> = emptyList()
    var routineDetail: RoutineDetailDto? = null
    var insertedRoutineId: String = "new-routine-id"

    /** Rows "affected" by the next update/delete call, simulating RLS matching 0 rows. */
    var rowsAffected: Int = 1

    var getRoutinesError: Throwable? = null
    var getRoutineDetailError: Throwable? = null

    /** If set, [updateExerciseSortOrder] throws this on its (1-based) [failOnCallNumber]th call. */
    var updateExerciseSortOrderError: Throwable? = null
    var failOnCallNumber: Int = 1

    val getRoutineDetailCalls = mutableListOf<Pair<String, String>>()
    val updateExerciseSortOrderCalls = mutableListOf<Pair<String, Int>>()
    val updateRoutineExerciseCalls = mutableListOf<Pair<Int?, Int?>>()
    val insertedExercises = mutableListOf<RoutineExerciseInsertDto>()
    val insertedDays = mutableListOf<RoutineDayInsertDto>()
    val deletedRoutineIds = mutableListOf<String>()

    override suspend fun getRoutines(userId: String): List<RoutineSummaryDto> {
        getRoutinesError?.let { throw it }
        return routines
    }

    override suspend fun getArchivedRoutines(userId: String): List<RoutineSummaryDto> = archivedRoutines

    override suspend fun getRoutineDetail(routineId: String, userId: String): RoutineDetailDto? {
        getRoutineDetailCalls += routineId to userId
        getRoutineDetailError?.let { throw it }
        return routineDetail
    }

    override suspend fun insertRoutine(dto: RoutineInsertDto): String = insertedRoutineId

    override suspend fun updateRoutine(routineId: String, userId: String, name: String, description: String?, daysPerWeek: Int?): Int = rowsAffected

    override suspend fun setArchived(routineId: String, userId: String, archived: Boolean): Int = rowsAffected

    override suspend fun deleteRoutine(routineId: String, userId: String): Int {
        if (rowsAffected > 0) deletedRoutineIds += routineId
        return rowsAffected
    }

    override suspend fun insertExercise(dto: RoutineExerciseInsertDto) {
        insertedExercises += dto
    }

    override suspend fun insertExercises(dtos: List<RoutineExerciseInsertDto>) {
        insertedExercises += dtos
    }

    override suspend fun updateRoutineExercise(
        routineExerciseId: String,
        targetSets: Int,
        targetReps: Int,
        restSeconds: Int,
        dayNumber: Int?,
        sortOrder: Int?,
    ): Int {
        updateRoutineExerciseCalls += dayNumber to sortOrder
        return rowsAffected
    }

    override suspend fun deleteRoutineExercise(routineExerciseId: String): Int = rowsAffected

    override suspend fun updateExerciseSortOrder(routineExerciseId: String, sortOrder: Int): Int {
        updateExerciseSortOrderCalls += routineExerciseId to sortOrder
        val error = updateExerciseSortOrderError
        if (error != null && updateExerciseSortOrderCalls.size == failOnCallNumber) throw error
        return rowsAffected
    }

    override suspend fun insertDays(dtos: List<RoutineDayInsertDto>) {
        insertedDays += dtos
    }

    override suspend fun renameDay(dayId: String, name: String): Int = rowsAffected

    override suspend fun deleteDay(dayId: String): Int = rowsAffected
}
