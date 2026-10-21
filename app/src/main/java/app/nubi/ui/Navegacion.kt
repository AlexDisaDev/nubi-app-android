package app.nubi.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.nubi.data.Repositorio
import app.nubi.ui.theme.NubiTheme

/**
 * Las cinco pestañas, en el mismo orden y con los mismos nombres que en
 * iOS (enum Pestana en NubiApp.swift).
 *
 * Los iconos son provisionales: los de Nubi son dibujos propios
 * (Ilustraciones.swift) y hay que portarlos como vectores en su momento.
 */
private enum class Pestana(val titulo: String, val icono: ImageVector) {
    HOY("Hoy", Icons.Default.Home),
    DIA("Día", Icons.Default.DateRange),
    CRECE("Crece", Icons.Default.Star),
    SALUD("Salud", Icons.Default.Favorite),
    DIARIO("Diario", Icons.Default.Edit)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Navegacion(alCerrarSesion: () -> Unit) {
    val datos by Repositorio.local.datos.collectAsState()
    var pestana by remember { mutableStateOf(Pestana.HOY) }
    var enAjustes by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (enAjustes) "Ajustes" else "Nubi") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NubiTheme.colores.lienzo,
                    titleContentColor = NubiTheme.colores.tinta
                ),
                actions = {
                    IconButton(onClick = { enAjustes = !enAjustes }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                }
            )
        },
        bottomBar = {
            if (!enAjustes) {
                NavigationBar(containerColor = NubiTheme.colores.superficie) {
                    Pestana.entries.forEach { p ->
                        NavigationBarItem(
                            selected = pestana == p,
                            onClick = { pestana = p },
                            icon = { Icon(p.icono, contentDescription = p.titulo) },
                            label = { Text(p.titulo) }
                        )
                    }
                }
            }
        }
    ) { relleno ->
        val contenido = Modifier.padding(relleno)

        if (enAjustes) {
            androidx.compose.foundation.layout.Box(contenido) {
                PantallaAjustes(bebe = datos.bebe, alCerrarSesion = alCerrarSesion)
            }
            return@Scaffold
        }

        androidx.compose.foundation.layout.Box(contenido) {
            when (pestana) {
                Pestana.HOY -> datos.bebe?.let { PantallaHoy(it, datos.registros) }
                Pestana.DIA -> PantallaEnObras("Día")
                Pestana.CRECE -> PantallaEnObras("Crece")
                Pestana.SALUD -> PantallaEnObras("Salud")
                Pestana.DIARIO -> PantallaEnObras("Diario")
            }
        }
    }
}
