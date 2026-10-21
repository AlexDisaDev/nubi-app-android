package app.nubi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val NubiLightColors = lightColorScheme(
    primary = PurplePrimary,
    secondary = PurpleSecondary,
    tertiary = PurpleTertiary
)

private val NubiDarkColors = darkColorScheme(
    primary = PurplePrimary,
    secondary = PurpleSecondary,
    tertiary = PurpleTertiary
)

@Composable
fun NubiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) NubiDarkColors else NubiLightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NubiTypography,
        content = content
    )
}
