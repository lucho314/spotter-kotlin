package com.lucho314.spotter.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.ValidationReason
import com.lucho314.spotter.domain.model.AI_IMPORT_MAX_BASE64_LENGTH
import com.lucho314.spotter.domain.model.AiImportErrorCodes
import com.lucho314.spotter.domain.model.AiImportedRoutine
import com.lucho314.spotter.domain.model.RoutineDetail
import com.lucho314.spotter.testutil.FakeAiImportRepository
import com.lucho314.spotter.testutil.FakeRoutineRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ImportRoutineFromImageUseCaseTest {

    private val userId = "user-1"

    private fun detail(id: String = "550e8400-e29b-41d4-a716-446655440000", name: String = "Push Day") = RoutineDetail(
        id = id, userId = userId, name = name, description = null, daysPerWeek = null,
        isArchived = false, days = emptyList(), exercises = emptyList(),
    )

    private fun useCase(aiRepo: FakeAiImportRepository, routineRepo: FakeRoutineRepository) = ImportRoutineFromImageUseCase(aiRepo, routineRepo)

    @Test
    fun `empty base64 is rejected without calling the repository`() = runTest {
        val aiRepo = FakeAiImportRepository()
        val routineRepo = FakeRoutineRepository()

        val result = useCase(aiRepo, routineRepo)(userId, "")

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Validation(ValidationReason.IMAGE_UNREADABLE)))
        assertThat(aiRepo.calls).isEmpty()
    }

    @Test
    fun `base64 over the max length is rejected without calling the repository, exactly at the max calls it`() = runTest {
        val aiRepo = FakeAiImportRepository()
        val routineRepo = FakeRoutineRepository()
        val tooLong = "a".repeat(AI_IMPORT_MAX_BASE64_LENGTH + 1)

        val result = useCase(aiRepo, routineRepo)(userId, tooLong)

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Validation(ValidationReason.IMAGE_TOO_LARGE)))
        assertThat(aiRepo.calls).isEmpty()

        val exactly = "a".repeat(AI_IMPORT_MAX_BASE64_LENGTH)
        routineRepo.setRoutineDetail(detail().id, detail())
        useCase(aiRepo, routineRepo)(userId, exactly)
        assertThat(aiRepo.calls).hasSize(1)
    }

    @Test
    fun `userId is passed through unchanged`() = runTest {
        val aiRepo = FakeAiImportRepository()
        val routineRepo = FakeRoutineRepository()
        routineRepo.setRoutineDetail(detail().id, detail())

        useCase(aiRepo, routineRepo)(userId, "b64")

        assertThat(aiRepo.calls.single().first).isEqualTo(userId)
    }

    @Test
    fun `success with a refreshed detail uses the database name, not the response's`() = runTest {
        val aiRepo = FakeAiImportRepository().apply { result = AppResult.Success(AiImportedRoutine(detail().id, "Response Name")) }
        val routineRepo = FakeRoutineRepository()
        routineRepo.setRoutineDetail(detail().id, detail(name = "DB Name"))

        val result = useCase(aiRepo, routineRepo)(userId, "b64")

        assertThat(result).isEqualTo(AppResult.Success(AiImportedRoutine(detail().id, "DB Name")))
        assertThat(routineRepo.refreshRoutinesCallCount).isEqualTo(1)
    }

    @Test
    fun `refresh succeeds but the routine isn't found for the user`() = runTest {
        val aiRepo = FakeAiImportRepository().apply { result = AppResult.Success(AiImportedRoutine(detail().id, "Response Name")) }
        val routineRepo = FakeRoutineRepository()
        // No detail set -> observeRoutine emits null.

        val result = useCase(aiRepo, routineRepo)(userId, "b64")

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Server(AiImportErrorCodes.INVALID_RESPONSE)))
    }

    @Test
    fun `a non-UUID routineId is rejected without calling refreshRoutine, but still refreshes the list`() = runTest {
        val aiRepo = FakeAiImportRepository().apply { result = AppResult.Success(AiImportedRoutine("not-a-uuid", "Name")) }
        val routineRepo = FakeRoutineRepository()

        val result = useCase(aiRepo, routineRepo)(userId, "b64")

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Server(AiImportErrorCodes.INVALID_RESPONSE)))
        assertThat(routineRepo.refreshRoutinesCallCount).isEqualTo(1)
    }

    @Test
    fun `a Network failure on refreshRoutine still reports success with the sanitized response name`() = runTest {
        val aiRepo = FakeAiImportRepository().apply { result = AppResult.Success(AiImportedRoutine(detail().id, "Response Name")) }
        val routineRepo = FakeRoutineRepository().apply { refreshRoutineResult = AppResult.Failure(AppError.Network) }

        val result = useCase(aiRepo, routineRepo)(userId, "b64")

        assertThat(result).isEqualTo(AppResult.Success(AiImportedRoutine(detail().id, "Response Name")))
    }

    @Test
    fun `a Server TIMEOUT failure refreshes the routines list once and leaves the error unchanged`() = runTest {
        val aiRepo = FakeAiImportRepository().apply { result = AppResult.Failure(AppError.Server(AiImportErrorCodes.TIMEOUT)) }
        val routineRepo = FakeRoutineRepository()

        val result = useCase(aiRepo, routineRepo)(userId, "b64")

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Server(AiImportErrorCodes.TIMEOUT)))
        assertThat(routineRepo.refreshRoutinesCallCount).isEqualTo(1)
    }

    @Test
    fun `an Unauthorized failure doesn't refresh the list`() = runTest {
        val aiRepo = FakeAiImportRepository().apply { result = AppResult.Failure(AppError.Unauthorized) }
        val routineRepo = FakeRoutineRepository()

        val result = useCase(aiRepo, routineRepo)(userId, "b64")

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Unauthorized))
        assertThat(routineRepo.refreshRoutinesCallCount).isEqualTo(0)
    }
}
