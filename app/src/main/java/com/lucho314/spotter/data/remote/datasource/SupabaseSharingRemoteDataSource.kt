package com.lucho314.spotter.data.remote.datasource

import com.lucho314.spotter.data.remote.dto.SharedRoutineDto
import com.lucho314.spotter.data.remote.dto.SharedRoutineImportDto
import com.lucho314.spotter.data.remote.dto.SharedRoutineInsertDto
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

/**
 * No ids of the sharer/routine owner (`shared_by`, `routines.user_id`) and no exercise catalog
 * join - the previous `*, routines(*, routine_days(*), routine_exercises(*, exercises(*,
 * muscle_groups(*)))))` select downloaded all of that for nothing (section 2, finding 4).
 */
private const val SHARED_ROUTINE_IMPORT_COLUMNS =
    "share_code, is_active, expires_at, routines(name, description, days_per_week, " +
        "routine_days(day_number, name), " +
        "routine_exercises(exercise_id, day_number, sort_order, target_sets, target_reps, rest_seconds))"

/** How many candidate active shares to fetch before giving up on finding a non-expired one. */
private const val ACTIVE_SHARES_FETCH_LIMIT = 5L

@Singleton
class SupabaseSharingRemoteDataSource @Inject constructor(
    private val postgrest: Postgrest,
) : SharingRemoteDataSource {

    override suspend fun findActiveShares(routineId: String, userId: String): List<SharedRoutineDto> =
        postgrest.from("shared_routines").select {
            filter {
                eq("routine_id", routineId)
                eq("shared_by", userId)
                eq("is_active", true)
            }
            order("created_at", Order.DESCENDING)
            limit(ACTIVE_SHARES_FETCH_LIMIT)
        }.decodeList()

    override suspend fun insertShare(dto: SharedRoutineInsertDto): SharedRoutineDto =
        postgrest.from("shared_routines").insert(dto) { select() }.decodeSingle()

    override suspend fun getSharedRoutine(code: String): SharedRoutineImportDto? =
        postgrest.from("shared_routines").select(Columns.raw(SHARED_ROUTINE_IMPORT_COLUMNS)) {
            filter {
                eq("share_code", code)
                eq("is_active", true)
            }
        }.decodeSingleOrNull()
}
