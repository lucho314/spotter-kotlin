package com.lucho314.spotter.data.repository

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.data.remote.dto.SharedRoutineDto
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
}
