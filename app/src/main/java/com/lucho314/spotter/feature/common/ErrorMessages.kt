package com.lucho314.spotter.feature.common

import androidx.annotation.StringRes
import com.lucho314.spotter.R
import com.lucho314.spotter.core.common.AppError

/** Maps a typed [AppError] to a user-facing (Spanish) string resource. */
@StringRes
fun AppError.toMessageRes(): Int = when (this) {
    AppError.Network -> R.string.error_network
    AppError.Unauthorized -> R.string.error_unauthorized
    AppError.NotFound -> R.string.error_not_found
    is AppError.Conflict -> R.string.error_conflict
    is AppError.Validation -> R.string.error_validation
    is AppError.Server -> R.string.error_server
    is AppError.Unknown -> R.string.error_unknown
}

/**
 * Like [toMessageRes], but for errors raised while signing in: an [AppError.Unauthorized] there
 * means "we couldn't verify your account" (e.g. a rejected nonce), not "your session expired" -
 * there is no prior session to have expired yet.
 */
@StringRes
fun AppError.toLoginMessageRes(): Int = when (this) {
    AppError.Unauthorized -> R.string.error_login_failed
    else -> toMessageRes()
}

/**
 * Like [toMessageRes], but for a [AppError.Validation] picks the specific
 * [com.lucho314.spotter.core.common.ValidationReason] message instead of the generic
 * `error_validation` string - useful for one-shot events (`ActionFailed`) surfacing a validation
 * failure that isn't already shown as an inline field error.
 */
@StringRes
fun AppError.toUserMessageRes(): Int = if (this is AppError.Validation) reason.toMessageRes() else toMessageRes()
