package com.lucho314.spotter.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/** Raw color tokens, taken from `E:\Spotter\constants\colors.ts`. Dark theme only. */
object SpotterColors {
    val Background = Color(0xFF0E0E0E)
    val Surface = Color(0xFF0E0E0E)
    val SurfaceLowest = Color(0xFF000000)
    val SurfaceLow = Color(0xFF131313)
    val SurfaceContainer = Color(0xFF1A1A1A)
    val SurfaceHigh = Color(0xFF20201F)
    val SurfaceHighest = Color(0xFF262626)
    val SurfaceBright = Color(0xFF2C2C2C)

    val Primary = Color(0xFFF4FFC6)
    val PrimaryContainer = Color(0xFFD1FC00)
    val PrimaryDim = Color(0xFFC7EF00)
    val OnPrimary = Color(0xFF546600)

    val Secondary = Color(0xFF00E3FD)
    val SecondaryContainer = Color(0xFF006875)

    val Error = Color(0xFFFF7351)

    val OnSurface = Color(0xFFFFFFFF)
    val OnSurfaceVariant = Color(0xFFADAAAA)
    val Outline = Color(0xFF767575)
    val OutlineVariant = Color(0xFF484847)

    // Template goal accents.
    val GoalStrength = Color(0xFFFF7351)
    val GoalHypertrophy = Secondary
    val GoalFatLoss = Color(0xFFFCDC43)
    val GoalGeneral = PrimaryContainer
}
