package com.lucho314.spotter.data.remote.datasource

import com.lucho314.spotter.data.remote.dto.PersonalRecordDto

interface ProgressRemoteDataSource {
    /** Ordered by `estimated_1rm` descending. */
    suspend fun getPersonalRecords(userId: String): List<PersonalRecordDto>

    /** Ordered by `updated_at` descending, limit 1. */
    suspend fun getLatestPersonalRecord(userId: String): PersonalRecordDto?
    suspend fun countPersonalRecords(userId: String): Int
}
