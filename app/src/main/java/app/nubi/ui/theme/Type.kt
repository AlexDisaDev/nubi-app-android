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

// Roboto Condensed, al ser condensada, ocupa menos ancho que la Roboto por
// defecto que se usaba antes — a igual tamaño en sp se percibe más pequeña.
// Para compensar, cada escalón sube ~1-2sp respecto a los tamaños "de catálogo"
// de Material 3.
val NubiTipografia = Typography(
    // Titulares: Roboto Condensed Regular 400, igual que Theme.display en iOS
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 36.sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 30.sp),
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 26.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 30.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 26.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 22.sp),

    // Texto corriente: la redondeada
    titleLarge = TextStyle(fontFamily = Cuerpo, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Cuerpo, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleSmall = TextStyle(fontFamily = Cuerpo, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = Cuerpo, fontWeight = FontWeight.Normal, fontSize = 18.sp),
    bodyMedium = TextStyle(fontFamily = Cuerpo, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodySmall = TextStyle(fontFamily = Cuerpo, fontWeight = FontWeight.Normal, fontSize = 13.sp),

    // etiqueta de iOS: 11 pt, semibold, redondeada
    labelLarge = TextStyle(fontFamily = Cuerpo, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = Cuerpo, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = Cuerpo, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
)
