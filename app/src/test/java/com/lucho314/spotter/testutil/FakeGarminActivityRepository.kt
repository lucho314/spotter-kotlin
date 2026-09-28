package com.lucho314.spotter.testutil

import com.lucho314.spotter.domain.model.GarminActivityPlan
import com.lucho314.spotter.domain.model.GarminResult
import com.lucho314.spotter.domain.model.GarminUploadOutcome
import com.lucho314.spotter.domain.repository.GarminActivityRepository

class FakeGarminActivityRepository : GarminActivityRepository {

    var defaultResult: GarminResult<GarminUploadOutcome> = GarminResult.Success(GarminUploadOutcome.Uploaded(1L, 2L))
    private val queuedResults = ArrayDeque<GarminResult<GarminUploadOutcome>>()

    val plans = mutableListOf<GarminActivityPlan>()
    val uploadCalls = mutableListOf<String>()

    fun enqueueResult(result: GarminResult<GarminUploadOutcome>) {
        queuedResults.addLast(result)
    }

    override suspend fun upload(userId: String, plan: GarminActivityPlan): GarminResult<GarminUploadOutcome> {
        uploadCalls += userId
        plans += plan
        return if (queuedResults.isNotEmpty()) queuedResults.removeFirst() else defaultResult
    }
}
