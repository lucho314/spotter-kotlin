package com.lucho314.spotter.testutil

import com.lucho314.spotter.data.remote.datasource.AiImportRemoteDataSource
import com.lucho314.spotter.data.remote.dto.ParseRoutineImageRequest
import com.lucho314.spotter.data.remote.dto.ParseRoutineImageResponse

class FakeAiImportRemoteDataSource : AiImportRemoteDataSource {
    var response: ParseRoutineImageResponse = ParseRoutineImageResponse(routineId = "r1", routineName = "Push")
    var error: Throwable? = null
    val requests = mutableListOf<ParseRoutineImageRequest>()

    override suspend fun parseRoutineImage(request: ParseRoutineImageRequest): ParseRoutineImageResponse {
        requests += request
        error?.let { throw it }
        return response
    }
}
