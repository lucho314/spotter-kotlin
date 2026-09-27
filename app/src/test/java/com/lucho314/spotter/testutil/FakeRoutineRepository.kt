package com.lucho314.spotter.testutil

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.NewRoutineExercise
import com.lucho314.spotter.domain.model.RoutineDetail
import com.lucho314.spotter.domain.model.RoutineExercisePatch
import com.lucho314.spotter.domain.model.RoutineInput
import com.lucho314.spotter.domain.model.RoutineSummary
import com.lucho314.spotter.domain.repository.RoutineRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [RoutineRepository] test double; call sites can inspect the recorded calls. */
class FakeRoutineRepository : RoutineRepository {

    private val routinesFlow = MutableStateFlow<List<RoutineSummary>>(emptyList())
    private val archivedFlow = MutableStateFlow<List<RoutineSummary>>(emptyList())
    private val detailFlows = mutableMapOf<String, MutableStateFlow<RoutineDetail?>>()

    var refreshRoutinesResult: AppResult<Unit> = AppResult.Success(Unit)
    var refreshArchivedResult: AppResult<Unit> = AppResult.Success(Unit)
    var refreshRoutineResult: AppResult<Unit> = AppResult.Success(Unit)
    var createRoutineResult: AppResult<String> = AppResult.Success("new-routine-id")
    var updateRoutineResult: AppResult<Unit> = AppResult.Success(Unit)
    var setArchivedResult: AppResult<Unit> = AppResult.Success(Unit)
    var deleteRoutineResult: AppResult<Unit> = AppResult.Success(Unit)
    var addExerciseResult: AppResult<Unit> = AppResult.Success(Unit)
    var addExercisesResult: AppResult<Unit> = AppResult.Success(Unit)
    var updateRoutineExerciseResult: AppResult<Unit> = AppResult.Success(Unit)
    var removeExerciseResult: AppResult<Unit> = AppResult.Success(Unit)
    var reorderExercisesResult: AppResult<Unit> = AppResult.Success(Unit)
    var addDaysResult: AppResult<Unit> = AppResult.Success(Unit)
    var renameDayResult: AppResult<Unit> = AppResult.Success(Unit)
    var deleteDayResult: AppResult<Unit> = AppResult.Success(Unit)

    /** If set, thrown by [addExercises] instead of returning [addExercisesResult] - used to simulate cancellation. */
    var addExercisesThrows: Throwable? = null

    /** When set, [createRoutine] suspends until it completes, to test in-flight re-entrancy. */
    var createRoutineGate: CompletableDeferred<Unit>? = null

    val setArchivedCalls = mutableListOf<Pair<String, Boolean>>()
    val createRoutineCalls = mutableListOf<Pair<RoutineInput, String?>>()
    val updateRoutineCalls = mutableListOf<Pair<String, RoutineInput>>()
    val updateRoutineExerciseCalls = mutableListOf<Triple<String, String, RoutineExercisePatch>>()
    val addExerciseCalls = mutableListOf<Pair<NewRoutineExercise, Int>>()
    val addExercisesCalls = mutableListOf<List<Pair<NewRoutineExercise, Int>>>()
    val removeExerciseCalls = mutableListOf<Pair<String, String>>()
    val reorderExercisesCalls = mutableListOf<List<String>>()
    val addDaysCalls = mutableListOf<List<Pair<Int, String>>>()
    val deletedRoutineIds = mutableListOf<String>()

    fun setRoutines(routines: List<RoutineSummary>) {
        routinesFlow.value = routines
    }

    fun setArchivedRoutines(routines: List<RoutineSummary>) {
        archivedFlow.value = routines
    }

    fun setRoutineDetail(routineId: String, detail: RoutineDetail?) {
        detailFlowFor(routineId).value = detail
    }

    private fun detailFlowFor(routineId: String) = detailFlows.getOrPut(routineId) { MutableStateFlow(null) }

    var refreshRoutinesCallCount = 0

    override fun observeRoutines(userId: String): Flow<List<RoutineSummary>> = routinesFlow

    override suspend fun refreshRoutines(userId: String): AppResult<Unit> {
        refreshRoutinesCallCount++
        return refreshRoutinesResult
    }

    override fun observeArchivedRoutines(userId: String): Flow<List<RoutineSummary>> = archivedFlow

    override suspend fun refreshArchived(userId: String): AppResult<Unit> = refreshArchivedResult

    override fun observeRoutine(userId: String, routineId: String): Flow<RoutineDetail?> = detailFlowFor(routineId)

    override suspend fun refreshRoutine(userId: String, routineId: String): AppResult<Unit> = refreshRoutineResult

    override suspend fun createRoutine(userId: String, input: RoutineInput, sourceTemplateId: String?): AppResult<String> {
        createRoutineCalls += input to sourceTemplateId
        createRoutineGate?.await()
        return createRoutineResult
    }

    override suspend fun updateRoutine(userId: String, routineId: String, input: RoutineInput): AppResult<Unit> {
        updateRoutineCalls += routineId to input
        return updateRoutineResult
    }

    override suspend fun setArchived(userId: String, routineId: String, archived: Boolean): AppResult<Unit> {
        setArchivedCalls += routineId to archived
        return setArchivedResult
    }

    override suspend fun deleteRoutine(userId: String, routineId: String): AppResult<Unit> {
        if (deleteRoutineResult is AppResult.Success) deletedRoutineIds += routineId
        return deleteRoutineResult
    }

    override suspend fun addExercise(userId: String, routineId: String, input: NewRoutineExercise, sortOrder: Int): AppResult<Unit> {
        addExerciseCalls += input to sortOrder
        return addExerciseResult
    }

    override suspend fun addExercises(userId: String, routineId: String, items: List<Pair<NewRoutineExercise, Int>>): AppResult<Unit> {
        addExercisesThrows?.let { throw it }
        addExercisesCalls += items
        return addExercisesResult
    }

    override suspend fun updateRoutineExercise(
        userId: String,
        routineId: String,
        routineExerciseId: String,
        patch: RoutineExercisePatch,
    ): AppResult<Unit> {
        updateRoutineExerciseCalls += Triple(routineId, routineExerciseId, patch)
        return updateRoutineExerciseResult
    }

    override suspend fun removeExercise(userId: String, routineId: String, routineExerciseId: String): AppResult<Unit> {
        removeExerciseCalls += routineId to routineExerciseId
        return removeExerciseResult
    }

    override suspend fun reorderExercises(userId: String, routineId: String, orderedIds: List<String>): AppResult<Unit> {
        reorderExercisesCalls += orderedIds
        return reorderExercisesResult
    }

    override suspend fun addDays(userId: String, routineId: String, days: List<Pair<Int, String>>): AppResult<Unit> {
        addDaysCalls += days
        return addDaysResult
    }

    override suspend fun renameDay(userId: String, routineId: String, dayId: String, name: String): AppResult<Unit> = renameDayResult

    override suspend fun deleteDay(userId: String, routineId: String, dayId: String): AppResult<Unit> = deleteDayResult
}
