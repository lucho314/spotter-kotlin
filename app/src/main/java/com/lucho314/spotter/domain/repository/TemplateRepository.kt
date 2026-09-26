package com.lucho314.spotter.domain.repository

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.RoutineTemplateSummary
import com.lucho314.spotter.domain.model.TemplateDetail
import com.lucho314.spotter.domain.model.TemplateGoal

interface TemplateRepository {
    suspend fun getTemplates(goal: TemplateGoal? = null, daysPerWeek: Int? = null): AppResult<List<RoutineTemplateSummary>>
    suspend fun getTemplate(id: String): AppResult<TemplateDetail>
}
