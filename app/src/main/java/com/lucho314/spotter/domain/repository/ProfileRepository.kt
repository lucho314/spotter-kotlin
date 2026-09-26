package com.lucho314.spotter.domain.repository

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.Profile
import java.time.LocalDate

interface ProfileRepository {
    suspend fun getProfile(userId: String): AppResult<Profile>

    /**
     * @param goal the raw `fitness_goal` value to store (`null` clears it). The column is free
     * `text`: callers not changing the goal should pass through [Profile.rawGoal] verbatim rather
     * than round-tripping it through [com.lucho314.spotter.domain.model.ProfileGoal], which would
     * silently drop any value it doesn't recognize.
     */
    suspend fun updatePhysical(
        userId: String,
        weightKg: Double?,
        heightCm: Int?,
        birthDate: LocalDate?,
        goal: String?,
    ): AppResult<Unit>

    suspend fun countActiveRoutines(userId: String): AppResult<Int>
}
