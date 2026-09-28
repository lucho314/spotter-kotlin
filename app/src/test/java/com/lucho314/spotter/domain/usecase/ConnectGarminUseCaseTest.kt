package com.lucho314.spotter.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.GarminError
import com.lucho314.spotter.domain.model.GarminLoginResult
import com.lucho314.spotter.domain.model.GarminResult
import com.lucho314.spotter.testutil.FakeGarminAccountRepository
import com.lucho314.spotter.testutil.FakeGarminUploadScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val USER_ID = "user-1"

class ConnectGarminUseCaseTest {

    private val garminAccountRepository = FakeGarminAccountRepository()
    private val garminUploadScheduler = FakeGarminUploadScheduler()
    private val useCase = ConnectGarminUseCase(garminAccountRepository, garminUploadScheduler)

    @Test
    fun `a successful direct login schedules the worker`() = runTest {
        garminAccountRepository.loginResult = GarminResult.Success(GarminLoginResult.Connected)

        val result = useCase.login(USER_ID, "  a@b.com  ", "pw")

        assertThat(result).isEqualTo(GarminResult.Success(GarminLoginResult.Connected))
        assertThat(garminAccountRepository.loginCalls.single().second).isEqualTo("a@b.com") // trimmed
        assertThat(garminUploadScheduler.scheduleCallCount).isEqualTo(1)
    }

    @Test
    fun `MFA required does not schedule yet`() = runTest {
        garminAccountRepository.loginResult = GarminResult.Success(GarminLoginResult.MfaRequired("challenge-1", "email"))

        useCase.login(USER_ID, "a@b.com", "pw")

        assertThat(garminUploadScheduler.scheduleCallCount).isEqualTo(0)
    }

    @Test
    fun `a login failure does not schedule`() = runTest {
        garminAccountRepository.loginResult = GarminResult.Failure(GarminError.InvalidCredentials)

        useCase.login(USER_ID, "a@b.com", "pw")

        assertThat(garminUploadScheduler.scheduleCallCount).isEqualTo(0)
    }

    @Test
    fun `a successful MFA verification schedules the worker`() = runTest {
        garminAccountRepository.verifyResult = GarminResult.Success(Unit)

        val result = useCase.verifyMfa(USER_ID, "challenge-1", " 123456 ")

        assertThat(result).isEqualTo(GarminResult.Success(Unit))
        assertThat(garminAccountRepository.verifyMfaCalls.single().third).isEqualTo("123456") // trimmed
        assertThat(garminUploadScheduler.scheduleCallCount).isEqualTo(1)
    }

    @Test
    fun `a failed MFA verification does not schedule`() = runTest {
        garminAccountRepository.verifyResult = GarminResult.Failure(GarminError.InvalidMfaCode)

        useCase.verifyMfa(USER_ID, "challenge-1", "000000")

        assertThat(garminUploadScheduler.scheduleCallCount).isEqualTo(0)
    }
}
