@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lucho314.spotter.feature.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.component.ConfirmDialog
import com.lucho314.spotter.core.designsystem.component.ErrorState
import com.lucho314.spotter.core.designsystem.component.LoadingState
import com.lucho314.spotter.core.designsystem.component.SectionHeader
import com.lucho314.spotter.core.designsystem.component.SpotterButton
import com.lucho314.spotter.core.designsystem.component.SpotterButtonVariant
import com.lucho314.spotter.core.designsystem.component.SpotterCard
import com.lucho314.spotter.core.designsystem.component.SpotterTextField
import com.lucho314.spotter.core.designsystem.component.StatCard
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.domain.calc.WeightConverter
import com.lucho314.spotter.domain.model.Profile
import com.lucho314.spotter.domain.model.ProfileGoal
import com.lucho314.spotter.domain.model.ProfileStats
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.domain.usecase.SignOutRisk
import com.lucho314.spotter.feature.common.ObserveAsEvents
import com.lucho314.spotter.feature.common.SpotterDateFormats
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ProfileEvent.Message -> scope.launch { snackbarHostState.showSnackbar(context.getString(event.messageRes)) }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        val loadErrorRes = uiState.loadErrorRes
        when {
            uiState.loading -> LoadingState(modifier = Modifier.padding(padding))
            loadErrorRes != null -> ErrorState(
                message = stringResource(loadErrorRes),
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )

            else -> ProfileContent(
                uiState = uiState,
                onEdit = viewModel::onEdit,
                onEditDismiss = viewModel::onEditDismiss,
                onSaveWeight = viewModel::onSaveWeight,
                onSaveHeight = viewModel::onSaveHeight,
                onSaveBirthDate = viewModel::onSaveBirthDate,
                onSaveGoal = viewModel::onSaveGoal,
                onWeightUnitChange = viewModel::onWeightUnitChange,
                onSignOutClick = viewModel::onSignOutClick,
                onSignOutDismiss = viewModel::onSignOutDismiss,
                onSignOutConfirmed = viewModel::onSignOutConfirmed,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ProfileContent(
    uiState: ProfileUiState,
    onEdit: (ProfileField) -> Unit,
    onEditDismiss: () -> Unit,
    onSaveWeight: (String) -> Unit,
    onSaveHeight: (String) -> Unit,
    onSaveBirthDate: (String) -> Unit,
    onSaveGoal: (ProfileGoal?) -> Unit,
    onWeightUnitChange: (WeightUnit) -> Unit,
    onSignOutClick: () -> Unit,
    onSignOutDismiss: () -> Unit,
    onSignOutConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = uiState.profile
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item { ProfileHeader(uiState.avatarUrl, uiState.displayName ?: stringResource(R.string.onboarding_default_name), uiState.email) }

        item { ProfileStatsRow(uiState.stats, uiState.statsUnavailable) }

        if (profile != null) {
            item {
                SectionHeader(title = stringResource(R.string.profile_physical_title))
            }
            item {
                SpotterCard(modifier = Modifier.fillMaxWidth()) {
                    ProfileFieldRow(
                        label = stringResource(R.string.profile_weight),
                        value = profile.weightKg?.let { "${WeightConverter.format(it, uiState.weightUnit)} ${if (uiState.weightUnit == WeightUnit.KG) stringResource(R.string.unit_kg) else stringResource(R.string.unit_lb)}" }
                            ?: stringResource(R.string.profile_not_set),
                        onClick = { onEdit(ProfileField.WEIGHT) },
                    )
                    ProfileFieldRow(
                        label = stringResource(R.string.profile_height),
                        value = profile.heightCm?.let { stringResource(R.string.profile_height_value, it) } ?: stringResource(R.string.profile_not_set),
                        onClick = { onEdit(ProfileField.HEIGHT) },
                    )
                    ProfileFieldRow(
                        label = stringResource(R.string.profile_age),
                        value = uiState.age?.let { pluralStringResource(R.plurals.profile_age_value, it, it) } ?: stringResource(R.string.profile_not_set),
                        onClick = { onEdit(ProfileField.BIRTH_DATE) },
                    )
                    ProfileFieldRow(
                        label = stringResource(R.string.profile_goal),
                        value = stringResource(profile.goal.labelRes()),
                        onClick = { onEdit(ProfileField.GOAL) },
                    )
                }
            }
        }

        item { SectionHeader(title = stringResource(R.string.profile_settings_title)) }
        item {
            SpotterCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.profile_weight_unit), style = MaterialTheme.typography.bodyLarge, color = SpotterColors.OnSurface)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    WeightUnit.entries.forEachIndexed { index, unit ->
                        SegmentedButton(
                            selected = uiState.weightUnit == unit,
                            onClick = { onWeightUnitChange(unit) },
                            shape = SegmentedButtonDefaults.itemShape(index, count = WeightUnit.entries.size),
                        ) {
                            Text(if (unit == WeightUnit.KG) stringResource(R.string.unit_kg) else stringResource(R.string.unit_lb))
                        }
                    }
                }
            }
        }

        item {
            SpotterButton(
                text = stringResource(R.string.profile_sign_out),
                onClick = onSignOutClick,
                modifier = Modifier.fillMaxWidth(),
                variant = SpotterButtonVariant.Secondary,
                loading = uiState.signingOut,
                enabled = !uiState.signingOut,
            )
        }
    }

    val editing = uiState.editing
    if (profile != null && editing != null) {
        ProfileEditDialogs(
            field = editing,
            profile = profile,
            weightUnit = uiState.weightUnit,
            editErrorRes = uiState.editErrorRes,
            savingEdit = uiState.savingEdit,
            onDismiss = onEditDismiss,
            onSaveWeight = onSaveWeight,
            onSaveHeight = onSaveHeight,
            onSaveBirthDate = onSaveBirthDate,
            onSaveGoal = onSaveGoal,
        )
    }

    val signOutPrompt = uiState.signOutPrompt
    if (signOutPrompt != null) {
        SignOutConfirmDialog(risk = signOutPrompt, onConfirm = onSignOutConfirmed, onDismiss = onSignOutDismiss)
    }
}

@Composable
private fun ProfileHeader(avatarUrl: String?, displayName: String, email: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = stringResource(R.string.profile_avatar_cd),
                modifier = Modifier.size(72.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Row(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(SpotterColors.SurfaceHigh),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = displayName.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, color = SpotterColors.OnSurface)
            }
        }
        Column {
            Text(text = displayName, style = MaterialTheme.typography.headlineSmall, color = SpotterColors.OnSurface)
            if (email != null) {
                Text(text = email, style = MaterialTheme.typography.bodyMedium, color = SpotterColors.OnSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProfileStatsRow(stats: ProfileStats?, unavailable: Boolean) {
    val notAvailable = stringResource(R.string.generic_not_available)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
        StatCard(
            label = stringResource(R.string.profile_stats_sessions),
            value = if (unavailable) notAvailable else stats?.totalSessions?.toString().orEmpty(),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(R.string.profile_stats_prs),
            value = if (unavailable) notAvailable else stats?.totalPrs?.toString().orEmpty(),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(R.string.profile_stats_routines),
            value = if (unavailable) notAvailable else stats?.totalRoutines?.toString().orEmpty(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ProfileFieldRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = SpotterColors.OnSurface)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = SpotterColors.OnSurfaceVariant)
    }
}

@Composable
private fun ProfileEditDialogs(
    field: ProfileField,
    profile: Profile,
    weightUnit: WeightUnit,
    @StringRes editErrorRes: Int?,
    savingEdit: Boolean,
    onDismiss: () -> Unit,
    onSaveWeight: (String) -> Unit,
    onSaveHeight: (String) -> Unit,
    onSaveBirthDate: (String) -> Unit,
    onSaveGoal: (ProfileGoal?) -> Unit,
) {
    when (field) {
        ProfileField.WEIGHT -> EditTextDialog(
            title = stringResource(R.string.profile_edit_weight_title, if (weightUnit == WeightUnit.KG) stringResource(R.string.unit_kg) else stringResource(R.string.unit_lb)),
            initialValue = profile.weightKg?.let { WeightConverter.format(it, weightUnit) }.orEmpty(),
            keyboardType = KeyboardType.Decimal,
            errorRes = editErrorRes,
            saving = savingEdit,
            onDismiss = onDismiss,
            onConfirm = onSaveWeight,
        )

        ProfileField.HEIGHT -> EditTextDialog(
            title = stringResource(R.string.profile_edit_height_title),
            initialValue = profile.heightCm?.toString().orEmpty(),
            keyboardType = KeyboardType.Number,
            errorRes = editErrorRes,
            saving = savingEdit,
            onDismiss = onDismiss,
            onConfirm = onSaveHeight,
        )

        ProfileField.BIRTH_DATE -> EditTextDialog(
            title = stringResource(R.string.profile_edit_birth_date_title),
            initialValue = profile.birthDate?.let { SpotterDateFormats.birthDate(it) }.orEmpty(),
            keyboardType = KeyboardType.Number,
            errorRes = editErrorRes,
            saving = savingEdit,
            placeholder = stringResource(R.string.profile_birth_date_placeholder),
            transformInput = BirthDateInput::format,
            onDismiss = onDismiss,
            onConfirm = onSaveBirthDate,
        )

        ProfileField.GOAL -> GoalEditDialog(current = profile.goal, onDismiss = onDismiss, onConfirm = onSaveGoal)
    }
}

@Composable
private fun EditTextDialog(
    title: String,
    initialValue: String,
    keyboardType: KeyboardType,
    @StringRes errorRes: Int?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    placeholder: String? = null,
    transformInput: (String) -> String = { it },
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            SpotterTextField(
                value = text,
                onValueChange = { text = transformInput(it) },
                placeholder = placeholder,
                keyboardType = keyboardType,
                isError = errorRes != null,
                supportingText = errorRes?.let { stringResource(it) },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = !saving) { Text(stringResource(R.string.generic_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.generic_cancel)) }
        },
    )
}

@Composable
private fun GoalEditDialog(current: ProfileGoal?, onDismiss: () -> Unit, onConfirm: (ProfileGoal?) -> Unit) {
    val options: List<ProfileGoal?> = listOf(null) + ProfileGoal.entries
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_edit_goal_title)) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onConfirm(option) },
                    ) {
                        RadioButton(selected = option == current, onClick = { onConfirm(option) })
                        Text(text = stringResource(option.labelRes()), style = MaterialTheme.typography.bodyLarge, color = SpotterColors.OnSurface)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.generic_cancel)) }
        },
    )
}

@Composable
private fun SignOutConfirmDialog(risk: SignOutRisk, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (risk.isEmpty) {
        ConfirmDialog(
            title = stringResource(R.string.profile_sign_out_confirm_title),
            message = stringResource(R.string.profile_sign_out_confirm_message),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    } else {
        val warnings = buildList {
            if (risk.unsyncedWorkouts > 0) {
                add(pluralStringResource(R.plurals.profile_sign_out_unsynced_warning, risk.unsyncedWorkouts, risk.unsyncedWorkouts))
            }
            if (risk.hasActiveWorkout) {
                add(stringResource(R.string.profile_sign_out_active_workout_warning))
            }
        }
        ConfirmDialog(
            title = stringResource(R.string.profile_sign_out_confirm_title),
            message = warnings.joinToString("\n\n"),
            confirmLabel = stringResource(R.string.profile_sign_out_confirm_anyway),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}
