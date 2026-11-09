package app.nubi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.nubi.R

/**
 * Roboto Condensed, empaquetada en res/font (archivos .ttf reales, no la
 * tipografía "Roboto Condensed" del sistema, que no todos los fabricantes
 * incluyen). De momento solo la usa el paywall (PantallaPaywall.kt) — ver
 * ese archivo — y no la Typography general de abajo, para no cambiar el
 * aspecto del resto de la app.
 */
val RobotoCondensedFamily = FontFamily(
    Font(R.font.roboto_condensed_regular, FontWeight.Normal),
    Font(R.font.roboto_condensed_medium, FontWeight.Medium),
    Font(R.font.roboto_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.roboto_condensed_bold, FontWeight.Bold)
)

/**
 * Tipografía.
 *
 * En iOS, Theme.swift usa dos familias del sistema de Apple:
 *   - display → .serif   (New York)
 *   - cuerpo  → .rounded (SF Rounded)
 *
 * Ninguna de las dos existe en Android, y no hay equivalente exacto. De
 * momento se usan las familias del sistema (Serif y la por defecto) para no
 * bloquear el porte de las pantallas.
 *
 * PENDIENTE (decisión de diseño, ver README): elegir dos fuentes reales y
 * empaquetarlas. Para el aire redondeado de SF Rounded, las candidatas
 * habituales son Nunito, Quicksand o Varela Round; para la serif, Lora o
 * Source Serif. Hasta entonces, el aspecto no será idéntico al de iOS
 * aunque los colores sí lo sean.
 */
private val Display = FontFamily.Serif
private val Cuerpo = FontFamily.Default

val NubiTipografia = Typography(
    // Titulares: la serif, como el display de iOS
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 34.sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 28.sp),
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 24.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 28.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 24.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 20.sp),

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
