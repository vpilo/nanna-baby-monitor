package org.vpilo.babymonitor.presentation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Theme {

    object Colors {
        val mainBackground: Color = DarkGreen

        val accent: Color = LightBlue

        val text: Color = White
        val error: Color = LightRed
    }

    object Sizes {
        val Button: Dp = 40.dp
    }

    object Paddings {
        val Tiny: Dp = 4.dp
        val Small: Dp = 8.dp
        val Medium: Dp = 16.dp
        val Large: Dp = 32.dp
        val Huge: Dp = 128.dp
    }
}
