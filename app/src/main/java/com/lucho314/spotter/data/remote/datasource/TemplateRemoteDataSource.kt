package com.lucho314.spotter.data.remote.datasource

import com.lucho314.spotter.data.remote.dto.RoutineTemplateDto

interface TemplateRemoteDataSource {
    /** @param goal raw `goal` column value (e.g. "strength"), or null for no filter. */
    suspend fun getTemplates(goal: String?, daysPerWeek: Int?): List<RoutineTemplateDto>
    suspend fun getTemplate(id: String): RoutineTemplateDto?
}
