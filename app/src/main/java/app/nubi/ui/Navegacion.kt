package app.nubi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
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
import app.nubi.ui.theme.NubiTheme

/**
 * Las cinco pestañas, en el mismo orden y con los mismos nombres que en
 * iOS (enum Pestana en NubiApp.swift).
 */
private enum class Pestana(val titulo: String) {
    HOY("Hoy"),
    DIA("Día"),
    CRECE("Crece"),
    SALUD("Salud"),
    DIARIO("Diario")
}

/**
 * Color de cada pestaña, de la paleta de Nubi.
 *
 * Provisional en cuanto a forma: en iOS cada pestaña tiene su dibujo
 * (Ilustraciones.swift, 873 líneas) y aquí de momento es un punto de
 * color. El paquete de iconos de Material está deprecado y no se usa a
 * propósito: los iconos definitivos serán los de Nubi portados a vectores,
 * no los genéricos de Android.
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
    var pestana by remember { mutableStateOf(Pestana.HOY) }
    var enAjustes by remember { mutableStateOf(false) }
    val colores = NubiTheme.colores

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (enAjustes) "Ajustes" else "Nubi") },
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
        },
        bottomBar = {
            if (!enAjustes) {
                NavigationBar(containerColor = colores.superficie) {
                    Pestana.entries.forEach { p ->
                        NavigationBarItem(
                            selected = pestana == p,
                            onClick = { pestana = p },
                            icon = {
                                Box(
                                    Modifier
                                        .size(if (pestana == p) 14.dp else 10.dp)
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
                    alCerrarSesion = alCerrarSesion
                )

                else -> when (pestana) {
                    Pestana.HOY -> datos.bebe?.let { PantallaHoy(it, datos.registros) }
                    Pestana.DIA -> PantallaEnObras("Día")
                    Pestana.CRECE -> PantallaEnObras("Crece")
                    Pestana.SALUD -> PantallaEnObras("Salud")
                    Pestana.DIARIO -> PantallaEnObras("Diario")
                }
            }
        }
    }
}
