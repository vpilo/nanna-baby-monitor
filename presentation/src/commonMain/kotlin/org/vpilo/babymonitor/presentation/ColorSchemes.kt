package org.vpilo.babymonitor.presentation

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val LightColorScheme =
    lightColorScheme(
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
        scrim = OffWhite.copy(alpha = 0.6f),
    )

val DarkColorScheme =
    darkColorScheme(
        primary = LighterGreen,
        onPrimary = White,
        primaryContainer = DarkBlueSurface,
        secondary = LightBlue,
        onSecondary = Color.Black,
        error = LightRed,
        onError = Color.Black,
        background = OffBlack,
        onBackground = White,
        surface = OffBlack,
        onSurface = White,
        scrim = OffBlack.copy(alpha = 0.6f),
    )
