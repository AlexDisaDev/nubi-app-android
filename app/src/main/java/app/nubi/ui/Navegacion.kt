package app.nubi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.nubi.data.Repositorio
import app.nubi.ui.theme.NubiColores
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

/**
 * Color de cada pestaña, de la paleta de Nubi.
 *
 * Provisional en cuanto a forma: en iOS cada pestaña tiene su dibujo
 * (Ilustraciones.swift) y aquí de momento es un punto de color.
 */
private fun colorDe(p: Pestana, c: NubiColores): Color = when (p) {
    Pestana.HOY -> c.menta
    Pestana.DIA -> c.lila
    Pestana.CRECE -> c.mantequilla
    Pestana.SALUD -> c.cielo
    Pestana.DIARIO -> c.rosa
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
        else -> "Nubi"
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
                        TextButton(onClick = { enAjustes = !enAjustes }) {
                            Text(if (enAjustes) "Cerrar" else "Ajustes")
                        }
                    }
                )

                if (!enAjustes) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = NubiGeometria.margen, vertical = 4.dp)
                    ) {
                        VistaSuperior.entries.forEach { v ->
                            val seleccionada = (destino as? Destino.Arriba)?.vista == v
                            FilterChip(
                                selected = seleccionada,
                                onClick = { destino = Destino.Arriba(v) },
                                label = { Text(v.titulo) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = colores.lienzoAlto,
                                    labelColor = colores.tintaSuave,
                                    selectedContainerColor = colores.lila,
                                    selectedLabelColor = colores.tinta
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (!enAjustes) {
                NavigationBar(containerColor = colores.superficie) {
                    Pestana.entries.forEach { p ->
                        NavigationBarItem(
                            selected = (destino as? Destino.Abajo)?.pestana == p,
                            onClick = { destino = Destino.Abajo(p) },
                            icon = {
                                val activa = (destino as? Destino.Abajo)?.pestana == p
                                Box(
                                    Modifier
                                        .size(if (activa) 14.dp else 10.dp)
                                        .clip(CircleShape)
                                        .background(colorDe(p, colores))
                                )
                            },
                            label = { Text(p.titulo) }
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
                        Pestana.HOY -> datos.bebe?.let { PantallaHoy(it, datos.registros) }
                        Pestana.DIA -> datos.bebe?.let { PantallaDia(it, datos.registros) }
                        Pestana.CRECE -> PantallaEnObras("Crece")
                        Pestana.SALUD -> PantallaEnObras("Salud")
                        Pestana.DIARIO -> PantallaEnObras("Diario")
                    }
                }
            }
        }
    }
}
