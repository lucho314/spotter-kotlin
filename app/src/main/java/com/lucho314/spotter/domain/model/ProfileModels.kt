package com.lucho314.spotter.domain.model

import java.time.LocalDate

enum class ProfileGoal(val apiValue: String) {
    GAIN_MUSCLE("gain_muscle"),
    LOSE_WEIGHT("lose_weight"),
    MAINTAIN("maintain"),
    IMPROVE_PERFORMANCE("improve_performance"),
    OTHER("other"),
    ;

    companion object {
        fun fromApi(value: String?): ProfileGoal? = entries.firstOrNull { it.apiValue == value }
    }
}

data class Profile(
    val id: String,
    val displayName: String,
    val avatarUrl: String?,
    val weightKg: Double?,
    val heightCm: Int?,
    val birthDate: LocalDate?,
    val goal: ProfileGoal?,
    /**
     * Raw `fitness_goal` value as stored (the column is free `text`, not this enum). Kept even when
     * [goal] is null because the value isn't one of [ProfileGoal]'s known api values: passing this
     * back verbatim on an update that isn't meant to change the goal (e.g. just the weight) avoids
     * silently wiping an unrecognized value to `NULL`.
     */
    val rawGoal: String?,
)

data class ProfileStats(
    val totalSessions: Int,
    val totalPrs: Int,
    val totalRoutines: Int,
)
