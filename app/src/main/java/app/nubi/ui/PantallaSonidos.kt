package app.nubi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nubi.audio.MotorRuido
import app.nubi.audio.TipoRuido
import app.nubi.ui.theme.NubiColores
import app.nubi.ui.theme.NubiGeometria
import app.nubi.ui.theme.NubiTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pestaña de arriba "Sonidos": ruido blanco/rosa/marrón generado en el
 * momento por MotorRuido.kt (AudioTrack, sin ficheros ni red de por
 * medio), con un temporizador para que se pare solo.
 *
 * El texto completo de la tarjeta "¿Cuál elegir?" para Rosa y Marrón no
 * venía en la captura de iOS (se cortaba): Alejandro pidió completarlo
 * con mi propio criterio, así que esas dos frases son mías, no un porte
 * literal.
 */
private enum class TemporizadorSonido(val etiqueta: String, val milisegundos: Long?) {
    MIN_30("30 min", 30L * 60_000),
    HORA_1("1 h", 60L * 60_000),
    INFINITO("Infinito", null)
}

@Composable
fun PantallaSonidos() {
    val colores = NubiTheme.colores
    var tipoSeleccionado by remember { mutableStateOf(TipoRuido.BLANCO) }
    var temporizador by remember { mutableStateOf(TemporizadorSonido.INFINITO) }
    var reproduciendo by remember { mutableStateOf(false) }
    var trabajoTemporizador by remember { mutableStateOf<Job?>(null) }
    val ambito = rememberCoroutineScope()

    fun pararDeTodo() {
        MotorRuido.detener()
        trabajoTemporizador?.cancel()
        trabajoTemporizador = null
        reproduciendo = false
    }

    fun alternarReproduccion() {
        if (reproduciendo) {
            pararDeTodo()
        } else {
            MotorRuido.reproducir(tipoSeleccionado)
            reproduciendo = true
            val duracion = temporizador.milisegundos
            trabajoTemporizador = if (duracion != null) {
                ambito.launch {
                    delay(duracion)
                    pararDeTodo()
                }
            } else null
        }
    }

    // Si cambia el tipo mientras ya está sonando, se reinicia con el nuevo
    // sin que haga falta parar y volver a darle a Play.
    LaunchedEffect(tipoSeleccionado) {
        if (reproduciendo) MotorRuido.reproducir(tipoSeleccionado)
    }

    // Al salir de esta pantalla (cambiar de pestaña), el sonido se para:
    // no tendría sentido que siguiera de fondo sin que se vea el botón.
    DisposableEffect(Unit) {
        onDispose { MotorRuido.detener() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(NubiGeometria.margen)
    ) {
        Text("Sonidos", style = MaterialTheme.typography.headlineLarge, color = colores.tinta)
        Spacer(Modifier.height(4.dp))
        Text(
            "Generados matemáticamente. Sin descargas ni anuncios.",
            style = MaterialTheme.typography.bodyMedium,
            color = colores.tintaSuave
        )
        Spacer(Modifier.height(22.dp))

        EtiquetaSeccionSonidos("Tipo de sonido")
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                TipoRuido.BLANCO to "Ruido blanco",
                TipoRuido.ROSA to "Ruido rosa",
                TipoRuido.MARRON to "Ruido marrón"
            ).forEach { (tipo, etiqueta) ->
                OpcionSonido(
                    etiqueta = etiqueta,
                    seleccionado = tipoSeleccionado == tipo,
                    colores = colores,
                    modifier = Modifier.weight(1f)
                ) { tipoSeleccionado = tipo }
            }
        }

        Spacer(Modifier.height(22.dp))
        EtiquetaSeccionSonidos("Temporizador")
        Spacer(Modifier.height(10.dp))
        SelectorSegmentadoSonidos(
            opciones = TemporizadorSonido.entries.map { it to it.etiqueta },
            actual = temporizador,
            onCambiar = { nuevo ->
                temporizador = nuevo
                // Cambiar la duración mientras ya suena reinicia la cuenta,
                // para que se respete la nueva elección desde ya.
                if (reproduciendo) {
                    trabajoTemporizador?.cancel()
                    val duracion = nuevo.milisegundos
                    trabajoTemporizador = if (duracion != null) {
                        ambito.launch {
                            delay(duracion)
                            pararDeTodo()
                        }
                    } else null
                }
            }
        )

        Spacer(Modifier.height(28.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(colores.lila)
                    .clickable { alternarReproduccion() },
                contentAlignment = Alignment.Center
            ) {
                Icono(
                    if (reproduciendo) IconoNubi.PAUSA else IconoNubi.PLAY,
                    tint = colores.superficie,
                    tamano = 34.dp
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Card(
            shape = RoundedCornerShape(NubiGeometria.radio),
            colors = CardDefaults.cardColors(containerColor = colores.superficie),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BadgeIcono(
                        IconoNubi.ONDAS,
                        colorFondo = colores.cielo,
                        colorIcono = colores.tinta,
                        tamano = 32.dp,
                        tamanoIcono = 15.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("¿Cuál elegir?", style = MaterialTheme.typography.titleMedium, color = colores.tinta)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    buildString {
                        append("Blanco: enmascara ruidos bruscos (portazos, perros, hermanos mayores).\n\n")
                        append("Rosa: más suave y con menos agudos, ideal para quedarse dormido sin sobresaltos.\n\n")
                        append("Marrón: aún más grave y envolvente, para bebés más sensibles al sonido.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.tintaSuave
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun OpcionSonido(
    etiqueta: String,
    seleccionado: Boolean,
    colores: NubiColores,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(NubiGeometria.radioChico))
            .then(
                if (seleccionado) {
                    Modifier.border(1.5.dp, colores.lila, RoundedCornerShape(NubiGeometria.radioChico))
                } else Modifier
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 6.dp)
    ) {
        BadgeIcono(
            IconoNubi.ONDAS,
            colorFondo = if (seleccionado) colores.lila.copy(alpha = 0.35f) else colores.lienzoAlto,
            colorIcono = if (seleccionado) colores.lila else colores.tintaTenue,
            tamano = 56.dp,
            tamanoIcono = 22.dp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            etiqueta,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (seleccionado) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (seleccionado) colores.tinta else colores.tintaTenue,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EtiquetaSeccionSonidos(texto: String) {
    Text(
        texto.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = NubiTheme.colores.tintaTenue
    )
}

// =====================================================================
// Selector segmentado genérico, igual patrón que el de HojasRegistro.kt
// (cada fichero tiene el suyo: "private" en Kotlin es por fichero, no por
// paquete, así que no se puede compartir sin hacerlo público).
// =====================================================================
@Composable
private fun <T> SelectorSegmentadoSonidos(
    opciones: List<Pair<T, String>>,
    actual: T,
    onCambiar: (T) -> Unit
) {
    val colores = NubiTheme.colores
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(colores.lienzoAlto)
            .padding(4.dp)
    ) {
        opciones.forEach { (valor, etiqueta) ->
            val seleccionado = actual == valor
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (seleccionado) colores.superficie else Color.Transparent)
                    .clickable { onCambiar(valor) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    etiqueta,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (seleccionado) colores.tinta else colores.tintaTenue
                )
            }
        }
    }
}
