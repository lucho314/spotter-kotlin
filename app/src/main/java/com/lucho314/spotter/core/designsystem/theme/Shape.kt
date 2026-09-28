package com.lucho314.spotter.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner radii tokens: 10 (compact fields/icon chips), 12 (inputs), 16 (cards), 20 (session cards/chips), 24 (buttons/sheets/stat cards). */
object SpotterShapes {
    val CompactField = RoundedCornerShape(10.dp)
    val Input = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(16.dp)
    val SessionCard = RoundedCornerShape(20.dp)
    val Chip = RoundedCornerShape(20.dp)
    val Button = RoundedCornerShape(24.dp)
    val Sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    /** Bigger dashboard cards (stat tiles, "último PR"), matching RN's `borderRadius: 24` there. */
    val DashboardCard = RoundedCornerShape(24.dp)
}

val SpotterMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = SpotterShapes.Input,
    medium = SpotterShapes.Card,
    large = SpotterShapes.SessionCard,
    extraLarge = SpotterShapes.Button,
)
