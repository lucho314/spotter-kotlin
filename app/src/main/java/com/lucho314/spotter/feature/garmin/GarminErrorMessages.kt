package com.lucho314.spotter.feature.garmin

import androidx.annotation.StringRes
import com.lucho314.spotter.R
import com.lucho314.spotter.domain.model.GarminError

/** Maps a typed [GarminError] to a user-facing (Spanish) string resource - see the plan's error matrix. */
@StringRes
fun GarminError.toMessageRes(): Int = when (this) {
    GarminError.Network -> R.string.error_network
    GarminError.InvalidCredentials -> R.string.garmin_error_invalid_credentials
    GarminError.InvalidMfaCode -> R.string.garmin_error_invalid_mfa
    GarminError.MfaSessionExpired -> R.string.garmin_error_mfa_expired
    GarminError.CaptchaRequired -> R.string.garmin_error_captcha
    GarminError.Blocked -> R.string.garmin_error_blocked
    GarminError.RateLimited -> R.string.garmin_error_rate_limited
    GarminError.ReauthRequired -> R.string.garmin_error_reauth
    GarminError.NotConnected -> R.string.garmin_error_not_connected
    is GarminError.InvalidFile -> R.string.garmin_error_service_changed
    is GarminError.Server -> R.string.garmin_error_server
    is GarminError.ServiceChanged -> R.string.garmin_error_service_changed
    is GarminError.Unknown -> R.string.error_unknown
}
