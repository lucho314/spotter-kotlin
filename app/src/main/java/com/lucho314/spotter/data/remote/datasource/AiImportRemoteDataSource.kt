package com.lucho314.spotter.data.remote.datasource

import com.lucho314.spotter.data.remote.dto.ParseRoutineImageRequest
import com.lucho314.spotter.data.remote.dto.ParseRoutineImageResponse

/**
 * Wraps the `parse-routine-image` edge function. The client still sends `user_id` in the body for
 * compatibility with the live function (section 8, B1: it trusts that field instead of the JWT -
 * a backend issue the user decided not to fix for now).
 *
 * **A client-side timeout does not mean the routine wasn't created**: the edge function may have
 * already committed the insert before the response reached the client. The FASE 6 import-from-image
 * UI must surface this (e.g. warn the user and refresh the routines list before letting them retry)
 * instead of assuming a timeout means nothing happened.
 */
interface AiImportRemoteDataSource {
    suspend fun parseRoutineImage(request: ParseRoutineImageRequest): ParseRoutineImageResponse
}
