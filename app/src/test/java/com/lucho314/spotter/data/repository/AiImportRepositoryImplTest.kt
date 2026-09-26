package com.lucho314.spotter.data.repository

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.data.remote.dto.ParseRoutineImageResponse
import com.lucho314.spotter.testutil.FakeAiImportRemoteDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiImportRepositoryImplTest {

    private lateinit var remote: FakeAiImportRemoteDataSource
    private lateinit var repository: AiImportRepositoryImpl

    @Before
    fun setUp() {
        remote = FakeAiImportRemoteDataSource()
        repository = AiImportRepositoryImpl(remote)
    }

    @Test
    fun `a successful response returns the routine id and name`() = runTest {
        remote.response = ParseRoutineImageResponse(routineId = "r1", routineName = "Push Day")

        val result = repository.importFromImage("user-1", "base64...")

        assertThat(result).isEqualTo(AppResult.Success("r1" to "Push Day"))
    }

    @Test
    fun `a 200 response with an error field maps to a Server failure`() = runTest {
        remote.response = ParseRoutineImageResponse(error = "no exercises detected")

        val result = repository.importFromImage("user-1", "base64...")

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Server(message = "no exercises detected")))
    }

    @Test
    fun `a 200 response missing routine_id or routine_name maps to a Server failure`() = runTest {
        remote.response = ParseRoutineImageResponse(routineId = null, routineName = null)

        val result = repository.importFromImage("user-1", "base64...")

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error).isInstanceOf(AppError.Server::class.java)
    }

    @Test
    fun `user_id is still sent in the request body for compatibility with the live function`() = runTest {
        repository.importFromImage("user-1", "base64...")

        assertThat(remote.requests.single().userId).isEqualTo("user-1")
    }
}
