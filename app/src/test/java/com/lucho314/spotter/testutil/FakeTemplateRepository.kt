package com.lucho314.spotter.testutil

import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.RoutineTemplateSummary
import com.lucho314.spotter.domain.model.TemplateDetail
import com.lucho314.spotter.domain.model.TemplateGoal
import com.lucho314.spotter.domain.repository.TemplateRepository

/** In-memory [TemplateRepository] test double; call sites can inspect the recorded calls. */
class FakeTemplateRepository : TemplateRepository {

    var templatesResult: AppResult<List<RoutineTemplateSummary>> = AppResult.Success(emptyList())
    var templateDetailResult: AppResult<TemplateDetail> = AppResult.Failure(AppError.NotFound)

    val getTemplatesCalls = mutableListOf<Pair<TemplateGoal?, Int?>>()
    val getTemplateCalls = mutableListOf<String>()

    override suspend fun getTemplates(goal: TemplateGoal?, daysPerWeek: Int?): AppResult<List<RoutineTemplateSummary>> {
        getTemplatesCalls += goal to daysPerWeek
        return templatesResult
    }

    override suspend fun getTemplate(id: String): AppResult<TemplateDetail> {
        getTemplateCalls += id
        return templateDetailResult
    }
}
