package com.lucho314.spotter.feature.profile

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import com.lucho314.spotter.testutil.FakeAuthRepository
import com.lucho314.spotter.testutil.FakeRestTimerAlarmScheduler
import com.lucho314.spotter.testutil.FakeSyncScheduler
import com.lucho314.spotter.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val user = AuthUser(id = "user-1", email = "a@b.com", displayName = "Ada", avatarUrl = null)

    /**
     * Review carry-over: full Room/preferences cleanup is `SignOutUseCase`'s job (phase 5), but the
     * rest-timer alarm and the sync worker must be cancelled here already - otherwise a stale alarm
     * could fire "Descanso terminado" for the next account on this device, and the sync worker
     * could try to upload an outbox it doesn't own once phase 5's `SignOutUseCase` clears Room.
     */
    @Test
    fun `signing out cancels the rest-timer alarm and the sync worker`() = runTest {
        val alarmScheduler = FakeRestTimerAlarmScheduler()
        val syncScheduler = FakeSyncScheduler()
        val vm = ProfileViewModel(FakeAuthRepository(AuthState.SignedIn(user)), alarmScheduler, syncScheduler)

        vm.onSignOutConfirmed()

        assertThat(alarmScheduler.cancelCallCount).isEqualTo(1)
        assertThat(syncScheduler.cancelCallCount).isEqualTo(1)
    }
}
