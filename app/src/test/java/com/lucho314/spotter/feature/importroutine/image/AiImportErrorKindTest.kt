package com.lucho314.spotter.feature.importroutine.image

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.ValidationReason
import com.lucho314.spotter.domain.model.AiImportErrorCodes
import org.junit.Test

class AiImportErrorKindTest {

    @Test
    fun `maps every AppError branch`() {
        assertThat(AppError.Network.toAiImportErrorKind()).isEqualTo(AiImportErrorKind.CONNECTION_LOST_MAYBE_CREATED)
        assertThat(AppError.Unauthorized.toAiImportErrorKind()).isEqualTo(AiImportErrorKind.SESSION_EXPIRED)
        assertThat(AppError.Validation(ValidationReason.IMAGE_TOO_LARGE).toAiImportErrorKind()).isEqualTo(AiImportErrorKind.IMAGE_TOO_LARGE)
        assertThat(AppError.Validation(ValidationReason.IMAGE_UNREADABLE).toAiImportErrorKind()).isEqualTo(AiImportErrorKind.IMAGE_UNREADABLE)
        assertThat(AppError.Validation(ValidationReason.NAME_EMPTY).toAiImportErrorKind()).isEqualTo(AiImportErrorKind.GENERIC)
        assertThat(AppError.Server(AiImportErrorCodes.TIMEOUT).toAiImportErrorKind()).isEqualTo(AiImportErrorKind.TIMEOUT_MAYBE_CREATED)
        assertThat(AppError.Server(AiImportErrorCodes.REJECTED).toAiImportErrorKind()).isEqualTo(AiImportErrorKind.NOT_RECOGNIZED)
        assertThat(AppError.Server(AiImportErrorCodes.RATE_LIMITED).toAiImportErrorKind()).isEqualTo(AiImportErrorKind.RATE_LIMITED)
        assertThat(AppError.Server(AiImportErrorCodes.FAILED).toAiImportErrorKind()).isEqualTo(AiImportErrorKind.GENERIC)
        assertThat(AppError.Server(AiImportErrorCodes.INVALID_RESPONSE).toAiImportErrorKind()).isEqualTo(AiImportErrorKind.GENERIC)
        assertThat(AppError.NotFound.toAiImportErrorKind()).isEqualTo(AiImportErrorKind.GENERIC)
        assertThat(AppError.Conflict().toAiImportErrorKind()).isEqualTo(AiImportErrorKind.GENERIC)
        assertThat(AppError.Unknown().toAiImportErrorKind()).isEqualTo(AiImportErrorKind.GENERIC)
    }

    @Test
    fun `suggestsCheckingRoutines is only true for the two maybe-created kinds`() {
        val expectedTrue = setOf(AiImportErrorKind.TIMEOUT_MAYBE_CREATED, AiImportErrorKind.CONNECTION_LOST_MAYBE_CREATED)
        AiImportErrorKind.entries.forEach { kind ->
            assertThat(kind.suggestsCheckingRoutines).isEqualTo(kind in expectedTrue)
        }
    }
}
