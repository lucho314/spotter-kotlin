package com.lucho314.spotter.core.common

/** Reasons a domain-level validation can fail. Used by [AppError.Validation]. */
enum class ValidationReason {
    NAME_EMPTY,
    NAME_TOO_LONG,
    DESCRIPTION_TOO_LONG,
    DAYS_PER_WEEK_RANGE,
    SETS_RANGE,
    REPS_RANGE,
    REST_RANGE,
    WEIGHT_INVALID,
    WEIGHT_RANGE,
    BODY_WEIGHT_RANGE,
    HEIGHT_RANGE,
    BIRTH_DATE_INVALID,
    SHARE_CODE_INVALID,
    IMAGE_TOO_LARGE,
    IMAGE_UNREADABLE,
    NO_EXERCISES,
    EXERCISE_ALREADY_IN_ROUTINE,
    DAY_ALREADY_EXISTS,

    /** All 7 weekday slots are already taken (by a real day or by an orphaned exercise's `day_number`). */
    DAYS_MAX_REACHED,

    /** 1..200 ([com.lucho314.spotter.domain.calc.WeightInputParser.parseReps]); [REPS_RANGE] is 1..100, the routines' target reps. */
    WORKOUT_REPS_RANGE,

    /** Age (whole years) must be in 10..100. */
    AGE_RANGE,
}
