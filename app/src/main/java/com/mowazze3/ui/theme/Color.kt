package com.mowazze3.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Material3 schemes. Needs Material3 >= 1.2 (surfaceContainer* roles). Values from design Step 6, 8, 9. */
val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF1A5FB8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6F0FC),
    onPrimaryContainer = Color(0xFF0F3F7A),
    secondary = Color(0xFFFF8A3D),            // orange = "needs root" semantics only
    onSecondary = Color(0xFF1A0D03),
    secondaryContainer = Color(0xFFFDEBDD),
    onSecondaryContainer = Color(0xFF7A3407),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF12171B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF12171B),
    surfaceVariant = Color(0xFFF4F6F8),        // cards, stat tiles, device rows
    onSurfaceVariant = Color(0xFF4B5660),      // secondary text
    surfaceContainerLow = Color(0xFFF4F6F8),
    outline = Color(0xFF66727D),               // control borders (4.92:1 on white)
    outlineVariant = Color(0xFFD3DAE0),        // decorative card borders only
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE7E5),
    onErrorContainer = Color(0xFFB3261E),
    inverseSurface = Color(0xFF12171B),        // snackbar
    inverseOnSurface = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFF8EC5FF),        // snackbar action
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF8EC5FF),
    onPrimary = Color(0xFF06182B),
    primaryContainer = Color(0xFF14283F),      // added in Step 8/9, not in the Step 6 table
    onPrimaryContainer = Color(0xFFBBDCFF),
    secondary = Color(0xFFFF8A3D),
    onSecondary = Color(0xFF1A0D03),
    secondaryContainer = Color(0xFF2B1A0B),
    onSecondaryContainer = Color(0xFFFFB680),
    background = Color(0xFF0C0F11),
    onBackground = Color(0xFFE8ECEF),
    surface = Color(0xFF0C0F11),
    onSurface = Color(0xFFE8ECEF),
    surfaceVariant = Color(0xFF12171B),
    onSurfaceVariant = Color(0xFFB5BFC8),
    surfaceContainerLow = Color(0xFF12171B),
    surfaceContainerHigh = Color(0xFF1E252B),  // "raised"
    outline = Color(0xFF66727D),               // 3.91:1 on #0C0F11
    outlineVariant = Color(0xFF323B43),
    error = Color(0xFFFF8F85),
    onError = Color(0xFF3A1512),
    errorContainer = Color(0xFF3A1512),
    onErrorContainer = Color(0xFFFF8F85),
    inverseSurface = Color(0xFFE8ECEF),
    inverseOnSurface = Color(0xFF12171B),
    inversePrimary = Color(0xFF1A5FB8),
)

/** Colors Material3 does not have. Read with MowazzeTheme.extra. */
@Immutable
data class ExtraColors(
    val success: Color,
    val successContainer: Color,
    val warn: Color,                // border / icon of the "no internet" banner
    val warnContainer: Color,
    val onWarnContainer: Color,
    val disabledContainer: Color,   // disabled button / disabled option / blocked device row
    val onDisabled: Color,          // disabled BUTTON label (decorative, exempt). Option-card text uses onSurfaceVariant.
    val primaryHover: Color,
    val scrim: Color,
    val qrBackground: Color,        // always white so the QR scans in dark mode too
    val qrForeground: Color,
)

val LightExtra = ExtraColors(
    success = Color(0xFF176B47), successContainer = Color(0xFFE4F4EC),
    warn = Color(0xFFA8460A), warnContainer = Color(0xFFFDEBDD), onWarnContainer = Color(0xFF7A3407),
    disabledContainer = Color(0xFFE8ECEF), onDisabled = Color(0xFF8A96A1),
    primaryHover = Color(0xFF154D96), scrim = Color(0x8012171B),
    qrBackground = Color(0xFFFFFFFF), qrForeground = Color(0xFF12171B),
)

val DarkExtra = ExtraColors(
    success = Color(0xFF5FD3A0), successContainer = Color(0xFF123226),
    warn = Color(0xFFFF8A3D), warnContainer = Color(0xFF2B1A0B), onWarnContainer = Color(0xFFFFB680),
    disabledContainer = Color(0xFF1E252B), onDisabled = Color(0xFF8A96A1),
    primaryHover = Color(0xFFB3D8FF), scrim = Color(0x99000000),
    qrBackground = Color(0xFFFFFFFF), qrForeground = Color(0xFF12171B),
)
