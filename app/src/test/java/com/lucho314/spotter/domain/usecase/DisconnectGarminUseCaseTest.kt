package com.lucho314.spotter.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.GarminUploadStatus
import com.lucho314.spotter.testutil.FakeGarminAccountRepository
import com.lucho314.spotter.testutil.FakeGarminUploadRepository
import com.lucho314.spotter.testutil.FakeGarminUploadScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val USER_ID = "user-1"

class DisconnectGarminUseCaseTest {

    private val garminUploadScheduler = FakeGarminUploadScheduler()
    private val garminUploadRepository = FakeGarminUploadRepository()
    private val garminAccountRepository = FakeGarminAccountRepository()
    private val useCase = DisconnectGarminUseCase(garminUploadScheduler, garminUploadRepository, garminAccountRepository)

    @Test
    fun `cancels the worker, deletes not-yet-uploaded rows, keeps UPLOADED ones, and disconnects the account`() = runTest {
        garminUploadRepository.seed("w1", USER_ID, GarminUploadStatus.PENDING)
        garminUploadRepository.seed("w2", USER_ID, GarminUploadStatus.FAILED)
        garminUploadRepository.seed("w3", USER_ID, GarminUploadStatus.UPLOADED)

        useCase(USER_ID)

        assertThat(garminUploadScheduler.cancelCallCount).isEqualTo(1)
        assertThat(garminUploadRepository.rows.keys).containsExactly("w3")
        assertThat(garminAccountRepository.disconnectCallCount).isEqualTo(1)
    }

    @Test
    fun `a failure deleting rows does not prevent disconnect from running`() = runTest {
        garminUploadRepository.deleteNotUploadedError = RuntimeException("boom")

        useCase(USER_ID)

        assertThat(garminAccountRepository.disconnectCallCount).isEqualTo(1)
    }
}
