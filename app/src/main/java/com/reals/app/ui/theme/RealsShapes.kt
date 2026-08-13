package com.reals.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object RealsRadii {
    val Row = 12.dp
    val Button = 12.dp
    val Card = 14.dp
    val Hero = 16.dp
}

val RealsShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(RealsRadii.Row),
    medium = RoundedCornerShape(RealsRadii.Button),
    large = RoundedCornerShape(RealsRadii.Card),
    extraLarge = RoundedCornerShape(RealsRadii.Hero),
)
