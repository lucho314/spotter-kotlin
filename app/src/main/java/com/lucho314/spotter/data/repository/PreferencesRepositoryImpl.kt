package com.lucho314.spotter.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lucho314.spotter.core.datastore.USER_PREFS_DATASTORE
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.domain.repository.PreferencesRepository
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

private val WEIGHT_UNIT_KEY = stringPreferencesKey("weight_unit")
private fun onboardingKey(userId: String) = booleanPreferencesKey("onboarding_done_$userId")

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    @Named(USER_PREFS_DATASTORE) private val store: DataStore<Preferences>,
) : PreferencesRepository {

    override val weightUnit = store.data.map { prefs ->
        prefs[WEIGHT_UNIT_KEY]?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() } ?: WeightUnit.KG
    }

    override suspend fun setWeightUnit(unit: WeightUnit) {
        store.edit { it[WEIGHT_UNIT_KEY] = unit.name }
    }

    override fun isOnboardingDone(userId: String) = store.data.map { prefs ->
        prefs[onboardingKey(userId)] ?: false
    }

    override suspend fun setOnboardingDone(userId: String) {
        store.edit { it[onboardingKey(userId)] = true }
    }

    override suspend fun clearUserScoped() {
        store.edit { prefs ->
            val keysToRemove = prefs.asMap().keys.filter { it.name.startsWith("onboarding_done_") }
            keysToRemove.forEach { prefs.remove(it) }
        }
    }
}
