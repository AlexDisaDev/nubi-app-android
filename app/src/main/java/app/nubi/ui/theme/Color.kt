package app.nubi.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colores de Nubi, portados uno a uno desde Theme.swift del proyecto de iOS.
 * Cada color tiene su par claro/oscuro, igual que allí.
 *
 * Si cambias un color aquí, cámbialo también en Theme.swift (y al revés):
 * son la misma marca en dos plataformas, no dos paletas distintas.
 */

// Fondos
val LienzoClaro      = Color(0xFFF7F4FB)
val LienzoOscuro     = Color(0xFF161423)
val LienzoAltoClaro  = Color(0xFFFBF9FE)
val LienzoAltoOscuro = Color(0xFF1C1A2B)
val SuperficieClara  = Color(0xFFFFFFFF)
val SuperficieOscura = Color(0xFF252236)

// Tintas de texto
val TintaClara      = Color(0xFF3D3450)
val TintaOscura     = Color(0xFFF0E8FF)
val TintaSuaveClara = Color(0xFF6B6282)
val TintaSuaveOscura= Color(0xFFC8BEE0)
val TintaTenueClara = Color(0xFFA49BB5)
val TintaTenueOscura= Color(0xFF7A7190)
val SeparadorClaro  = Color(0xFFE8E3F0)
val SeparadorOscuro = Color(0xFF3A3550)

// Acentos
val LilaClaro          = Color(0xFFC9B8E8)
val LilaOscuro         = Color(0xFFA895D4)
val MentaClara         = Color(0xFFA8DCCB)
val MentaOscura        = Color(0xFF7BC4A8)
val MelocotonClaro     = Color(0xFFF7C9B5)
val MelocotonOscuro    = Color(0xFFE5A88E)
val MantequillaClara   = Color(0xFFF5E1A4)
val MantequillaOscura  = Color(0xFFE5CB7A)
val CieloClaro         = Color(0xFFB7D4EF)
val CieloOscuro        = Color(0xFF8BB8DE)
val RosaClara          = Color(0xFFEFC0D4)
val RosaOscura         = Color(0xFFD99CB5)
val IndigoClaro        = Color(0xFF6E63A6)
val IndigoOscuro       = Color(0xFF9B8FD4)
val CoralClaro         = Color(0xFFE88B7B)   // rojo pastel, para despertares
val CoralOscuro        = Color(0xFFD46B5A)

// Sombras (negro con distinta opacidad según el modo)
val SombraClara       = Color(0x14000000)  // 8 %
val SombraOscura      = Color(0x66000000)  // 40 %
val SombraFuerteClara = Color(0x26000000)  // 15 %
val SombraFuerteOscura= Color(0x99000000)  // 60 %
