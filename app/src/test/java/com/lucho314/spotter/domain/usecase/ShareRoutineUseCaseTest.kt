package com.lucho314.spotter.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.RoutineDetail
import com.lucho314.spotter.domain.model.ShareCode
import com.lucho314.spotter.testutil.FakeSharingRepository
import java.security.SecureRandom
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ShareRoutineUseCaseTest {

    private val userId = "user-1"

    private fun routine(ownerId: String = userId) = RoutineDetail(
        id = "r1", userId = ownerId, name = "Push", description = null, daysPerWeek = null,
        isArchived = false, days = emptyList(), exercises = emptyList(),
    )

    private fun useCase(repository: FakeSharingRepository) = ShareRoutineUseCase(repository, SecureRandom())

    @Test
    fun `reuses the active share without creating a new one`() = runTest {
        val repository = FakeSharingRepository()
        val existingCode = requireNotNull(ShareCode.parse("K7MN3QXP"))
        repository.findActiveShareResult = AppResult.Success(existingCode)

        val result = useCase(repository)(userId, routine())

        assertThat(result).isEqualTo(AppResult.Success(existingCode))
        assertThat(repository.createShareCodes).isEmpty()
    }

    @Test
    fun `no active share creates one with a valid-format code`() = runTest {
        val repository = FakeSharingRepository()

        val result = useCase(repository)(userId, routine())

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat(repository.createShareCodes).hasSize(1)
        assertThat(ShareCode.parse(repository.createShareCodes.single().value)).isNotNull()
    }

    @Test
    fun `retries on Conflict up to success, with distinct codes`() = runTest {
        val repository = FakeSharingRepository()
        repository.createShareResults += AppResult.Failure(AppError.Conflict())
        repository.createShareResults += AppResult.Failure(AppError.Conflict())
        // Third call falls through to "no more queued results" -> Success(code).

        val result = useCase(repository)(userId, routine())

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat(repository.createShareCodes).hasSize(3)
        assertThat(repository.createShareCodes.toSet()).hasSize(3)
    }

    @Test
    fun `exhausting all attempts on Conflict returns the last failure`() = runTest {
        val repository = FakeSharingRepository()
        repeat(3) { repository.createShareResults += AppResult.Failure(AppError.Conflict()) }

        val result = useCase(repository)(userId, routine())

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error).isInstanceOf(AppError.Conflict::class.java)
        assertThat(repository.createShareCodes).hasSize(3)
    }

    @Test
    fun `a non-Conflict failure on create stops after a single attempt`() = runTest {
        val repository = FakeSharingRepository()
        repository.createShareResults += AppResult.Failure(AppError.Network)

        val result = useCase(repository)(userId, routine())

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Network))
        assertThat(repository.createShareCodes).hasSize(1)
    }

    @Test
    fun `findActiveShare failure is propagated without creating anything`() = runTest {
        val repository = FakeSharingRepository()
        repository.findActiveShareResult = AppResult.Failure(AppError.Network)

        val result = useCase(repository)(userId, routine())

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Network))
        assertThat(repository.createShareCodes).isEmpty()
    }

    @Test
    fun `sharing another user's routine is rejected without any repository calls`() = runTest {
        val repository = FakeSharingRepository()

        val result = useCase(repository)(userId, routine(ownerId = "someone-else"))

        assertThat(result).isEqualTo(AppResult.Failure(AppError.NotFound))
        assertThat(repository.findActiveShareCallCount).isEqualTo(0)
        assertThat(repository.createShareCodes).isEmpty()
    }
}
