package com.lucho314.spotter.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/** Spotter only ships a dark theme (`userInterfaceStyle: dark` in the RN app). */
private val SpotterDarkColorScheme = darkColorScheme(
    primary = SpotterColors.PrimaryContainer,
    onPrimary = SpotterColors.OnPrimary,
    primaryContainer = SpotterColors.PrimaryContainer,
    onPrimaryContainer = SpotterColors.OnPrimary,
    secondary = SpotterColors.Secondary,
    onSecondary = SpotterColors.SurfaceLowest,
    secondaryContainer = SpotterColors.SecondaryContainer,
    background = SpotterColors.Background,
    onBackground = SpotterColors.OnSurface,
    surface = SpotterColors.Surface,
    onSurface = SpotterColors.OnSurface,
    surfaceContainerLowest = SpotterColors.SurfaceLowest,
    surfaceContainerLow = SpotterColors.SurfaceLow,
    surfaceContainer = SpotterColors.SurfaceContainer,
    surfaceContainerHigh = SpotterColors.SurfaceHigh,
    surfaceContainerHighest = SpotterColors.SurfaceHighest,
    surfaceBright = SpotterColors.SurfaceBright,
    onSurfaceVariant = SpotterColors.OnSurfaceVariant,
    outline = SpotterColors.Outline,
    outlineVariant = SpotterColors.OutlineVariant,
    error = SpotterColors.Error,
)

@Composable
fun SpotterTheme(content: @Composable () -> Unit) {
    // Dark-only by design; isSystemInDarkTheme() is intentionally not consulted.
    MaterialTheme(
        colorScheme = SpotterDarkColorScheme,
        typography = SpotterTypography,
        shapes = SpotterMaterialShapes,
        content = content,
    )
}
