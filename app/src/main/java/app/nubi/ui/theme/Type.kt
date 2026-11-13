package app.nubi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.nubi.R

/**
 * Tipografía: Roboto Condensed en toda la app (misma familia que iOS y la
 * web), con Regular 400 como peso por defecto. Los `.ttf` están en
 * res/font/ con un peso estático por archivo (Android no interpola fuentes
 * variables en tiempo real igual que una fuente estática), así que la
 * familia declara cada peso con su propio archivo.
 */
val RobotoCondensed = FontFamily(
    Font(R.font.roboto_condensed_regular, FontWeight.Normal),
    Font(R.font.roboto_condensed_medium, FontWeight.Medium),
    Font(R.font.roboto_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.roboto_condensed_bold, FontWeight.Bold)
)

// Sin `private`: PantallaPaywall.kt y otras pantallas referencian estas dos
// directamente, igual que Paywall.swift en iOS usa Theme.display/Theme.cuerpo.
// Antes eran familias distintas (serif / redondeada del sistema); ahora las
// dos son Roboto Condensed para unificar la tipografía con iOS y la web.
val Display = RobotoCondensed
val Cuerpo = RobotoCondensed

val NubiTipografia = Typography(
    // Titulares: Roboto Condensed Regular 400, igual que Theme.display en iOS
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 34.sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 28.sp),
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 24.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 28.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 24.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 20.sp),

    // Texto corriente: la redondeada
    titleLarge = TextStyle(fontFamily = Cuerpo, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Cuerpo, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = Cuerpo, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = Cuerpo, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Cuerpo, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = Cuerpo, fontWeight = FontWeight.Normal, fontSize = 12.sp),

    // etiqueta de iOS: 11 pt, semibold, redondeada
    labelLarge = TextStyle(fontFamily = Cuerpo, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Cuerpo, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Cuerpo, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
)
