package app.nubi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Paleta de Nubi con los mismos nombres que en iOS (Theme.swift), para que
 * portar una pantalla sea traducir el código y no reinventar los colores.
 *
 * Material 3 solo tiene huecos para "primary", "surface" y demás, y Nubi usa
 * colores con significado propio (menta, melocotón, coral...), así que van
 * aparte y se acceden como NubiTheme.colores.menta.
 */
@Immutable
data class NubiColores(
    val lienzo: Color,
    val lienzoAlto: Color,
    val superficie: Color,
    val tinta: Color,
    val tintaSuave: Color,
    val tintaTenue: Color,
    val separador: Color,
    val lila: Color,
    val menta: Color,
    val melocoton: Color,
    val mantequilla: Color,
    val cielo: Color,
    val rosa: Color,
    val indigo: Color,
    val coral: Color,
    val sombra: Color,
    val sombraFuerte: Color
)

private val coloresClaros = NubiColores(
    lienzo = LienzoClaro,
    lienzoAlto = LienzoAltoClaro,
    superficie = SuperficieClara,
    tinta = TintaClara,
    tintaSuave = TintaSuaveClara,
    tintaTenue = TintaTenueClara,
    separador = SeparadorClaro,
    lila = LilaClaro,
    menta = MentaClara,
    melocoton = MelocotonClaro,
    mantequilla = MantequillaClara,
    cielo = CieloClaro,
    rosa = RosaClara,
    indigo = IndigoClaro,
    coral = CoralClaro,
    sombra = SombraClara,
    sombraFuerte = SombraFuerteClara
)

private val coloresOscuros = NubiColores(
    lienzo = LienzoOscuro,
    lienzoAlto = LienzoAltoOscuro,
    superficie = SuperficieOscura,
    tinta = TintaOscura,
    tintaSuave = TintaSuaveOscura,
    tintaTenue = TintaTenueOscura,
    separador = SeparadorOscuro,
    lila = LilaOscuro,
    menta = MentaOscura,
    melocoton = MelocotonOscuro,
    mantequilla = MantequillaOscura,
    cielo = CieloOscuro,
    rosa = RosaOscura,
    indigo = IndigoOscuro,
    coral = CoralOscuro,
    sombra = SombraOscura,
    sombraFuerte = SombraFuerteOscura
)

/**
 * Geometría, igual que en Theme.swift.
 */
object NubiGeometria {
    val radio = 22.dp
    val radioChico = 14.dp
    val margen = 20.dp
    val huecoBarra = 12.dp
}

/**
 * Además de la paleta propia, se rellena el esquema de Material 3 para que
 * los componentes de serie (botones, campos de texto, diálogos) salgan ya
 * con los colores de Nubi sin tener que vestir cada uno a mano.
 */
private fun esquemaClaro() = lightColorScheme(
    primary = IndigoClaro,
    onPrimary = SuperficieClara,
    secondary = LilaClaro,
    onSecondary = TintaClara,
    tertiary = MentaClara,
    onTertiary = TintaClara,
    background = LienzoClaro,
    onBackground = TintaClara,
    surface = SuperficieClara,
    onSurface = TintaClara,
    surfaceVariant = LienzoAltoClaro,
    onSurfaceVariant = TintaSuaveClara,
    outline = SeparadorClaro,
    error = CoralClaro,
    onError = SuperficieClara
)

private fun esquemaOscuro() = darkColorScheme(
    primary = IndigoOscuro,
    onPrimary = LienzoOscuro,
    secondary = LilaOscuro,
    onSecondary = LienzoOscuro,
    tertiary = MentaOscura,
    onTertiary = LienzoOscuro,
    background = LienzoOscuro,
    onBackground = TintaOscura,
    surface = SuperficieOscura,
    onSurface = TintaOscura,
    surfaceVariant = LienzoAltoOscuro,
    onSurfaceVariant = TintaSuaveOscura,
    outline = SeparadorOscuro,
    error = CoralOscuro,
    onError = LienzoOscuro
)

val LocalNubiColores = staticCompositionLocalOf { coloresClaros }

@Composable
fun NubiTheme(
    oscuro: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colores = if (oscuro) coloresOscuros else coloresClaros

    CompositionLocalProvider(LocalNubiColores provides colores) {
        MaterialTheme(
            colorScheme = if (oscuro) esquemaOscuro() else esquemaClaro(),
            typography = NubiTipografia,
            content = content
        )
    }
}

/**
 * Acceso corto a la paleta propia desde cualquier pantalla:
 * NubiTheme.colores.menta
 */
object NubiTheme {
    val colores: NubiColores
        @Composable get() = LocalNubiColores.current
}
