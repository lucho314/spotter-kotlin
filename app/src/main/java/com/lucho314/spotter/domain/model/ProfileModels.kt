package com.lucho314.spotter.domain.model

import com.lucho314.spotter.core.common.AppError
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

/**
 * [stats] is null when at least one of the counts that make it up failed to load; [statsError] is
 * then the first such failure, kept only to show a message (the profile itself is still shown).
 */
data class ProfileOverview(
    val profile: Profile,
    val stats: ProfileStats?,
    val statsError: AppError?,
)

/** A single field edit from the profile screen; each variant's empty/null value clears the field. */
sealed interface ProfileEdit {
    /** [text] in [unit]; `""` clears the weight. */
    data class Weight(val text: String, val unit: WeightUnit) : ProfileEdit

    /** `""` clears the height. */
    data class Height(val text: String) : ProfileEdit

    /** "dd/MM/aaaa"; `""` clears the birth date. */
    data class BirthDate(val text: String) : ProfileEdit

    /** `null` clears the goal ("Sin especificar"). */
    data class Goal(val goal: ProfileGoal?) : ProfileEdit
}
