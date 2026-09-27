package com.lucho314.spotter.feature.importroutine.image

import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.ValidationReason
import com.lucho314.spotter.domain.model.AiImportErrorCodes

/**
 * User-facing classification of an AI-import failure. Kept free of `R`/Android imports so it's
 * pure and testable outside Robolectric/instrumentation; [ImportImageScreen] maps each value to a
 * string resource.
 */
enum class AiImportErrorKind {
    OFFLINE,
    IMAGE_TOO_LARGE,
    IMAGE_UNREADABLE,
    TIMEOUT_MAYBE_CREATED,
    CONNECTION_LOST_MAYBE_CREATED,
    NOT_RECOGNIZED,
    SESSION_EXPIRED,
    GENERIC,
    ;

    val suggestsCheckingRoutines: Boolean get() = this == TIMEOUT_MAYBE_CREATED || this == CONNECTION_LOST_MAYBE_CREATED
}

fun AppError.toAiImportErrorKind(): AiImportErrorKind = when (this) {
    AppError.Network -> AiImportErrorKind.CONNECTION_LOST_MAYBE_CREATED
    AppError.Unauthorized -> AiImportErrorKind.SESSION_EXPIRED
    is AppError.Validation -> when (reason) {
        ValidationReason.IMAGE_TOO_LARGE -> AiImportErrorKind.IMAGE_TOO_LARGE
        ValidationReason.IMAGE_UNREADABLE -> AiImportErrorKind.IMAGE_UNREADABLE
        else -> AiImportErrorKind.GENERIC
    }
    is AppError.Server -> when (code) {
        AiImportErrorCodes.TIMEOUT -> AiImportErrorKind.TIMEOUT_MAYBE_CREATED
        AiImportErrorCodes.REJECTED -> AiImportErrorKind.NOT_RECOGNIZED
        else -> AiImportErrorKind.GENERIC
    }
    AppError.NotFound, is AppError.Conflict, is AppError.Unknown -> AiImportErrorKind.GENERIC
}
