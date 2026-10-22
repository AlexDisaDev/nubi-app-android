package app.nubi.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nubi.data.ColorEtapa
import app.nubi.data.EtapaBebe
import app.nubi.data.LadoPecho
import app.nubi.data.Registro
import app.nubi.data.Repositorio
import app.nubi.data.TipoLeche
import app.nubi.data.TipoRegistro
import app.nubi.data.UnidadBiberon
import app.nubi.ui.theme.NubiColores
import app.nubi.ui.theme.NubiGeometria
import app.nubi.ui.theme.NubiTheme
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Las cuatro hojas que se abren desde la pestaña Hoy (ver VistaInicio.swift):
 * Pecho, Biberón, Pañal y el detalle de la etapa actual. Cada una de
 * registro guarda directamente a la hora de "ahora" y cierra, igual que en
 * iOS — no hay selector de hora propio todavía, igual que en la botonera
 * de la pestaña Día.
 */

// =====================================================================
// Pecho — elegir lado, se registra al tocar.
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaPecho(bebeId: String, onCerrar: () -> Unit) {
    val colores = NubiTheme.colores
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = sheetState, containerColor = colores.lienzo) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NubiGeometria.margen)
                .padding(bottom = 28.dp)
        ) {
            Text("Pecho", style = MaterialTheme.typography.titleLarge, color = colores.tinta)
            Spacer(Modifier.height(6.dp))
            Text(
                "¿Qué lado ha tomado?",
                style = MaterialTheme.typography.bodyMedium,
                color = colores.tintaSuave
            )
            Spacer(Modifier.height(18.dp))

            FilaLadoPecho(titulo = "Pecho izquierdo", subtitulo = "Pecho I") {
                Repositorio.guardarRegistro(
                    Registro(
                        bebeId = bebeId,
                        tipo = TipoRegistro.PECHO,
                        inicio = Instant.now(),
                        lado = LadoPecho.IZQUIERDA
                    )
                )
                onCerrar()
            }
            Spacer(Modifier.height(10.dp))
            FilaLadoPecho(titulo = "Pecho derecho", subtitulo = "Pecho D") {
                Repositorio.guardarRegistro(
                    Registro(
                        bebeId = bebeId,
                        tipo = TipoRegistro.PECHO,
                        inicio = Instant.now(),
                        lado = LadoPecho.DERECHA
                    )
                )
                onCerrar()
            }
        }
    }
}

@Composable
private fun FilaLadoPecho(titulo: String, subtitulo: String, onClick: () -> Unit) {
    val colores = NubiTheme.colores
    Card(
        shape = RoundedCornerShape(NubiGeometria.radio),
        colors = CardDefaults.cardColors(containerColor = colores.superficie),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BadgeIcono(
                IconoNubi.CORAZON,
                colorFondo = colores.melocoton,
                colorIcono = colores.tinta,
                tamano = 48.dp,
                tamanoIcono = 20.dp
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.bodyLarge, color = colores.tinta)
                Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = colores.tintaTenue)
            }
            Icono(IconoNubi.CHEVRON, tint = colores.tintaTenue, tamano = 16.dp)
        }
    }
}

// =====================================================================
// Biberón — cantidad, unidad y tipo de leche.
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaBiberon(bebeId: String, onCerrar: () -> Unit) {
    val colores = NubiTheme.colores
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var unidad by remember { mutableStateOf(UnidadBiberon.ML) }
    var tipoLeche by remember { mutableStateOf(TipoLeche.MATERNA) }
    var cantidad by remember { mutableStateOf(120.0) }

    val sugerencias = if (unidad == UnidadBiberon.ML)
        listOf(30.0, 60.0, 90.0, 120.0, 150.0, 180.0, 210.0, 240.0)
    else
        listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0)
    val paso = if (unidad == UnidadBiberon.ML) 10.0 else 1.0
    val minimo = if (unidad == UnidadBiberon.ML) 10.0 else 1.0
    val maximo = if (unidad == UnidadBiberon.ML) 400.0 else 14.0

    fun guardar(conCantidad: Boolean) {
        Repositorio.guardarRegistro(
            Registro(
                bebeId = bebeId,
                tipo = TipoRegistro.BIBERON,
                inicio = Instant.now(),
                cantidadBiberon = if (conCantidad) cantidad else null,
                unidadBiberon = if (conCantidad) unidad else null,
                tipoLeche = tipoLeche
            )
        )
        onCerrar()
    }

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = sheetState, containerColor = colores.lienzo) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NubiGeometria.margen)
                .padding(bottom = 28.dp)
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BadgeIcono(
                    IconoNubi.BIBERON,
                    colorFondo = colores.mantequilla,
                    colorIcono = colores.tinta,
                    tamano = 54.dp,
                    tamanoIcono = 24.dp
                )
                Spacer(Modifier.height(10.dp))
                Text("Biberón", style = MaterialTheme.typography.titleLarge, color = colores.tinta)
                Spacer(Modifier.height(4.dp))
                Text(
                    "¿Cuánto ha bebido?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.tintaSuave
                )
            }
            Spacer(Modifier.height(18.dp))

            Card(
                shape = RoundedCornerShape(NubiGeometria.radio),
                colors = CardDefaults.cardColors(containerColor = colores.superficie),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    EtiquetaSeccionHoja("Tipo de leche")
                    Spacer(Modifier.height(8.dp))
                    SelectorSegmentado(
                        opciones = listOf(TipoLeche.MATERNA to "Materna", TipoLeche.FORMULA to "Fórmula"),
                        actual = tipoLeche,
                        onCambiar = { tipoLeche = it }
                    )

                    Spacer(Modifier.height(16.dp))
                    EtiquetaSeccionHoja("Unidad")
                    Spacer(Modifier.height(8.dp))
                    SelectorSegmentado(
                        opciones = listOf(UnidadBiberon.ML to "ml", UnidadBiberon.OZ to "oz"),
                        actual = unidad,
                        onCambiar = { nueva ->
                            unidad = nueva
                            cantidad = if (nueva == UnidadBiberon.ML) 120.0 else 4.0
                        }
                    )

                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BotonCirculoBiberon(masOMenos = false, colores = colores) {
                            cantidad = (cantidad - paso).coerceAtLeast(minimo)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                textoCantidadBiberon(cantidad, unidad),
                                style = MaterialTheme.typography.displayMedium,
                                color = colores.tinta
                            )
                            Text(unidad.etiqueta, style = MaterialTheme.typography.bodySmall, color = colores.tintaSuave)
                        }
                        BotonCirculoBiberon(masOMenos = true, colores = colores) {
                            cantidad = (cantidad + paso).coerceAtMost(maximo)
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    EtiquetaSeccionHoja("Rápido")
                    Spacer(Modifier.height(8.dp))
                    sugerencias.chunked(4).forEach { fila ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            fila.forEach { valor ->
                                BotonRapidoBiberon(
                                    texto = textoValorBiberon(valor, unidad),
                                    seleccionado = cantidad == valor,
                                    colores = colores,
                                    modifier = Modifier.weight(1f)
                                ) { cantidad = valor }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { guardar(conCantidad = true) },
                colors = ButtonDefaults.buttonColors(containerColor = colores.mantequilla, contentColor = colores.tinta),
                shape = RoundedCornerShape(NubiGeometria.radioChico),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar") }

            TextButton(
                onClick = { guardar(conCantidad = false) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar sin cantidad", color = colores.tintaSuave)
            }
        }
    }
}

@Composable
private fun BotonCirculoBiberon(masOMenos: Boolean, colores: NubiColores, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(colores.lila)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (masOMenos) {
            Icono(IconoNubi.MAS, tint = colores.tinta, tamano = 18.dp)
        } else {
            Box(
                Modifier
                    .width(16.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colores.tinta)
            )
        }
    }
}

@Composable
private fun BotonRapidoBiberon(
    texto: String,
    seleccionado: Boolean,
    colores: NubiColores,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (seleccionado) colores.mantequilla else colores.lienzoAlto)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.labelLarge,
            color = if (seleccionado) colores.tinta else colores.tintaSuave,
            textAlign = TextAlign.Center
        )
    }
}

private fun textoCantidadBiberon(cantidad: Double, unidad: UnidadBiberon): String {
    if (unidad == UnidadBiberon.ML) return cantidad.toInt().toString()
    return if (cantidad == cantidad.roundToInt().toDouble()) cantidad.toInt().toString()
    else String.format(Locale.US, "%.1f", cantidad)
}

private fun textoValorBiberon(valor: Double, unidad: UnidadBiberon): String {
    if (unidad == UnidadBiberon.ML) return valor.toInt().toString()
    val numero = if (valor == valor.roundToInt().toDouble()) valor.toInt().toString()
    else String.format(Locale.US, "%.1f", valor)
    return "$numero oz"
}

// =====================================================================
// Pañal — pis y/o caca, los dos a la vez si hace falta.
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaPanal(bebeId: String, onCerrar: () -> Unit) {
    val colores = NubiTheme.colores
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pis by remember { mutableStateOf(true) }
    var caca by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = sheetState, containerColor = colores.lienzo) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NubiGeometria.margen)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BadgeIcono(
                    IconoNubi.GOTA,
                    colorFondo = colores.cielo,
                    colorIcono = colores.tinta,
                    tamano = 40.dp,
                    tamanoIcono = 18.dp
                )
                Spacer(Modifier.width(10.dp))
                Text("¿Qué hizo?", style = MaterialTheme.typography.titleLarge, color = colores.tinta)
            }
            Spacer(Modifier.height(18.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TogglePanal(
                    icono = IconoNubi.GOTA,
                    etiqueta = "Pis",
                    activo = pis,
                    color = colores.cielo,
                    colores = colores,
                    modifier = Modifier.weight(1f)
                ) { pis = !pis }
                TogglePanal(
                    icono = IconoNubi.CACA,
                    etiqueta = "Caca",
                    activo = caca,
                    color = colores.mantequilla,
                    colores = colores,
                    modifier = Modifier.weight(1f)
                ) { caca = !caca }
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    Repositorio.guardarRegistro(
                        Registro(
                            bebeId = bebeId,
                            tipo = TipoRegistro.PANAL,
                            inicio = Instant.now(),
                            panalPis = pis,
                            panalCaca = caca
                        )
                    )
                    onCerrar()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colores.cielo, contentColor = colores.tinta),
                shape = RoundedCornerShape(NubiGeometria.radioChico),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar") }
        }
    }
}

@Composable
private fun TogglePanal(
    icono: IconoNubi,
    etiqueta: String,
    activo: Boolean,
    color: Color,
    colores: NubiColores,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(NubiGeometria.radioChico))
            .background(if (activo) color else colores.lienzoAlto)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icono(icono, tint = if (activo) colores.tinta else colores.tintaTenue, tamano = 24.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            etiqueta,
            style = MaterialTheme.typography.labelLarge,
            color = if (activo) colores.tinta else colores.tintaTenue
        )
    }
}

// =====================================================================
// Selector segmentado genérico (Materna/Fórmula, ml/oz) — mismo patrón
// visual que el selector Todo/Solo hitos del Diario.
// =====================================================================
@Composable
private fun <T> SelectorSegmentado(
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

@Composable
private fun EtiquetaSeccionHoja(texto: String) {
    Text(
        texto.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = NubiTheme.colores.tintaTenue
    )
}

// =====================================================================
// Etapa — información de la etapa actual del bebé (MotorEtapas.kt).
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaEtapa(etapa: EtapaBebe, onCerrar: () -> Unit) {
    val colores = NubiTheme.colores
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorEtapa = colorDeEtapa(etapa.color, colores)

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = sheetState, containerColor = colores.lienzo) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NubiGeometria.margen)
                .padding(bottom = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(NubiGeometria.radio))
                    .background(colores.superficie)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BadgeIcono(
                    iconoDeEtapa(etapa.id),
                    colorFondo = colorEtapa,
                    colorIcono = colores.tinta,
                    tamano = 60.dp,
                    tamanoIcono = 26.dp
                )
                Spacer(Modifier.height(10.dp))
                Text(etapa.titulo, style = MaterialTheme.typography.displaySmall, color = colores.tinta, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(16.dp))
            TarjetaEtapaTexto("¿Por qué ocurre?", etapa.porQue, colores)

            Spacer(Modifier.height(14.dp))
            TarjetaEtapaTexto("¿Qué pasará?", etapa.quePasara, colores)

            Spacer(Modifier.height(14.dp))
            Card(
                shape = RoundedCornerShape(NubiGeometria.radio),
                colors = CardDefaults.cardColors(containerColor = colores.superficie),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Consejos", style = MaterialTheme.typography.labelLarge, color = colores.indigo)
                    Spacer(Modifier.height(10.dp))
                    etapa.consejos.forEachIndexed { indice, consejo ->
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(colorEtapa.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${indice + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colorEtapa
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                consejo,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colores.tinta,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (indice != etapa.consejos.lastIndex) Spacer(Modifier.height(10.dp))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Recuerda: cada bebé es único. Si tienes dudas, consulta siempre con tu pediatra.",
                style = MaterialTheme.typography.bodySmall,
                color = colores.tintaTenue
            )
        }
    }
}

@Composable
private fun TarjetaEtapaTexto(titulo: String, texto: String, colores: NubiColores) {
    Card(
        shape = RoundedCornerShape(NubiGeometria.radio),
        colors = CardDefaults.cardColors(containerColor = colores.superficie),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(titulo, style = MaterialTheme.typography.labelLarge, color = colores.indigo)
            Spacer(Modifier.height(8.dp))
            Text(texto, style = MaterialTheme.typography.bodyMedium, color = colores.tinta)
        }
    }
}

/** Qué dibujo e color le toca a cada etapa — ver MotorEtapas.kt. */
fun iconoDeEtapa(id: String): IconoNubi = when (id) {
    "brote_7dias" -> IconoNubi.NUBE
    "crisis_lactancia_3sem" -> IconoNubi.CORAZON
    "brote_6semanas" -> IconoNubi.NUBE
    "regresion_4meses" -> IconoNubi.LUNA
    "denticion_6meses" -> IconoNubi.GOTA
    "separacion_8meses" -> IconoNubi.CORAZON
    else -> IconoNubi.BOMBILLA
}

fun colorDeEtapa(color: ColorEtapa, c: NubiColores): Color = when (color) {
    ColorEtapa.MENTA -> c.menta
    ColorEtapa.MELOCOTON -> c.melocoton
    ColorEtapa.CIELO -> c.cielo
    ColorEtapa.LILA -> c.lila
    ColorEtapa.MANTEQUILLA -> c.mantequilla
    ColorEtapa.ROSA -> c.rosa
}
