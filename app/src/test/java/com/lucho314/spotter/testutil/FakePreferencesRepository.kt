package com.lucho314.spotter.testutil

import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow

class FakePreferencesRepository : PreferencesRepository {

    private val weightUnitFlow = MutableStateFlow(WeightUnit.KG)
    override val weightUnit = weightUnitFlow

    private val onboardingDone = mutableMapOf<String, MutableStateFlow<Boolean>>()

    override suspend fun setWeightUnit(unit: WeightUnit) {
        weightUnitFlow.value = unit
    }

    override fun isOnboardingDone(userId: String) = onboardingFlowFor(userId)

    override suspend fun setOnboardingDone(userId: String) {
        onboardingFlowFor(userId).value = true
    }

    override suspend fun clearUserScoped() {
        onboardingDone.clear()
    }

    private fun onboardingFlowFor(userId: String): MutableStateFlow<Boolean> =
        onboardingDone.getOrPut(userId) { MutableStateFlow(false) }
}
