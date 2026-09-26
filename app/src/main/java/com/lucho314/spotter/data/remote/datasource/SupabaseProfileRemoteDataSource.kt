package com.lucho314.spotter.data.remote.datasource

import com.lucho314.spotter.data.remote.dto.ProfileDto
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseProfileRemoteDataSource @Inject constructor(
    private val postgrest: Postgrest,
) : ProfileRemoteDataSource {

    override suspend fun getProfile(userId: String): ProfileDto? =
        postgrest.from("profiles").select {
            filter { eq("id", userId) }
        }.decodeSingleOrNull()

    override suspend fun updatePhysical(userId: String, weightKg: Double?, heightCm: Int?, birthDate: String?, goal: String?) {
        postgrest.from("profiles").update({
            set("weight_kg", weightKg)
            set("height_cm", heightCm)
            set("birth_date", birthDate)
            set("fitness_goal", goal)
        }) {
            filter { eq("id", userId) }
        }
    }

    override suspend fun countActiveRoutines(userId: String): Int =
        postgrest.from("routines").select(Columns.list("id")) {
            count(Count.EXACT)
            limit(0)
            filter {
                eq("user_id", userId)
                eq("is_archived", false)
            }
        }.countOrNull()?.toInt() ?: 0
}
