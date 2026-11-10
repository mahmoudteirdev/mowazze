package com.mowazze3.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mowazze3.R

/**
 * Add the font files (both are SIL OFL) under res/font/ :
 *   inter_regular / inter_medium / inter_semibold / inter_bold
 *   ibm_plex_sans_arabic_regular / _medium / _semibold / _bold
 * or load them with androidx.compose.ui.text.googlefonts (downloadable fonts).
 *
 * NOTE: the web design used a CSS font stack (Inter first, Arabic fallback). Android has no per-glyph
 * family chain in Compose. Use ONE family per locale: Arabic UI -> IBM Plex Sans Arabic for everything
 * (verify it renders Latin digits and Shizuku/GB well), English UI -> Inter.
 */
// Inter is not bundled while the app is Arabic only (see AppLocale). Add its files with the English UI.
val Inter: FontFamily = FontFamily.SansSerif
val PlexArabic = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
)

/** All sizes are sp (they follow the user's font scale). Line heights are sp too. */
fun mowazzeTypography(family: FontFamily) = Typography(
    displaySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),    // Display
    headlineMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp), // Headline
    titleLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),      // Title (screen + sheet titles)
    titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp),     // Body L
    bodyMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),    // Body
    labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),    // Label
    bodySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),     // Caption (minimum size allowed)
    // Not in the design scale. Set so that Material defaults can't bring in another font or go under 12sp.
    titleSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
)

/** Stats numbers: 20/28 semibold with tabular figures. Always LTR (wrap the Text in a LTR CompositionLocalProvider). */
fun statNumberStyle(family: FontFamily) = TextStyle(
    fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp, fontFeatureSettings = "tnum",
)
