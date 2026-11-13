package app.nubi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nubi.R
import app.nubi.data.Repositorio
import app.nubi.i18n.L10n
import app.nubi.ui.theme.NubiGeometria
import app.nubi.ui.theme.NubiTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Si la carga no ha terminado en este tiempo, se avisa y se ofrece reintentar. */
private const val AVISO_TARDANDO_MS = 20_000L

/**
 * Pantalla completa que se ve justo después de aceptar una invitación,
 * mientras baja el bebé compartido y todos sus registros (ver
 * VistaCargandoCompartido en iOS). Antes el diálogo se cerraba al momento y
 * la descarga seguía en segundo plano sin ninguna señal: parecía que no
 * había pasado nada.
 *
 * - Barra de progreso real: tablas ya descargadas sobre el total
 *   (Sincronizador.progresoBajada).
 * - A los 20 s sin terminar: aviso de que tarda más de lo normal, con
 *   "Reintentar" y "Seguir en segundo plano" para no dejar a nadie atrapado.
 * - Si falla (sin red, error del servidor): mensaje y los mismos botones.
 * - Al terminar bien, se cierra sola y se ve la app con el bebé ya cargado.
 */
@Composable
fun PantallaCargandoCompartido(alTerminar: () -> Unit) {
    val colores = NubiTheme.colores
    val progreso by Repositorio.progresoBajada.collectAsState()
    var intento by remember { mutableIntStateOf(0) }
    var tardando by remember { mutableStateOf(false) }
    var fallo by remember { mutableStateOf(false) }

    LaunchedEffect(intento) {
        tardando = false
        fallo = false
        val aviso = launch {
            delay(AVISO_TARDANDO_MS)
            tardando = true
        }
        val ok = Repositorio.sincronizarYEsperar()
        aviso.cancel()
        if (ok) {
            // Que la barra llegue a llenarse antes de desaparecer.
            delay(300)
            alTerminar()
        } else {
            fallo = true
        }
    }

    // Atrás no deja una pantalla vacía detrás: equivale a "seguir en
    // segundo plano" (la descarga continúa igual).
    BackHandler {
        Repositorio.sincronizarEnSegundoPlano()
        alTerminar()
    }

    val valorBarra by animateFloatAsState(
        targetValue = if (fallo) 0f else (progreso ?: 1f),
        animationSpec = tween(350),
        label = "progresoCompartido"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colores.lienzo)
            // Que los toques no lleguen a la pantalla de debajo.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = NubiGeometria.margen * 1.5f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.nubi_logo_splash),
                contentDescription = null,
                modifier = Modifier.width(140.dp)
            )
            Spacer(Modifier.height(28.dp))
            Text(
                L10n.t("Preparando el bebé compartido…"),
                style = MaterialTheme.typography.headlineSmall,
                color = colores.tinta,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (fallo) L10n.t("No se ha podido cargar. Comprueba tu conexión e inténtalo de nuevo.")
                else L10n.t("Estamos trayendo todos sus registros. Solo tarda unos segundos."),
                style = MaterialTheme.typography.bodyMedium,
                color = if (fallo) colores.coral else colores.tintaSuave,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            if (!fallo) {
                if (progreso == null) {
                    // Entre que se acepta y empieza la bajada (subida de
                    // pendientes, etc.): barra indeterminada.
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                        color = colores.lila,
                        trackColor = colores.lienzoAlto
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { valorBarra },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                        color = colores.lila,
                        trackColor = colores.lienzoAlto,
                        drawStopIndicator = {}
                    )
                }
            }

            if (tardando && !fallo) {
                Spacer(Modifier.height(16.dp))
                Text(
                    L10n.t("Está tardando más de lo normal."),
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.tintaTenue,
                    textAlign = TextAlign.Center
                )
            }

            if (tardando || fallo) {
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { intento++ },
                    colors = ButtonDefaults.buttonColors(containerColor = colores.lila, contentColor = colores.tinta),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(L10n.t("Reintentar"), style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp))
                }
                TextButton(
                    onClick = {
                        Repositorio.sincronizarEnSegundoPlano()
                        alTerminar()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(L10n.t("Seguir en segundo plano"), color = colores.tintaSuave)
                }
            }
        }
    }
}
