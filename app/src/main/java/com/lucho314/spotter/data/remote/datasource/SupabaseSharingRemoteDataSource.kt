package com.lucho314.spotter.data.remote.datasource

import com.lucho314.spotter.data.remote.dto.SharedRoutineDto
import com.lucho314.spotter.data.remote.dto.SharedRoutineInsertDto
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

private const val SHARED_ROUTINE_DETAIL_COLUMNS =
    "*, routines(*, routine_days(*), routine_exercises(*, exercises(*, muscle_groups(*))))"

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

    override suspend fun getSharedRoutine(code: String): SharedRoutineDto? =
        postgrest.from("shared_routines").select(Columns.raw(SHARED_ROUTINE_DETAIL_COLUMNS)) {
            filter {
                eq("share_code", code)
                eq("is_active", true)
            }
        }.decodeSingleOrNull()
}
