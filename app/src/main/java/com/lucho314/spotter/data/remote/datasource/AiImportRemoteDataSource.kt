package com.lucho314.spotter.data.remote.datasource

import com.lucho314.spotter.data.remote.dto.ParseRoutineImageRequest
import com.lucho314.spotter.data.remote.dto.ParseRoutineImageResponse

/**
 * Wraps the `parse-routine-image` edge function, which obtains identity from the caller's JWT.
 *
 * **A client-side timeout does not mean the routine wasn't created**: the edge function may have
 * already committed the insert before the response reached the client. The FASE 6 import-from-image
 * UI must surface this (e.g. warn the user and refresh the routines list before letting them retry)
 * instead of assuming a timeout means nothing happened.
 */
interface AiImportRemoteDataSource {
    suspend fun parseRoutineImage(request: ParseRoutineImageRequest): ParseRoutineImageResponse
}
