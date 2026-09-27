package com.lucho314.spotter.data.repository

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.data.remote.dto.SharedRoutineBodyDto
import com.lucho314.spotter.data.remote.dto.SharedRoutineDayDto
import com.lucho314.spotter.data.remote.dto.SharedRoutineDto
import com.lucho314.spotter.data.remote.dto.SharedRoutineExerciseDto
import com.lucho314.spotter.data.remote.dto.SharedRoutineImportDto
import com.lucho314.spotter.domain.model.ShareCode
import com.lucho314.spotter.testutil.FakeSharingRemoteDataSource
import com.lucho314.spotter.testutil.FakeTimeProvider
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharingRepositoryImplTest {

    private lateinit var remote: FakeSharingRemoteDataSource
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var repository: SharingRepositoryImpl

    @Before
    fun setUp() {
        remote = FakeSharingRemoteDataSource()
        timeProvider = FakeTimeProvider(instant = Instant.parse("2026-01-15T10:00:00Z"))
        repository = SharingRepositoryImpl(remote, timeProvider)
    }

    private fun share(id: String = "share-1", code: String = "K7MN3QXP", expiresAt: String?) = SharedRoutineDto(
        id = id, routineId = "r1", sharedBy = "user-1", shareCode = code,
        isActive = true, createdAt = "2026-01-01T00:00:00Z", expiresAt = expiresAt,
    )

    @Test
    fun `findActiveShare returns the code when there is no expiry`() = runTest {
        remote.activeShares = listOf(share(expiresAt = null))

        val result = repository.findActiveShare("r1", "user-1")

        assertThat(result).isEqualTo(AppResult.Success(ShareCode.parse("K7MN3QXP")))
    }

    @Test
    fun `findActiveShare returns the code when expires_at is in the future`() = runTest {
        remote.activeShares = listOf(share(expiresAt = "2026-01-16T10:00:00Z"))

        val result = repository.findActiveShare("r1", "user-1")

        assertThat((result as AppResult.Success).value).isNotNull()
    }

    @Test
    fun `findActiveShare discards an expired share instead of returning it as active`() = runTest {
        remote.activeShares = listOf(share(expiresAt = "2026-01-14T10:00:00Z")) // in the past relative to timeProvider

        val result = repository.findActiveShare("r1", "user-1")

        assertThat(result).isEqualTo(AppResult.Success(null))
    }

    @Test
    fun `findActiveShare returns null when there is no active share row`() = runTest {
        remote.activeShares = emptyList()

        val result = repository.findActiveShare("r1", "user-1")

        assertThat(result).isEqualTo(AppResult.Success(null))
    }

    @Test
    fun `findActiveShare skips an expired newest share and falls back to an older still-valid one`() = runTest {
        // Ordered newest-first, as the real query does (RN created one row per "share").
        remote.activeShares = listOf(
            share(id = "newest", code = "EXPIREDXX", expiresAt = "2026-01-14T10:00:00Z"),
            share(id = "older", code = "K7MN3QXP", expiresAt = null),
        )

        val result = repository.findActiveShare("r1", "user-1")

        assertThat(result).isEqualTo(AppResult.Success(ShareCode.parse("K7MN3QXP")))
    }

    private fun sharedRoutineImportDto(
        code: String = "K7MN3QXP",
        isActive: Boolean = true,
        routine: SharedRoutineBodyDto? = SharedRoutineBodyDto(
            name = "Push",
            description = null,
            daysPerWeek = null,
            routineDays = listOf(SharedRoutineDayDto(1, "Lunes")),
            routineExercises = listOf(SharedRoutineExerciseDto(1, 1, 0, 3, 10, 90)),
        ),
    ) = SharedRoutineImportDto(shareCode = code, isActive = isActive, expiresAt = null, routine = routine)

    @Test
    fun `getSharedRoutine maps the lightweight DTO's days and exercises`() = runTest {
        remote.sharedRoutine = sharedRoutineImportDto()

        val result = repository.getSharedRoutine(requireNotNull(ShareCode.parse("K7MN3QXP")))

        val content = (result as AppResult.Success).value
        assertThat(content?.days).hasSize(1)
        assertThat(content?.exercises).hasSize(1)
    }

    @Test
    fun `getSharedRoutine returns null when the returned share_code doesn't match what was asked`() = runTest {
        remote.sharedRoutine = sharedRoutineImportDto(code = "OTHERCODE")

        val result = repository.getSharedRoutine(requireNotNull(ShareCode.parse("K7MN3QXP")))

        assertThat(result).isEqualTo(AppResult.Success(null))
    }

    @Test
    fun `getSharedRoutine returns null when isActive is false`() = runTest {
        remote.sharedRoutine = sharedRoutineImportDto(isActive = false)

        val result = repository.getSharedRoutine(requireNotNull(ShareCode.parse("K7MN3QXP")))

        assertThat(result).isEqualTo(AppResult.Success(null))
    }

    @Test
    fun `getSharedRoutine returns null when the nested routine relation is null`() = runTest {
        remote.sharedRoutine = sharedRoutineImportDto(routine = null)

        val result = repository.getSharedRoutine(requireNotNull(ShareCode.parse("K7MN3QXP")))

        assertThat(result).isEqualTo(AppResult.Success(null))
    }
}
