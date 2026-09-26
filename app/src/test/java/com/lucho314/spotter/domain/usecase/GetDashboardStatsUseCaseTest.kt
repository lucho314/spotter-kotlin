package com.lucho314.spotter.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.PersonalRecord
import com.lucho314.spotter.testutil.FakeProgressRepository
import com.lucho314.spotter.testutil.FakeTimeProvider
import com.lucho314.spotter.testutil.FakeWorkoutHistoryRepository
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val USER_ID = "user-1"
private val ZONE = ZoneId.of("America/Argentina/Buenos_Aires")

class GetDashboardStatsUseCaseTest {

    private val workoutHistoryRepository = FakeWorkoutHistoryRepository()
    private val progressRepository = FakeProgressRepository()
    private val timeProvider = FakeTimeProvider(zoneId = ZONE)
    private val useCase = GetDashboardStatsUseCase(workoutHistoryRepository, progressRepository, timeProvider)

    @Test
    fun `Sunday 23-59 local uses the previous Monday as the week start`() = runTest {
        timeProvider.instant = ZonedDateTime.of(2026, 1, 18, 23, 59, 0, 0, ZONE).toInstant() // Sunday

        useCase(USER_ID)

        val expectedWeekStart = ZonedDateTime.of(2026, 1, 12, 0, 0, 0, 0, ZONE).toInstant() // previous Monday
        assertThat(workoutHistoryRepository.completedSinceCalls.single().second).isEqualTo(expectedWeekStart)
    }

    @Test
    fun `Monday 00-00 local uses that same day as the week start`() = runTest {
        timeProvider.instant = ZonedDateTime.of(2026, 1, 19, 0, 0, 0, 0, ZONE).toInstant() // Monday

        useCase(USER_ID)

        val expectedWeekStart = ZonedDateTime.of(2026, 1, 19, 0, 0, 0, 0, ZONE).toInstant()
        assertThat(workoutHistoryRepository.completedSinceCalls.single().second).isEqualTo(expectedWeekStart)
    }

    @Test
    fun `a partial failure leaves the other two sections successful`() = runTest {
        workoutHistoryRepository.completedSinceResult = AppResult.Failure(AppError.Network)
        workoutHistoryRepository.lastCompletedAtResult = AppResult.Success(Instant.EPOCH)
        progressRepository.latestPersonalRecordResult = AppResult.Success(null)

        val stats = useCase(USER_ID)

        assertThat(stats.sessionsThisWeek).isEqualTo(AppResult.Failure(AppError.Network))
        assertThat(stats.lastSessionAt).isEqualTo(AppResult.Success(Instant.EPOCH))
        assertThat(stats.latestPr).isEqualTo(AppResult.Success(null))
    }

    @Test
    fun `all successful`() = runTest {
        val pr = PersonalRecord(
            id = "pr-1", exerciseId = 1, exerciseName = "Press", bestWeightKg = 100.0, bestRepsAtWeight = 5,
            estimated1RmKg = 116.0, achievedAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        )
        workoutHistoryRepository.completedSinceResult = AppResult.Success(3)
        workoutHistoryRepository.lastCompletedAtResult = AppResult.Success(Instant.EPOCH)
        progressRepository.latestPersonalRecordResult = AppResult.Success(pr)

        val stats = useCase(USER_ID)

        assertThat(stats.sessionsThisWeek).isEqualTo(AppResult.Success(3))
        assertThat(stats.lastSessionAt).isEqualTo(AppResult.Success(Instant.EPOCH))
        assertThat(stats.latestPr).isEqualTo(AppResult.Success(pr))
    }
}
