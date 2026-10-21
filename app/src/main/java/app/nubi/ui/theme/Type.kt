package app.nubi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

// Tipografía PROVISIONAL (la del sistema). Pendiente de revisar en la fase 2
// contra la tipografía real usada en la app de iOS.
val NubiTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp
    )
)
