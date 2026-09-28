package com.lucho314.spotter.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.GarminUploadStatus
import com.lucho314.spotter.testutil.FakeGarminUploadRepository
import com.lucho314.spotter.testutil.FakeGarminUploadScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val USER_ID = "user-1"

class RetryFailedGarminUploadsUseCaseTest {

    private val garminUploadRepository = FakeGarminUploadRepository()
    private val garminUploadScheduler = FakeGarminUploadScheduler()
    private val useCase = RetryFailedGarminUploadsUseCase(garminUploadRepository, garminUploadScheduler)

    @Test
    fun `resetting at least one row schedules the worker`() = runTest {
        garminUploadRepository.seed("w1", USER_ID, GarminUploadStatus.FAILED)

        useCase(USER_ID)

        assertThat(garminUploadRepository.rows["w1"]!!.status).isEqualTo(GarminUploadStatus.PENDING)
        assertThat(garminUploadScheduler.scheduleCallCount).isEqualTo(1)
    }

    @Test
    fun `nothing to reset does not schedule`() = runTest {
        useCase(USER_ID)

        assertThat(garminUploadScheduler.scheduleCallCount).isEqualTo(0)
    }
}
