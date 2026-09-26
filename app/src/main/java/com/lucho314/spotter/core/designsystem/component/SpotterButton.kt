package com.lucho314.spotter.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.core.designsystem.theme.SpotterShapes

enum class SpotterButtonVariant { Primary, Secondary, Ghost }
enum class SpotterButtonSize(val height: Dp) { Small(40.dp), Medium(48.dp), Large(56.dp) }

private val PrimaryGradient = Brush.horizontalGradient(
    listOf(SpotterColors.Primary, SpotterColors.PrimaryDim),
)
private val DisabledGradient = Brush.horizontalGradient(
    listOf(SpotterColors.SurfaceHigh, SpotterColors.SurfaceHigh),
)

/**
 * Spotter's action button. [SpotterButtonVariant.Primary] uses the brand gradient with a solid
 * background so gradient + ripple compose cleanly, [SpotterButtonVariant.Secondary] is a flat
 * outlined button, [SpotterButtonVariant.Ghost] is text-only.
 */
@Composable
fun SpotterButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SpotterButtonVariant = SpotterButtonVariant.Primary,
    size: SpotterButtonSize = SpotterButtonSize.Medium,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val isEnabled = enabled && !loading
    when (variant) {
        SpotterButtonVariant.Primary -> {
            Box(
                modifier = modifier
                    .height(size.height)
                    .clip(SpotterShapes.Button)
                    .background(if (isEnabled) PrimaryGradient else DisabledGradient),
                contentAlignment = Alignment.Center,
            ) {
                TextButton(
                    onClick = onClick,
                    enabled = isEnabled,
                    modifier = Modifier.matchParentSize(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = SpotterColors.OnPrimary,
                        disabledContentColor = SpotterColors.OnSurfaceVariant,
                    ),
                    shape = SpotterShapes.Button,
                ) {
                    ButtonContent(text = text, loading = loading)
                }
            }
        }

        SpotterButtonVariant.Secondary -> {
            OutlinedButton(
                onClick = onClick,
                enabled = isEnabled,
                modifier = modifier.height(size.height),
                shape = SpotterShapes.Button,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SpotterColors.OnSurface),
            ) {
                ButtonContent(text = text, loading = loading)
            }
        }

        SpotterButtonVariant.Ghost -> {
            TextButton(
                onClick = onClick,
                enabled = isEnabled,
                modifier = modifier.height(size.height),
                colors = ButtonDefaults.textButtonColors(contentColor = SpotterColors.OnSurface),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                ButtonContent(text = text, loading = loading)
            }
        }
    }
}

@Composable
private fun ButtonContent(text: String, loading: Boolean) {
    if (loading) {
        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
    } else {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
