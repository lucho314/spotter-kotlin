package com.lucho314.spotter.feature.common

import androidx.annotation.StringRes
import com.lucho314.spotter.R
import com.lucho314.spotter.core.common.ValidationReason

/** Maps a [ValidationReason] to a user-facing (Spanish) string resource. */
@StringRes
fun ValidationReason.toMessageRes(): Int = when (this) {
    ValidationReason.NAME_EMPTY -> R.string.validation_name_empty
    ValidationReason.NAME_TOO_LONG -> R.string.validation_name_too_long
    ValidationReason.DESCRIPTION_TOO_LONG -> R.string.validation_description_too_long
    ValidationReason.DAYS_PER_WEEK_RANGE -> R.string.validation_days_per_week_range
    ValidationReason.SETS_RANGE -> R.string.validation_sets_range
    ValidationReason.REPS_RANGE -> R.string.validation_reps_range
    ValidationReason.REST_RANGE -> R.string.validation_rest_range
    ValidationReason.WEIGHT_INVALID -> R.string.validation_weight_invalid
    ValidationReason.WEIGHT_RANGE -> R.string.validation_weight_range
    ValidationReason.BODY_WEIGHT_RANGE -> R.string.validation_body_weight_range
    ValidationReason.HEIGHT_RANGE -> R.string.validation_height_range
    ValidationReason.BIRTH_DATE_INVALID -> R.string.validation_birth_date_invalid
    ValidationReason.SHARE_CODE_INVALID -> R.string.validation_share_code_invalid
    ValidationReason.IMAGE_TOO_LARGE -> R.string.validation_image_too_large
    ValidationReason.IMAGE_UNREADABLE -> R.string.validation_image_unreadable
    ValidationReason.SHARED_ROUTINE_INVALID -> R.string.validation_shared_routine_invalid
    ValidationReason.NO_EXERCISES -> R.string.validation_no_exercises
    ValidationReason.EXERCISE_ALREADY_IN_ROUTINE -> R.string.validation_exercise_already_in_routine
    ValidationReason.DAY_ALREADY_EXISTS -> R.string.validation_day_already_exists
    ValidationReason.DAYS_MAX_REACHED -> R.string.validation_days_max_reached
    ValidationReason.WORKOUT_REPS_RANGE -> R.string.validation_workout_reps_range
    ValidationReason.AGE_RANGE -> R.string.validation_age_range
}
