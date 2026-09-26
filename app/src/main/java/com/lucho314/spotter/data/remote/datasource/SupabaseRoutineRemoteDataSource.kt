package com.lucho314.spotter.data.remote.datasource

import com.lucho314.spotter.data.remote.dto.IdDto
import com.lucho314.spotter.data.remote.dto.RoutineDayInsertDto
import com.lucho314.spotter.data.remote.dto.RoutineDetailDto
import com.lucho314.spotter.data.remote.dto.RoutineExerciseInsertDto
import com.lucho314.spotter.data.remote.dto.RoutineInsertDto
import com.lucho314.spotter.data.remote.dto.RoutineSummaryDto
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

private const val ROUTINE_SUMMARY_COLUMNS = "*, routine_exercises(id), routine_days(*)"
private const val ROUTINE_DETAIL_COLUMNS = "*, routine_days(*), routine_exercises(*, exercises(*, muscle_groups(*)))"

@Singleton
class SupabaseRoutineRemoteDataSource @Inject constructor(
    private val postgrest: Postgrest,
) : RoutineRemoteDataSource {

    override suspend fun getRoutines(userId: String): List<RoutineSummaryDto> =
        postgrest.from("routines").select(Columns.raw(ROUTINE_SUMMARY_COLUMNS)) {
            filter {
                eq("user_id", userId)
                eq("is_archived", false)
            }
            order("created_at", Order.DESCENDING)
        }.decodeList()

    override suspend fun getArchivedRoutines(userId: String): List<RoutineSummaryDto> =
        postgrest.from("routines").select(Columns.raw(ROUTINE_SUMMARY_COLUMNS)) {
            filter {
                eq("user_id", userId)
                eq("is_archived", true)
            }
            order("created_at", Order.DESCENDING)
        }.decodeList()

    override suspend fun getRoutineDetail(routineId: String, userId: String): RoutineDetailDto? =
        postgrest.from("routines").select(Columns.raw(ROUTINE_DETAIL_COLUMNS)) {
            filter {
                eq("id", routineId)
                eq("user_id", userId)
            }
        }.decodeSingleOrNull()

    override suspend fun insertRoutine(dto: RoutineInsertDto): String =
        postgrest.from("routines").insert(dto) { select() }.decodeSingle<IdDto>().id

    override suspend fun updateRoutine(routineId: String, userId: String, name: String, description: String?, daysPerWeek: Int?): Int =
        postgrest.from("routines").update({
            set("name", name)
            set("description", description)
            set("days_per_week", daysPerWeek)
        }) {
            select()
            filter {
                eq("id", routineId)
                eq("user_id", userId)
            }
        }.decodeList<IdDto>().size

    override suspend fun setArchived(routineId: String, userId: String, archived: Boolean): Int =
        postgrest.from("routines").update({
            set("is_archived", archived)
        }) {
            select()
            filter {
                eq("id", routineId)
                eq("user_id", userId)
            }
        }.decodeList<IdDto>().size

    override suspend fun deleteRoutine(routineId: String, userId: String): Int =
        postgrest.from("routines").delete {
            select()
            filter {
                eq("id", routineId)
                eq("user_id", userId)
            }
        }.decodeList<IdDto>().size

    override suspend fun insertExercise(dto: RoutineExerciseInsertDto) {
        postgrest.from("routine_exercises").insert(dto)
    }

    override suspend fun insertExercises(dtos: List<RoutineExerciseInsertDto>) {
        if (dtos.isEmpty()) return
        postgrest.from("routine_exercises").insert(dtos)
    }

    override suspend fun updateRoutineExercise(
        routineExerciseId: String,
        targetSets: Int,
        targetReps: Int,
        restSeconds: Int,
        dayNumber: Int?,
        sortOrder: Int?,
    ): Int = postgrest.from("routine_exercises").update({
        set("target_sets", targetSets)
        set("target_reps", targetReps)
        set("rest_seconds", restSeconds)
        // `day_number`/`sort_order` are NOT NULL live: only include them when actually changing,
        // so "unchanged" never risks encoding an explicit JSON null for either column.
        if (dayNumber != null) set("day_number", dayNumber)
        if (sortOrder != null) set("sort_order", sortOrder)
    }) {
        select()
        filter { eq("id", routineExerciseId) }
    }.decodeList<IdDto>().size

    override suspend fun deleteRoutineExercise(routineExerciseId: String): Int =
        postgrest.from("routine_exercises").delete {
            select()
            filter { eq("id", routineExerciseId) }
        }.decodeList<IdDto>().size

    override suspend fun updateExerciseSortOrder(routineExerciseId: String, sortOrder: Int): Int =
        postgrest.from("routine_exercises").update({
            set("sort_order", sortOrder)
        }) {
            select()
            filter { eq("id", routineExerciseId) }
        }.decodeList<IdDto>().size

    override suspend fun insertDays(dtos: List<RoutineDayInsertDto>) {
        if (dtos.isEmpty()) return
        postgrest.from("routine_days").insert(dtos)
    }

    override suspend fun renameDay(dayId: String, name: String): Int =
        postgrest.from("routine_days").update({
            set("name", name)
        }) {
            select()
            filter { eq("id", dayId) }
        }.decodeList<IdDto>().size

    override suspend fun deleteDay(dayId: String): Int =
        postgrest.from("routine_days").delete {
            select()
            filter { eq("id", dayId) }
        }.decodeList<IdDto>().size
}
