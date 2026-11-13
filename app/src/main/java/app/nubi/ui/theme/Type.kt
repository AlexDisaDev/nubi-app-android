package app.nubi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.nubi.R

/**
 * Tipografía: dos familias, igual que en iOS (Theme.swift).
 *   - Titulares y subtítulos → Barlow Semi Condensed (Bold/ExtraBold para
 *     titulares, Medium para subtítulos).
 *   - Texto de interfaz y descripciones largas → Inter.
 * Los `.ttf` están en res/font/ con un peso estático por archivo.
 */
val BarlowSemiCondensed = FontFamily(
    Font(R.font.barlow_semi_condensed_regular, FontWeight.Normal),
    Font(R.font.barlow_semi_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_semi_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_semi_condensed_bold, FontWeight.Bold),
    Font(R.font.barlow_semi_condensed_extrabold, FontWeight.ExtraBold)
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

// Sin `private`: PantallaPaywall.kt y otras pantallas referencian estas dos
// directamente, igual que Paywall.swift en iOS usa Theme.display/Theme.cuerpo.
val Display = BarlowSemiCondensed
val Cuerpo = Inter

val NubiTipografia = Typography(
    // Titulares: Barlow Semi Condensed Bold, igual que Theme.display en iOS
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 36.sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp),

    // Subtítulos: Barlow Semi Condensed Medium
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 18.sp),
    titleSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 15.sp),

    // Texto de interfaz y descripciones largas: Inter
    bodyLarge = TextStyle(fontFamily = Cuerpo, fontWeight = FontWeight.Normal, fontSize = 18.sp),
    bodyMedium = TextStyle(fontFamily = Cuerpo, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodySmall = TextStyle(fontFamily = Cuerpo, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelLarge = TextStyle(fontFamily = Cuerpo, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = Cuerpo, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = Cuerpo, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
)
