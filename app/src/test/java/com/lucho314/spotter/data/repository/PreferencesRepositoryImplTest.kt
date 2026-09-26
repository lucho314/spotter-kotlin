package com.lucho314.spotter.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.WeightUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreferencesRepositoryImplTest {

    private lateinit var repository: PreferencesRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = TestScope(UnconfinedTestDispatcher())
        val store = PreferenceDataStoreFactory.create(scope = scope) {
            context.preferencesDataStoreFile("test_user_prefs_${System.nanoTime()}")
        }
        repository = PreferencesRepositoryImpl(store)
    }

    @Test
    fun `weightUnit defaults to KG`() = runTest {
        assertThat(repository.weightUnit.first()).isEqualTo(WeightUnit.KG)
    }

    @Test
    fun `setWeightUnit persists and is observable`() = runTest {
        repository.setWeightUnit(WeightUnit.LB)

        assertThat(repository.weightUnit.first()).isEqualTo(WeightUnit.LB)
    }

    @Test
    fun `onboarding is tracked per user id`() = runTest {
        assertThat(repository.isOnboardingDone("user-1").first()).isFalse()

        repository.setOnboardingDone("user-1")

        assertThat(repository.isOnboardingDone("user-1").first()).isTrue()
        assertThat(repository.isOnboardingDone("user-2").first()).isFalse()
    }

    @Test
    fun `clearUserScoped removes onboarding flags but keeps the weight unit`() = runTest {
        repository.setWeightUnit(WeightUnit.LB)
        repository.setOnboardingDone("user-1")

        repository.clearUserScoped()

        assertThat(repository.isOnboardingDone("user-1").first()).isFalse()
        assertThat(repository.weightUnit.first()).isEqualTo(WeightUnit.LB)
    }
}
