package com.lucho314.spotter.data.repository

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.testutil.FakeProfileRemoteDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryImplTest {

    private lateinit var remote: FakeProfileRemoteDataSource
    private lateinit var repository: ProfileRepositoryImpl

    @Before
    fun setUp() {
        remote = FakeProfileRemoteDataSource()
        repository = ProfileRepositoryImpl(remote)
    }

    @Test
    fun `updatePhysical with 0 rows affected maps to NotFound`() = runTest {
        remote.updatePhysicalRowsAffected = 0

        val result = repository.updatePhysical("user-1", weightKg = 80.0, heightCm = 180, birthDate = null, goal = null)

        assertThat(result).isEqualTo(AppResult.Failure(AppError.NotFound))
    }

    @Test
    fun `updatePhysical with a positive row count succeeds`() = runTest {
        remote.updatePhysicalRowsAffected = 1

        val result = repository.updatePhysical("user-1", weightKg = 80.0, heightCm = 180, birthDate = null, goal = null)

        assertThat(result).isEqualTo(AppResult.Success(Unit))
    }
}
