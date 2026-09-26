package com.lucho314.spotter.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.Profile
import com.lucho314.spotter.testutil.FakeProfileRepository
import com.lucho314.spotter.testutil.FakeProgressRepository
import com.lucho314.spotter.testutil.FakeWorkoutHistoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val USER_ID = "user-1"

class GetProfileOverviewUseCaseTest {

    private val profileRepository = FakeProfileRepository()
    private val workoutHistoryRepository = FakeWorkoutHistoryRepository()
    private val progressRepository = FakeProgressRepository()
    private val useCase = GetProfileOverviewUseCase(profileRepository, workoutHistoryRepository, progressRepository)

    private val profile = Profile(
        id = USER_ID, displayName = "Ada", avatarUrl = null, weightKg = 70.0, heightCm = 170,
        birthDate = null, goal = null, rawGoal = null,
    )

    @Test
    fun `a failed profile fails the whole thing`() = runTest {
        profileRepository.profileResult = AppResult.Failure(AppError.NotFound)

        val result = useCase(USER_ID)

        assertThat(result).isEqualTo(AppResult.Failure(AppError.NotFound))
    }

    @Test
    fun `a failed count leaves stats null with the error kept`() = runTest {
        profileRepository.profileResult = AppResult.Success(profile)
        workoutHistoryRepository.countCompletedResult = AppResult.Failure(AppError.Network)

        val result = useCase(USER_ID) as AppResult.Success

        assertThat(result.value.profile).isEqualTo(profile)
        assertThat(result.value.stats).isNull()
        assertThat(result.value.statsError).isEqualTo(AppError.Network)
    }

    @Test
    fun `all successful returns the combined stats`() = runTest {
        profileRepository.profileResult = AppResult.Success(profile)
        workoutHistoryRepository.countCompletedResult = AppResult.Success(10)
        progressRepository.countPersonalRecordsResult = AppResult.Success(3)
        profileRepository.countActiveRoutinesResult = AppResult.Success(2)

        val result = useCase(USER_ID) as AppResult.Success

        assertThat(result.value.profile).isEqualTo(profile)
        assertThat(result.value.statsError).isNull()
        assertThat(result.value.stats?.totalSessions).isEqualTo(10)
        assertThat(result.value.stats?.totalPrs).isEqualTo(3)
        assertThat(result.value.stats?.totalRoutines).isEqualTo(2)
    }
}
