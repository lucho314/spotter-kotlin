package com.lucho314.spotter.data.remote.datasource

import com.lucho314.spotter.data.remote.dto.IdDto
import com.lucho314.spotter.data.remote.dto.WorkoutSessionDto
import com.lucho314.spotter.data.remote.dto.WorkoutSessionInsertDto
import com.lucho314.spotter.data.remote.dto.WorkoutSetDto
import com.lucho314.spotter.data.remote.dto.WorkoutSetInsertDto
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val SESSION_COLUMNS = "*, routines(id,name)"
private const val SESSION_DETAIL_COLUMNS = "*, routines(id,name), workout_sets(*, exercises(*, muscle_groups(*)))"

// `exercise_id` is added on top of the plan's column list (section 6): the caller already knows
// it (it's a filter, not a projection), but `WorkoutSetDto.exerciseId` is non-nullable and would
// otherwise fail to decode.
private const val SET_WITH_SESSION_COLUMNS =
    "id, session_id, exercise_id, set_number, weight_kg, reps, is_warmup, completed_at, " +
        "workout_sessions!inner(user_id,status,started_at)"

@Serializable
private data class CompletedAtRow(@SerialName("completed_at") val completedAt: String? = null)

@Singleton
class SupabaseWorkoutRemoteDataSource @Inject constructor(
    private val postgrest: Postgrest,
) : WorkoutRemoteDataSource {

    override suspend fun getSessions(userId: String, from: Long, to: Long): List<WorkoutSessionDto> =
        postgrest.from("workout_sessions").select(Columns.raw(SESSION_COLUMNS)) {
            filter {
                eq("user_id", userId)
                eq("status", "completed")
            }
            order("started_at", Order.DESCENDING)
            range(from, to)
        }.decodeList()

    override suspend fun getSession(sessionId: String): WorkoutSessionDto? =
        postgrest.from("workout_sessions").select(Columns.raw(SESSION_DETAIL_COLUMNS)) {
            filter { eq("id", sessionId) }
        }.decodeSingleOrNull()

    override suspend fun updateSet(setId: String, weightKg: Double, reps: Int): Int =
        postgrest.from("workout_sets").update({
            set("weight_kg", weightKg)
            set("reps", reps)
        }) {
            select()
            filter { eq("id", setId) }
        }.decodeList<IdDto>().size

    override suspend fun insertSet(dto: WorkoutSetInsertDto) {
        postgrest.from("workout_sets").insert(dto)
    }

    override suspend fun deleteSet(setId: String): Int =
        postgrest.from("workout_sets").delete {
            select()
            filter { eq("id", setId) }
        }.decodeList<IdDto>().size

    override suspend fun deleteSession(sessionId: String): Int =
        postgrest.from("workout_sessions").delete {
            select()
            filter { eq("id", sessionId) }
        }.decodeList<IdDto>().size

    override suspend fun getLastSessionSets(userId: String, exerciseId: Int): List<WorkoutSetDto> =
        querySets(userId, exerciseId, limitRows = 200)

    override suspend fun getExerciseSets(userId: String, exerciseId: Int, limit: Int): List<WorkoutSetDto> =
        querySets(userId, exerciseId, limitRows = limit.toLong())

    private suspend fun querySets(userId: String, exerciseId: Int, limitRows: Long): List<WorkoutSetDto> =
        postgrest.from("workout_sets").select(Columns.raw(SET_WITH_SESSION_COLUMNS)) {
            filter {
                eq("workout_sessions.user_id", userId)
                eq("workout_sessions.status", "completed")
                eq("exercise_id", exerciseId)
                eq("is_warmup", false)
            }
            order("completed_at", Order.DESCENDING, nullsFirst = false)
            limit(limitRows)
        }.decodeList()

    override suspend fun getCompletedSince(userId: String, sinceIso: String): Int =
        postgrest.from("workout_sessions").select(Columns.list("id")) {
            count(Count.EXACT)
            limit(0)
            filter {
                eq("user_id", userId)
                eq("status", "completed")
                gte("started_at", sinceIso)
            }
        }.countOrNull()?.toInt() ?: 0

    override suspend fun getLastCompletedAt(userId: String): String? =
        postgrest.from("workout_sessions").select(Columns.list("completed_at")) {
            filter {
                eq("user_id", userId)
                eq("status", "completed")
            }
            // workout_sessions.completed_at is nullable (unlike workout_sets.completed_at):
            // nullsFirst=false keeps a null (shouldn't happen once filtered to status=completed,
            // but the DB doesn't enforce that) from ever sorting ahead of a real timestamp.
            order("completed_at", Order.DESCENDING, nullsFirst = false)
            limit(1)
        }.decodeSingleOrNull<CompletedAtRow>()?.completedAt

    override suspend fun countCompleted(userId: String): Int =
        postgrest.from("workout_sessions").select(Columns.list("id")) {
            count(Count.EXACT)
            limit(0)
            filter {
                eq("user_id", userId)
                eq("status", "completed")
            }
        }.countOrNull()?.toInt() ?: 0

    override suspend fun uploadSession(dto: WorkoutSessionInsertDto) {
        postgrest.from("workout_sessions").upsert(dto) {
            onConflict = "id"
            ignoreDuplicates = true
        }
    }

    override suspend fun uploadSets(dtos: List<WorkoutSetInsertDto>) {
        if (dtos.isEmpty()) return
        postgrest.from("workout_sets").upsert(dtos) {
            onConflict = "id"
            ignoreDuplicates = true
        }
    }
}
