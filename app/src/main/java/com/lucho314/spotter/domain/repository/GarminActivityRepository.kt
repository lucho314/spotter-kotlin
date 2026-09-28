package com.lucho314.spotter.domain.repository

import com.lucho314.spotter.domain.model.GarminActivityPlan
import com.lucho314.spotter.domain.model.GarminResult
import com.lucho314.spotter.domain.model.GarminUploadOutcome

interface GarminActivityRepository {
    suspend fun upload(userId: String, plan: GarminActivityPlan): GarminResult<GarminUploadOutcome>
}
