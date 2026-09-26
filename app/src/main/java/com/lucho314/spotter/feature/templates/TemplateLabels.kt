package com.lucho314.spotter.feature.templates

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.domain.model.TemplateGoal

/** Spanish label, accent color and icon for a template's [TemplateGoal] (section 9.11 of the migration plan). */
@StringRes
fun TemplateGoal.labelRes(): Int = when (this) {
    TemplateGoal.STRENGTH -> R.string.template_goal_strength
    TemplateGoal.HYPERTROPHY -> R.string.template_goal_hypertrophy
    TemplateGoal.FAT_LOSS -> R.string.template_goal_fat_loss
    TemplateGoal.GENERAL -> R.string.template_goal_general
}

fun TemplateGoal.accentColor(): Color = when (this) {
    TemplateGoal.STRENGTH -> SpotterColors.GoalStrength
    TemplateGoal.HYPERTROPHY -> SpotterColors.GoalHypertrophy
    TemplateGoal.FAT_LOSS -> SpotterColors.GoalFatLoss
    TemplateGoal.GENERAL -> SpotterColors.GoalGeneral
}

fun TemplateGoal.icon(): ImageVector = when (this) {
    TemplateGoal.STRENGTH -> Icons.Outlined.FitnessCenter
    TemplateGoal.HYPERTROPHY -> Icons.Outlined.SelfImprovement
    TemplateGoal.FAT_LOSS -> Icons.Outlined.LocalFireDepartment
    TemplateGoal.GENERAL -> Icons.Outlined.Star
}
