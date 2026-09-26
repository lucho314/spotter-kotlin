package com.lucho314.spotter.data.remote.datasource

import com.lucho314.spotter.data.remote.dto.PersonalRecordDto
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

private const val PERSONAL_RECORD_COLUMNS = "*, exercises(*, muscle_groups(*))"

@Singleton
class SupabaseProgressRemoteDataSource @Inject constructor(
    private val postgrest: Postgrest,
) : ProgressRemoteDataSource {

    override suspend fun getPersonalRecords(userId: String): List<PersonalRecordDto> =
        postgrest.from("personal_records").select(Columns.raw(PERSONAL_RECORD_COLUMNS)) {
            filter { eq("user_id", userId) }
            order("estimated_1rm", Order.DESCENDING)
        }.decodeList()

    override suspend fun getLatestPersonalRecord(userId: String): PersonalRecordDto? =
        postgrest.from("personal_records").select(Columns.raw(PERSONAL_RECORD_COLUMNS)) {
            filter { eq("user_id", userId) }
            order("updated_at", Order.DESCENDING)
            limit(1)
        }.decodeSingleOrNull()

    override suspend fun countPersonalRecords(userId: String): Int =
        postgrest.from("personal_records").select(Columns.list("id")) {
            count(Count.EXACT)
            limit(0)
            filter { eq("user_id", userId) }
        }.countOrNull()?.toInt() ?: 0
}
