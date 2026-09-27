package com.lucho314.spotter.testutil

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.repository.ImageRepository
import kotlinx.coroutines.CompletableDeferred

/** In-memory [ImageRepository] test double. */
class FakeImageRepository : ImageRepository {

    var captureUriResult: AppResult<String> = AppResult.Success("content://camera/capture-1.jpg")
    var encodeResult: AppResult<String> = AppResult.Success("base64-jpeg")
    val encodeCalls = mutableListOf<String>()
    var clearCallCount = 0

    /** When set, [encodeForAiImport] suspends until it completes, to test in-flight re-entrancy. */
    var encodeGate: CompletableDeferred<Unit>? = null

    override suspend fun createCameraCaptureUri(): AppResult<String> = captureUriResult

    override suspend fun clearCameraCaptures() {
        clearCallCount++
    }

    override suspend fun encodeForAiImport(uri: String): AppResult<String> {
        encodeCalls += uri
        encodeGate?.await()
        return encodeResult
    }
}
