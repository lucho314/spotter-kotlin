package com.lucho314.spotter.data.repository

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.notNullOrNotFound
import com.lucho314.spotter.core.network.safeCall
import com.lucho314.spotter.data.mapper.toDetail
import com.lucho314.spotter.data.mapper.toSummary
import com.lucho314.spotter.data.remote.datasource.TemplateRemoteDataSource
import com.lucho314.spotter.domain.model.RoutineTemplateSummary
import com.lucho314.spotter.domain.model.TemplateDetail
import com.lucho314.spotter.domain.model.TemplateGoal
import com.lucho314.spotter.domain.repository.TemplateRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Network-only, no local cache (ADR A3): templates change rarely and are read-mostly. */
@Singleton
class TemplateRepositoryImpl @Inject constructor(
    private val remote: TemplateRemoteDataSource,
) : TemplateRepository {

    override suspend fun getTemplates(goal: TemplateGoal?, daysPerWeek: Int?): AppResult<List<RoutineTemplateSummary>> = safeCall {
        remote.getTemplates(goal?.apiValue, daysPerWeek).map { it.toSummary() }
    }

    override suspend fun getTemplate(id: String): AppResult<TemplateDetail> =
        safeCall { remote.getTemplate(id)?.toDetail() }.notNullOrNotFound()
}
