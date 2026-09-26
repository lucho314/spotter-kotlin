package com.lucho314.spotter.feature.profile

import androidx.annotation.StringRes
import com.lucho314.spotter.R
import com.lucho314.spotter.domain.model.ProfileGoal

/** `null` (no goal set, or an unrecognized `rawGoal`) shows as "Sin especificar". */
@StringRes
fun ProfileGoal?.labelRes(): Int = when (this) {
    ProfileGoal.GAIN_MUSCLE -> R.string.profile_goal_gain_muscle
    ProfileGoal.LOSE_WEIGHT -> R.string.profile_goal_lose_weight
    ProfileGoal.MAINTAIN -> R.string.profile_goal_maintain
    ProfileGoal.IMPROVE_PERFORMANCE -> R.string.profile_goal_improve_performance
    ProfileGoal.OTHER -> R.string.profile_goal_other
    null -> R.string.profile_goal_none
}
