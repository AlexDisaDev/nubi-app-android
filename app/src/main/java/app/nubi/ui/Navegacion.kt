package app.nubi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nubi.data.Repositorio
import app.nubi.ui.theme.NubiGeometria
import app.nubi.ui.theme.NubiTheme

/**
 * Las cinco pestañas de abajo, en el mismo orden y con los mismos nombres
 * que en iOS (enum Pestana en NubiApp.swift).
 */
private enum class Pestana(val titulo: String) {
    HOY("Hoy"),
    DIA("Día"),
    CRECE("Crece"),
    SALUD("Salud"),
    DIARIO("Diario")
}

/**
 * La barra de arriba (enum VistaSuperior en iOS): las tres secciones de
 * premium. Funcionan igual que las de abajo — se selecciona una y se ve
 * su pantalla — solo que están arriba.
 */
private enum class VistaSuperior(val titulo: String) {
    BANCO("Banco"),
    ALIMENTACION("Alimentación"),
    SONIDOS("Sonidos")
}

/**
 * Qué se está viendo: una pestaña de abajo o una de arriba. Al ser una
 * sola variable, seleccionar arriba deselecciona abajo y al revés, sin
 * que haya que cerrar nada a mano.
 */
private sealed interface Destino {
    data class Abajo(val pestana: Pestana) : Destino
    data class Arriba(val vista: VistaSuperior) : Destino
}

/** Qué dibujo le toca a cada pestaña de abajo. */
private fun iconoDe(p: Pestana): IconoNubi = when (p) {
    Pestana.HOY -> IconoNubi.NUBE
    Pestana.DIA -> IconoNubi.RELOJ
    Pestana.CRECE -> IconoNubi.CRECE
    Pestana.SALUD -> IconoNubi.MALETIN
    Pestana.DIARIO -> IconoNubi.LIBRETA
}

/** Qué dibujo le toca a cada sección de arriba. */
private fun iconoDe(v: VistaSuperior): IconoNubi = when (v) {
    VistaSuperior.BANCO -> IconoNubi.BIBERON
    VistaSuperior.ALIMENTACION -> IconoNubi.CRONOMETRO
    VistaSuperior.SONIDOS -> IconoNubi.ONDAS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Navegacion(alCerrarSesion: () -> Unit) {
    val datos by Repositorio.local.datos.collectAsState()
    var destino by remember { mutableStateOf<Destino>(Destino.Abajo(Pestana.HOY)) }
    var enAjustes by remember { mutableStateOf(false) }
    val colores = NubiTheme.colores

    val titulo = when {
        enAjustes -> "Ajustes"
        destino is Destino.Arriba -> (destino as Destino.Arriba).vista.titulo
        else -> ""
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(titulo) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colores.lienzo,
                        titleContentColor = colores.tinta
                    ),
                    actions = {
                        // El acceso a Ajustes real es el botón de controles
                        // junto al icono del sol en la cabecera de Hoy; aquí
                        // solo hace falta un modo de volver a cerrarlo.
                        if (enAjustes) {
                            TextButton(onClick = { enAjustes = false }) {
                                Text("Cerrar")
                            }
                        }
                    }
                )

                if (!enAjustes) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = NubiGeometria.margen, vertical = 10.dp)
                    ) {
                        VistaSuperior.entries.forEach { v ->
                            val seleccionada = (destino as? Destino.Arriba)?.vista == v
                            val tinte = if (seleccionada) colores.lila else colores.tintaTenue
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { destino = Destino.Arriba(v) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    v.titulo,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = tinte
                                )
                                Spacer(Modifier.height(6.dp))
                                Icono(iconoDe(v), tint = tinte, tamano = 20.dp)
                            }
                        }
                    }
                    HorizontalDivider(color = colores.separador)
                }
            }
        },
        bottomBar = {
            if (!enAjustes) {
                NavigationBar(containerColor = colores.superficie) {
                    Pestana.entries.forEach { p ->
                        val activa = (destino as? Destino.Abajo)?.pestana == p
                        NavigationBarItem(
                            selected = activa,
                            onClick = { destino = Destino.Abajo(p) },
                            icon = {
                                Icono(
                                    iconoDe(p),
                                    tint = if (activa) colores.lila else colores.tintaTenue,
                                    tamano = 22.dp
                                )
                            },
                            label = { Text(p.titulo) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colores.lila,
                                selectedTextColor = colores.lila,
                                unselectedIconColor = colores.tintaTenue,
                                unselectedTextColor = colores.tintaTenue,
                                indicatorColor = androidx.compose.ui.graphics.Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { relleno ->
        Box(Modifier.padding(relleno)) {
            when {
                enAjustes -> PantallaAjustes(
                    bebe = datos.bebe,
                    tema = datos.tema,
                    alCerrarSesion = alCerrarSesion
                )

                else -> when (val d = destino) {
                    is Destino.Arriba -> PantallaEnObras(d.vista.titulo)

                    is Destino.Abajo -> when (d.pestana) {
                        Pestana.HOY -> datos.bebe?.let {
                            PantallaHoy(it, datos.registros, alAbrirAjustes = { enAjustes = true })
                        }
                        Pestana.DIA -> datos.bebe?.let { PantallaDia(it, datos.registros) }
                        Pestana.CRECE -> datos.bebe?.let { PantallaCrece(it, datos.medidas) }
                        Pestana.SALUD -> datos.bebe?.let {
                            PantallaSalud(
                                bebe = it,
                                citas = datos.citas,
                                vacunas = datos.vacunas,
                                gruposVacunas = datos.gruposVacunas,
                                vacunasPropias = datos.vacunasPropias,
                                enfermedades = datos.enfermedades,
                                comunidadGuardada = datos.comunidad
                            )
                        }
                        Pestana.DIARIO -> datos.bebe?.let { PantallaDiario(it, datos.diario) }
                    }
                }
            }
        }
    }
}
