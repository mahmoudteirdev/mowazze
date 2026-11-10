package com.mowazze3.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

private val LocalExtra = staticCompositionLocalOf { LightExtra }

object MowazzeTheme {
    val extra: ExtraColors
        @Composable @ReadOnlyComposable get() = LocalExtra.current
}

@Composable
fun MowazzeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),   // follows the system; no in-app switch yet
    content: @Composable () -> Unit,
) {
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val family = if (isArabic) PlexArabic else Inter
    CompositionLocalProvider(LocalExtra provides if (darkTheme) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = mowazzeTypography(family),
            shapes = MowazzeShapes,
            content = content,
        )
    }
}
