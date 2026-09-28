package com.lucho314.spotter.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.core.designsystem.theme.SpotterShapes

/** Standard text input, styled with [SpotterShapes.Input] rounded corners. */
@Composable
fun SpotterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    keyboardCapitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        isError = isError,
        supportingText = supportingText?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        singleLine = singleLine,
        shape = SpotterShapes.Input,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = keyboardCapitalization, imeAction = imeAction),
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(),
    )
}

/**
 * Compact, borderless, filled number input: a centered value on a [SpotterColors.SurfaceLow]
 * background, no outline/label - used for the active-workout set rows (`WorkoutScreen`'s weight/
 * reps fields), which are much shorter than [SpotterTextField]'s outlined style in the RN app.
 */
@Composable
fun CompactNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    textColor: Color = SpotterColors.OnSurface,
    keyboardType: KeyboardType = KeyboardType.Number,
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
) {
    val textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, color = textColor, textAlign = TextAlign.Center)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .background(SpotterColors.SurfaceLow, SpotterShapes.CompactField)
            .padding(horizontal = Spacing.sm, vertical = 10.dp),
        enabled = enabled,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(SpotterColors.OnSurface),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = keyboardActions,
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (value.isEmpty() && placeholder != null) {
                    Text(text = placeholder, style = textStyle, color = SpotterColors.OnSurfaceVariant)
                }
                innerTextField()
            }
        },
    )
}
