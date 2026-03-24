package org.vpilo.babymonitor.presentation

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val LightColorScheme = lightColorScheme(
    primary = DarkGreen,
    onPrimary = Color.White,
    primaryContainer = LightBlueSurface,
    secondary = LightBlue,
    onSecondary = Color.Black,
    error = LightRed,
    onError = Color.White,
    background = OffWhite,
    onBackground = Color.Black,
    surface = OffWhite,
    onSurface = Color.Black,
)

val DarkColorScheme = darkColorScheme(
    primary = DarkGreen,
    onPrimary = White,
    secondary = LightBlue,
    onSecondary = Color.Black,
    error = LightRed,
    onError = Color.Black,
    background = DarkerGreen,
    onBackground = White,
    surface = DarkerGreen,
    onSurface = White,
)
