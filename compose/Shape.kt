package com.mowazze3.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

val MowazzeShapes = Shapes(
    small = RoundedCornerShape(Radius.small),
    medium = RoundedCornerShape(Radius.medium),   // buttons, inputs
    large = RoundedCornerShape(Radius.large),     // cards
    extraLarge = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet), // bottom sheet
)
