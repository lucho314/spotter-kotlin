package com.lucho314.spotter.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner radii tokens: 12 (inputs), 16 (cards), 20 (session cards/chips), 24 (buttons/sheets). */
object SpotterShapes {
    val Input = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(16.dp)
    val SessionCard = RoundedCornerShape(20.dp)
    val Chip = RoundedCornerShape(20.dp)
    val Button = RoundedCornerShape(24.dp)
    val Sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
}

val SpotterMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = SpotterShapes.Input,
    medium = SpotterShapes.Card,
    large = SpotterShapes.SessionCard,
    extraLarge = SpotterShapes.Button,
)
