package com.lucho314.spotter.data.remote.datasource

import com.lucho314.spotter.data.remote.dto.RoutineTemplateDto
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

private const val TEMPLATE_COLUMNS = "*, template_days(*, template_day_exercises(*, exercises(*, muscle_groups(*))))"

@Singleton
class SupabaseTemplateRemoteDataSource @Inject constructor(
    private val postgrest: Postgrest,
) : TemplateRemoteDataSource {

    override suspend fun getTemplates(goal: String?, daysPerWeek: Int?): List<RoutineTemplateDto> =
        postgrest.from("routine_templates").select(Columns.raw(TEMPLATE_COLUMNS)) {
            filter {
                eq("is_active", true)
                if (goal != null) eq("goal", goal)
                if (daysPerWeek != null) eq("days_per_week", daysPerWeek)
            }
            order("sort_order", Order.ASCENDING)
        }.decodeList()

    override suspend fun getTemplate(id: String): RoutineTemplateDto? =
        postgrest.from("routine_templates").select(Columns.raw(TEMPLATE_COLUMNS)) {
            filter { eq("id", id) }
        }.decodeSingleOrNull()
}
