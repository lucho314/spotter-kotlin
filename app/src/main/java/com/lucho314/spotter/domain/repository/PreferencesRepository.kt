package com.lucho314.spotter.domain.repository

import com.lucho314.spotter.domain.model.WeightUnit
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    val weightUnit: Flow<WeightUnit>
    suspend fun setWeightUnit(unit: WeightUnit)

    /** Onboarding is shown once per user id (a device can be shared across accounts). */
    fun isOnboardingDone(userId: String): Flow<Boolean>
    suspend fun setOnboardingDone(userId: String)

    /** Clears any per-user preference on sign-out; the weight unit (device-scoped) is kept. */
    suspend fun clearUserScoped()
}
