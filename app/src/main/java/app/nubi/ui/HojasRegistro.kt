package app.nubi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nubi.data.ColorEtapa
import app.nubi.data.EtapaBebe
import app.nubi.data.LadoPecho
import app.nubi.data.LugarSueno
import app.nubi.data.Registro
import app.nubi.data.Repositorio
import app.nubi.data.TipoLeche
import app.nubi.data.TipoRegistro
import app.nubi.data.UnidadBiberon
import app.nubi.ui.theme.NubiColores
import app.nubi.ui.theme.NubiGeometria
import app.nubi.ui.theme.NubiTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
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
fun HojaBiberon(
    bebeId: String,
    dia: LocalDate? = null,
    horaInicial: Instant = Instant.now(),
    registro: Registro? = null,
    onCerrar: () -> Unit
) {
    val colores = NubiTheme.colores
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editando = registro != null

    var unidad by remember { mutableStateOf(registro?.unidadBiberon ?: UnidadBiberon.ML) }
    var tipoLeche by remember { mutableStateOf(registro?.tipoLeche ?: TipoLeche.MATERNA) }
    var cantidad by remember { mutableStateOf(registro?.cantidadBiberon ?: 120.0) }
    val horaLocalInicial = remember { (registro?.inicio ?: horaInicial).atZone(ZoneId.systemDefault()).toLocalTime() }
    var hora by remember { mutableIntStateOf(horaLocalInicial.hour) }
    var minuto by remember { mutableIntStateOf(horaLocalInicial.minute) }
    val diaBase = registro?.inicio?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: dia

    val sugerencias = if (unidad == UnidadBiberon.ML)
        listOf(30.0, 60.0, 90.0, 120.0, 150.0, 180.0, 210.0, 240.0)
    else
        listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0)
    val paso = if (unidad == UnidadBiberon.ML) 10.0 else 1.0
    val minimo = if (unidad == UnidadBiberon.ML) 10.0 else 1.0
    val maximo = if (unidad == UnidadBiberon.ML) 400.0 else 14.0

    fun guardar(conCantidad: Boolean) {
        val inicio = if (diaBase != null) instanteDe(diaBase, hora, minuto) else Instant.now()
        val base = registro ?: Registro(bebeId = bebeId, tipo = TipoRegistro.BIBERON, inicio = inicio)
        Repositorio.guardarRegistro(
            base.copy(
                inicio = inicio,
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
            if (editando) {
                BarraEditarRegistro(onCancelar = onCerrar)
                Spacer(Modifier.height(14.dp))
                CabeceraHojaEditar(IconoNubi.BIBERON, colores.mantequilla, "Biberón")
                Spacer(Modifier.height(14.dp))
                TarjetaHoraInicioEditar(hora, minuto, { hora = it }, { minuto = it })
                Spacer(Modifier.height(14.dp))
            } else {
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
                        if (dia != null) "Elige la hora del registro" else "¿Cuánto ha bebido?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colores.tintaSuave
                    )
                }
                Spacer(Modifier.height(18.dp))

                if (dia != null) {
                    RuedaHoraMinuto(hora = hora, minuto = minuto, onHoraCambiar = { hora = it }, onMinutoCambiar = { minuto = it })
                    Spacer(Modifier.height(18.dp))
                }
            }

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
fun HojaPanal(
    bebeId: String,
    dia: LocalDate? = null,
    horaInicial: Instant = Instant.now(),
    registro: Registro? = null,
    onCerrar: () -> Unit
) {
    val colores = NubiTheme.colores
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editando = registro != null
    var pis by remember { mutableStateOf(registro?.panalPis ?: true) }
    var caca by remember { mutableStateOf(registro?.panalCaca ?: false) }
    val horaLocalInicial = remember { (registro?.inicio ?: horaInicial).atZone(ZoneId.systemDefault()).toLocalTime() }
    var hora by remember { mutableIntStateOf(horaLocalInicial.hour) }
    var minuto by remember { mutableIntStateOf(horaLocalInicial.minute) }
    val diaBase = registro?.inicio?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: dia

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = sheetState, containerColor = colores.lienzo) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NubiGeometria.margen)
                .padding(bottom = 28.dp)
        ) {
            if (editando) {
                BarraEditarRegistro(onCancelar = onCerrar)
                Spacer(Modifier.height(14.dp))
                CabeceraHojaEditar(IconoNubi.GOTA, colores.cielo, "Pañal")
                Spacer(Modifier.height(14.dp))
                TarjetaHoraInicioEditar(hora, minuto, { hora = it }, { minuto = it })
                Spacer(Modifier.height(14.dp))
                EtiquetaSeccionHoja("¿Qué hizo?")
                Spacer(Modifier.height(8.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BadgeIcono(
                        IconoNubi.GOTA,
                        colorFondo = colores.cielo,
                        colorIcono = colores.tinta,
                        tamano = 40.dp,
                        tamanoIcono = 18.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (dia != null) "Pañal" else "¿Qué hizo?",
                        style = MaterialTheme.typography.titleLarge,
                        color = colores.tinta
                    )
                }
                Spacer(Modifier.height(18.dp))

                if (dia != null) {
                    Text(
                        "Elige la hora del registro",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colores.tintaSuave
                    )
                    Spacer(Modifier.height(10.dp))
                    RuedaHoraMinuto(hora = hora, minuto = minuto, onHoraCambiar = { hora = it }, onMinutoCambiar = { minuto = it })
                    Spacer(Modifier.height(18.dp))
                    EtiquetaSeccionHoja("¿Qué hizo?")
                    Spacer(Modifier.height(8.dp))
                }
            }

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
                    val inicio = if (diaBase != null) instanteDe(diaBase, hora, minuto) else Instant.now()
                    val base = registro ?: Registro(bebeId = bebeId, tipo = TipoRegistro.PANAL, inicio = inicio)
                    Repositorio.guardarRegistro(
                        base.copy(inicio = inicio, panalPis = pis, panalCaca = caca)
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

// =====================================================================
// Hojas de la pestaña Día — a diferencia de las de Hoy, todas dejan
// elegir la hora del registro (con la rueda de abajo) porque se puede
// estar apuntando un día distinto de hoy.
// =====================================================================

/** Combina un día y una hora:minuto sueltos en el instante que se guarda. */
private fun instanteDe(dia: LocalDate, hora: Int, minuto: Int): Instant =
    dia.atTime(hora, minuto).atZone(ZoneId.systemDefault()).toInstant()

/** Cabecera común a varias hojas de Día: icono, título y subtítulo centrados. */
@Composable
private fun CabeceraHoja(icono: IconoNubi, colorFondo: Color, titulo: String, subtitulo: String) {
    val colores = NubiTheme.colores
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BadgeIcono(icono, colorFondo = colorFondo, colorIcono = colores.tinta, tamano = 54.dp, tamanoIcono = 24.dp)
        Spacer(Modifier.height(10.dp))
        Text(titulo, style = MaterialTheme.typography.titleLarge, color = colores.tinta)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitulo,
            style = MaterialTheme.typography.bodyMedium,
            color = colores.tintaSuave,
            textAlign = TextAlign.Center
        )
    }
}

// =====================================================================
// Edición — al tocar un registro ya guardado en "Todo lo del día" se
// reabre la misma hoja de siempre, pero en modo edición: barra de arriba
// con "Cancelar" / "Editar registro", la cabecera metida en su propia
// tarjeta con un subtítulo fijo, y la hora de inicio siempre visible (se
// haya o no mostrado al crearlo) para poder corregirla sin perder el
// registro. Al guardar se actualiza el mismo registro (mismo id), nunca
// se crea uno nuevo.
// =====================================================================
const val SUBTITULO_EDITAR = "Corrige la hora sin perder el registro"

/** Barra superior de toda hoja en modo edición: "Cancelar" a la izquierda,
 *  título centrado — igual que en iOS. */
@Composable
fun BarraEditarRegistro(onCancelar: () -> Unit) {
    val colores = NubiTheme.colores
    Box(Modifier.fillMaxWidth()) {
        TextButton(onClick = onCancelar, modifier = Modifier.align(Alignment.CenterStart)) {
            Text("Cancelar", color = colores.lila, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            "Editar registro",
            style = MaterialTheme.typography.titleMedium,
            color = colores.tinta,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

/** La misma cabecera de siempre (icono + título), pero metida en su propia
 *  tarjeta y con el subtítulo fijo de edición — así se ve en iOS. */
@Composable
fun CabeceraHojaEditar(icono: IconoNubi, colorFondo: Color, titulo: String) {
    val colores = NubiTheme.colores
    Card(
        shape = RoundedCornerShape(NubiGeometria.radio),
        colors = CardDefaults.cardColors(containerColor = colores.superficie),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CabeceraHoja(icono, colorFondo, titulo, SUBTITULO_EDITAR)
        }
    }
}

/** La rueda de hora, en su propia tarjeta con etiqueta "Hora de inicio" —
 *  siempre visible en modo edición, aunque esa hoja normalmente no la
 *  muestre al crear un registro nuevo (p. ej. Biberón o Pañal al vuelo). */
@Composable
fun TarjetaHoraInicioEditar(hora: Int, minuto: Int, onHoraCambiar: (Int) -> Unit, onMinutoCambiar: (Int) -> Unit) {
    val colores = NubiTheme.colores
    Card(
        shape = RoundedCornerShape(NubiGeometria.radio),
        colors = CardDefaults.cardColors(containerColor = colores.superficie),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            EtiquetaSeccionHoja("Hora de inicio")
            Spacer(Modifier.height(6.dp))
            RuedaHoraMinuto(hora = hora, minuto = minuto, onHoraCambiar = onHoraCambiar, onMinutoCambiar = onMinutoCambiar)
        }
    }
}

// ---------------------------------------------------------------------
// La rueda de hora — el selector de dedos de iOS (.wheel), con dos
// columnas (horas y minutos) y una cápsula central fija detrás.
// ---------------------------------------------------------------------
@Composable
private fun RuedaHoraMinuto(
    hora: Int,
    minuto: Int,
    onHoraCambiar: (Int) -> Unit,
    onMinutoCambiar: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colores = NubiTheme.colores
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth(0.62f)
                .height(40.dp)
                .clip(RoundedCornerShape(50))
                .background(colores.lienzoAlto)
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            RuedaNumeros(cantidad = 24, valor = hora, onCambiar = onHoraCambiar)
            Spacer(Modifier.width(24.dp))
            RuedaNumeros(cantidad = 60, valor = minuto, onCambiar = onMinutoCambiar)
        }
    }
}

/**
 * Una columna de números que se arrastra con el dedo, con el de en medio
 * resaltado — la pieza básica de la rueda. Se detecta cuál queda centrado
 * mirando la posición real de los elementos visibles (layoutInfo), no el
 * índice del primero visible, para no depender de cómo ajuste el snap.
 */
@Composable
private fun RuedaNumeros(
    cantidad: Int,
    valor: Int,
    onCambiar: (Int) -> Unit
) {
    val colores = NubiTheme.colores
    val alturaFila = 40.dp
    val visibles = 5
    val estado = rememberLazyListState(initialFirstVisibleItemIndex = valor)

    val indiceCentrado by remember {
        derivedStateOf {
            val info = estado.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) {
                valor
            } else {
                val centro = (info.viewportStartOffset + info.viewportEndOffset) / 2
                info.visibleItemsInfo.minByOrNull { abs((it.offset + it.size / 2) - centro) }?.index ?: valor
            }
        }
    }

    LaunchedEffect(indiceCentrado, estado.isScrollInProgress) {
        if (!estado.isScrollInProgress && indiceCentrado != valor) {
            onCambiar(indiceCentrado)
        }
    }

    LazyColumn(
        state = estado,
        flingBehavior = rememberSnapFlingBehavior(estado),
        modifier = Modifier.height(alturaFila * visibles).width(56.dp),
        contentPadding = PaddingValues(vertical = alturaFila * (visibles / 2)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(cantidad) { indice ->
            val distancia = abs(indice - indiceCentrado)
            val alfa = when (distancia) {
                0 -> 1f
                1 -> 0.55f
                else -> 0.22f
            }
            Box(modifier = Modifier.height(alturaFila).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    indice.toString().padStart(2, '0'),
                    style = if (distancia == 0) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                    color = colores.tinta.copy(alpha = alfa),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** El selector "Día: Hoy ⌄" de Sueño nocturno — para marcar que se acostó
 *  la noche anterior. Solo tiene dos opciones, así que basta un menú. */
@Composable
private fun SelectorDiaRelativo(esAyer: Boolean, onCambiar: (Boolean) -> Unit) {
    val colores = NubiTheme.colores
    var expandido by remember { mutableStateOf(false) }
    Column {
        EtiquetaSeccionHoja("Día")
        Spacer(Modifier.height(6.dp))
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(NubiGeometria.radioChico))
                    .clickable { expandido = true }
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (esAyer) "Ayer" else "Hoy",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colores.indigo
                )
                Spacer(Modifier.width(4.dp))
                Icono(
                    IconoNubi.CHEVRON,
                    tint = colores.indigo,
                    tamano = 12.dp,
                    modifier = Modifier.graphicsLayer(rotationZ = 90f)
                )
            }
            DropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
                DropdownMenuItem(text = { Text("Hoy") }, onClick = { onCambiar(false); expandido = false })
                DropdownMenuItem(text = { Text("Ayer") }, onClick = { onCambiar(true); expandido = false })
            }
        }
    }
}

/** La fila "Aún está soñando" — deja el registro abierto (fin = null). */
@Composable
private fun FilaAunDurmiendo(activo: Boolean, onClick: () -> Unit) {
    val colores = NubiTheme.colores
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NubiGeometria.radio))
            .background(colores.superficie)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (activo) colores.lila else Color.Transparent)
                .border(1.5.dp, if (activo) colores.lila else colores.tintaTenue, CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Aún está soñando", style = MaterialTheme.typography.bodyLarge, color = colores.tinta)
            Text(
                "Se guarda abierto y aparece en la pantalla de bloqueo",
                style = MaterialTheme.typography.bodySmall,
                color = colores.tintaTenue
            )
        }
    }
}

/** Los contadores de horas y minutos de "Duración" — reutiliza el mismo
 *  botón circular +/- que ya tenía Biberón. */
@Composable
private fun BloqueDuracion(
    horas: Int,
    minutos: Int,
    onHorasCambiar: (Int) -> Unit,
    onMinutosCambiar: (Int) -> Unit
) {
    val colores = NubiTheme.colores
    Card(
        shape = RoundedCornerShape(NubiGeometria.radio),
        colors = CardDefaults.cardColors(containerColor = colores.superficie),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            EtiquetaSeccionHoja("Duración")
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ContadorDuracion("horas", horas, 0, 23) { onHorasCambiar(it) }
                ContadorDuracion("min", minutos, 0, 55) { onMinutosCambiar(it) }
            }
        }
    }
}

@Composable
private fun ContadorDuracion(etiqueta: String, valor: Int, minimo: Int, maximo: Int, onCambiar: (Int) -> Unit) {
    val colores = NubiTheme.colores
    val paso = if (etiqueta == "min") 5 else 1
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor.toString(), style = MaterialTheme.typography.displayMedium, color = colores.tinta)
        Text(etiqueta, style = MaterialTheme.typography.bodySmall, color = colores.tintaSuave)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BotonCirculoBiberon(masOMenos = false, colores = colores) {
                onCambiar((valor - paso).coerceIn(minimo, maximo))
            }
            BotonCirculoBiberon(masOMenos = true, colores = colores) {
                onCambiar((valor + paso).coerceIn(minimo, maximo))
            }
        }
    }
}

/** "¿Dónde se durmió?" — los 6 sitios de LugarSueno, en una rejilla 3x2. */
@Composable
private fun GrillaLugarSueno(lugar: LugarSueno?, onCambiar: (LugarSueno) -> Unit) {
    val colores = NubiTheme.colores
    LugarSueno.entries.chunked(3).forEach { fila ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fila.forEach { opcion ->
                val activo = lugar == opcion
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(NubiGeometria.radioChico))
                        .background(if (activo) colores.lila else colores.lienzoAlto)
                        .clickable { onCambiar(opcion) }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icono(iconoDeLugar(opcion), tint = if (activo) colores.tinta else colores.tintaTenue, tamano = 20.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        nombreLugar(opcion),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activo) colores.tinta else colores.tintaTenue
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun iconoDeLugar(lugar: LugarSueno): IconoNubi = when (lugar) {
    LugarSueno.BRAZOS -> IconoNubi.BRAZOS
    LugarSueno.PECHO -> IconoNubi.CORAZON
    LugarSueno.BIBERON -> IconoNubi.BIBERON
    LugarSueno.CUNA -> IconoNubi.CUNA
    LugarSueno.CARRITO -> IconoNubi.CARRITO
    LugarSueno.COCHE -> IconoNubi.COCHE
}

private fun nombreLugar(lugar: LugarSueno): String = when (lugar) {
    LugarSueno.BRAZOS -> "Brazos"
    LugarSueno.PECHO -> "Pecho"
    LugarSueno.BIBERON -> "Biberón"
    LugarSueno.CUNA -> "Cuna"
    LugarSueno.CARRITO -> "Carrito"
    LugarSueno.COCHE -> "Coche"
}

// =====================================================================
// Siesta — hora, "aún está soñando", duración y dónde se durmió.
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaSiestaDia(bebeId: String, dia: LocalDate, horaInicial: Instant = Instant.now(), registro: Registro? = null, onCerrar: () -> Unit) {
    val colores = NubiTheme.colores
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editando = registro != null
    val diaBase = registro?.inicio?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: dia
    val horaLocalInicial = remember { (registro?.inicio ?: horaInicial).atZone(ZoneId.systemDefault()).toLocalTime() }
    var hora by remember { mutableIntStateOf(horaLocalInicial.hour) }
    var minuto by remember { mutableIntStateOf(horaLocalInicial.minute) }
    var aunDurmiendo by remember { mutableStateOf(registro != null && registro.fin == null) }
    val duracionInicial = registro?.fin?.let { Duration.between(registro.inicio, it) }
    var horas by remember { mutableIntStateOf(duracionInicial?.toHours()?.toInt() ?: 0) }
    var minutos by remember { mutableIntStateOf(duracionInicial?.let { (it.toMinutes() % 60).toInt() } ?: 30) }
    var lugar by remember { mutableStateOf(registro?.lugarSueno) }

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = sheetState, containerColor = colores.lienzo) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NubiGeometria.margen)
                .padding(bottom = 28.dp)
        ) {
            if (editando) {
                BarraEditarRegistro(onCancelar = onCerrar)
                Spacer(Modifier.height(14.dp))
                CabeceraHojaEditar(IconoNubi.NUBE, colores.menta, "Siesta")
                Spacer(Modifier.height(14.dp))
                TarjetaHoraInicioEditar(hora, minuto, { hora = it }, { minuto = it })
                Spacer(Modifier.height(14.dp))
            } else {
                CabeceraHoja(IconoNubi.NUBE, colores.menta, "Siesta", "¿Cuándo durmió y cuánto?")
                Spacer(Modifier.height(18.dp))
                EtiquetaSeccionHoja("Hora")
                Spacer(Modifier.height(6.dp))
                RuedaHoraMinuto(hora = hora, minuto = minuto, onHoraCambiar = { hora = it }, onMinutoCambiar = { minuto = it })
                Spacer(Modifier.height(18.dp))
            }

            FilaAunDurmiendo(activo = aunDurmiendo) { aunDurmiendo = !aunDurmiendo }

            if (!aunDurmiendo) {
                Spacer(Modifier.height(18.dp))
                BloqueDuracion(horas, minutos, { horas = it }, { minutos = it })
            }

            Spacer(Modifier.height(18.dp))
            EtiquetaSeccionHoja("¿Dónde se durmió? · Opcional")
            Spacer(Modifier.height(8.dp))
            GrillaLugarSueno(lugar = lugar) { lugar = if (lugar == it) null else it }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    val inicio = instanteDe(diaBase, hora, minuto)
                    val fin = if (aunDurmiendo) null else inicio.plusSeconds((horas * 3600 + minutos * 60).toLong())
                    val base = registro ?: Registro(bebeId = bebeId, tipo = TipoRegistro.SIESTA, inicio = inicio)
                    Repositorio.guardarRegistro(
                        base.copy(inicio = inicio, fin = fin, lugarSueno = lugar)
                    )
                    onCerrar()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colores.menta, contentColor = colores.tinta),
                shape = RoundedCornerShape(NubiGeometria.radioChico),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar") }
        }
    }
}

// =====================================================================
// Sueño nocturno — igual que Siesta pero con selector de día (por si se
// acostó la noche anterior) y sin "dónde se durmió".
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaSuenoNocturnoDia(bebeId: String, dia: LocalDate, horaInicial: Instant = Instant.now(), registro: Registro? = null, onCerrar: () -> Unit) {
    val colores = NubiTheme.colores
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editando = registro != null
    val horaLocalInicial = remember { (registro?.inicio ?: horaInicial).atZone(ZoneId.systemDefault()).toLocalTime() }
    var hora by remember { mutableIntStateOf(horaLocalInicial.hour) }
    var minuto by remember { mutableIntStateOf(horaLocalInicial.minute) }
    var esAyer by remember { mutableStateOf(false) }
    var aunDurmiendo by remember { mutableStateOf(registro != null && registro.fin == null) }
    val duracionInicial = registro?.fin?.let { Duration.between(registro.inicio, it) }
    var horas by remember { mutableIntStateOf(duracionInicial?.toHours()?.toInt() ?: 0) }
    var minutos by remember { mutableIntStateOf(duracionInicial?.let { (it.toMinutes() % 60).toInt() } ?: 30) }
    // Editando, el día base es el mismo en el que ya estaba el registro;
    // no hace falta el selector de "ayer/hoy", que es solo para crearlo.
    val diaPropio = registro?.inicio?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: dia

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = sheetState, containerColor = colores.lienzo) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NubiGeometria.margen)
                .padding(bottom = 28.dp)
        ) {
            if (editando) {
                BarraEditarRegistro(onCancelar = onCerrar)
                Spacer(Modifier.height(14.dp))
                CabeceraHojaEditar(IconoNubi.LUNA, colores.lila, "Sueño nocturno")
                Spacer(Modifier.height(14.dp))
                TarjetaHoraInicioEditar(hora, minuto, { hora = it }, { minuto = it })
                Spacer(Modifier.height(14.dp))
            } else {
                CabeceraHoja(IconoNubi.LUNA, colores.lila, "Sueño nocturno", "¿Cuándo durmió y cuánto?")
                Spacer(Modifier.height(18.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    SelectorDiaRelativo(esAyer = esAyer, onCambiar = { esAyer = it })
                    Spacer(Modifier.width(10.dp))
                    RuedaHoraMinuto(
                        hora = hora, minuto = minuto,
                        onHoraCambiar = { hora = it }, onMinutoCambiar = { minuto = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(18.dp))
            }

            FilaAunDurmiendo(activo = aunDurmiendo) { aunDurmiendo = !aunDurmiendo }

            if (!aunDurmiendo) {
                Spacer(Modifier.height(18.dp))
                BloqueDuracion(horas, minutos, { horas = it }, { minutos = it })
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    val diaBase = if (editando) diaPropio else if (esAyer) dia.minusDays(1) else dia
                    val inicio = instanteDe(diaBase, hora, minuto)
                    val fin = if (aunDurmiendo) null else inicio.plusSeconds((horas * 3600 + minutos * 60).toLong())
                    val base = registro ?: Registro(bebeId = bebeId, tipo = TipoRegistro.NOCHE, inicio = inicio)
                    Repositorio.guardarRegistro(base.copy(inicio = inicio, fin = fin))
                    onCerrar()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colores.lila, contentColor = colores.tinta),
                shape = RoundedCornerShape(NubiGeometria.radioChico),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar") }
        }
    }
}

// =====================================================================
// Despertar nocturno — hora + duración del despertar, con un resumen.
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaDespertarNocturno(bebeId: String, dia: LocalDate, horaInicial: Instant = Instant.now(), registro: Registro? = null, onCerrar: () -> Unit) {
    val colores = NubiTheme.colores
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editando = registro != null
    val diaBase = registro?.inicio?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: dia
    val horaLocalInicial = remember { (registro?.inicio ?: horaInicial).atZone(ZoneId.systemDefault()).toLocalTime() }
    var hora by remember { mutableIntStateOf(horaLocalInicial.hour) }
    var minuto by remember { mutableIntStateOf(horaLocalInicial.minute) }
    val duracionInicial = registro?.fin?.let { Duration.between(registro.inicio, it) }
    var duracionMin by remember { mutableIntStateOf(duracionInicial?.toMinutes()?.toInt() ?: 5) }

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = sheetState, containerColor = colores.lienzo) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NubiGeometria.margen)
                .padding(bottom = 28.dp)
        ) {
            if (editando) {
                BarraEditarRegistro(onCancelar = onCerrar)
                Spacer(Modifier.height(14.dp))
                CabeceraHojaEditar(IconoNubi.LUNA, colores.coral, "Despertar nocturno")
                Spacer(Modifier.height(14.dp))
                TarjetaHoraInicioEditar(hora, minuto, { hora = it }, { minuto = it })
                Spacer(Modifier.height(14.dp))
            } else {
                CabeceraHoja(IconoNubi.LUNA, colores.coral, "Despertar nocturno", "¿A qué hora se despertó y cuánto duró?")
                Spacer(Modifier.height(18.dp))

                EtiquetaSeccionHoja("Hora del despertar")
                Spacer(Modifier.height(6.dp))
                RuedaHoraMinuto(hora = hora, minuto = minuto, onHoraCambiar = { hora = it }, onMinutoCambiar = { minuto = it })
                Spacer(Modifier.height(18.dp))
            }
            EtiquetaSeccionHoja("Duración del despertar")
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BotonCirculoBiberon(masOMenos = false, colores = colores) {
                    duracionMin = (duracionMin - 5).coerceIn(0, 180)
                }
                Spacer(Modifier.width(18.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(duracionMin.toString(), style = MaterialTheme.typography.displayMedium, color = colores.tinta)
                    Text("min", style = MaterialTheme.typography.bodySmall, color = colores.tintaSuave)
                }
                Spacer(Modifier.width(18.dp))
                BotonCirculoBiberon(masOMenos = true, colores = colores) {
                    duracionMin = (duracionMin + 5).coerceIn(0, 180)
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(NubiGeometria.radio))
                    .background(colores.superficie)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Resumen:", style = MaterialTheme.typography.bodyMedium, color = colores.tintaSuave)
                Text(
                    "%02d:%02d · %d min".format(hora, minuto, duracionMin),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.tinta
                )
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    val inicio = instanteDe(diaBase, hora, minuto)
                    val base = registro ?: Registro(bebeId = bebeId, tipo = TipoRegistro.DESPERTAR, inicio = inicio)
                    Repositorio.guardarRegistro(
                        base.copy(inicio = inicio, fin = inicio.plusSeconds(duracionMin * 60L))
                    )
                    onCerrar()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colores.coral, contentColor = colores.tinta),
                shape = RoundedCornerShape(NubiGeometria.radioChico),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar") }
        }
    }
}

// =====================================================================
// Baño — hora + cómo lo llevó (opcional).
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaBanoDia(bebeId: String, dia: LocalDate, horaInicial: Instant = Instant.now(), registro: Registro? = null, onCerrar: () -> Unit) {
    val colores = NubiTheme.colores
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editando = registro != null
    val diaBase = registro?.inicio?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: dia
    val horaLocalInicial = remember { (registro?.inicio ?: horaInicial).atZone(ZoneId.systemDefault()).toLocalTime() }
    var hora by remember { mutableIntStateOf(horaLocalInicial.hour) }
    var minuto by remember { mutableIntStateOf(horaLocalInicial.minute) }
    var contento by remember { mutableStateOf(registro?.banoContento) }

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = sheetState, containerColor = colores.lienzo) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NubiGeometria.margen)
                .padding(bottom = 28.dp)
        ) {
            if (editando) {
                BarraEditarRegistro(onCancelar = onCerrar)
                Spacer(Modifier.height(14.dp))
                CabeceraHojaEditar(IconoNubi.BANO, colores.rosa, "Baño")
                Spacer(Modifier.height(14.dp))
                TarjetaHoraInicioEditar(hora, minuto, { hora = it }, { minuto = it })
                Spacer(Modifier.height(14.dp))
            } else {
                CabeceraHoja(IconoNubi.BANO, colores.rosa, "Baño", "Elige la hora del registro")
                Spacer(Modifier.height(18.dp))
                EtiquetaSeccionHoja("Hora")
                Spacer(Modifier.height(6.dp))
                RuedaHoraMinuto(hora = hora, minuto = minuto, onHoraCambiar = { hora = it }, onMinutoCambiar = { minuto = it })
                Spacer(Modifier.height(18.dp))
            }
            EtiquetaSeccionHoja("¿Cómo lo llevó? · Opcional")
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TogglePanal(
                    icono = IconoNubi.CONTENTO,
                    etiqueta = "Contento",
                    activo = contento == true,
                    color = colores.menta,
                    colores = colores,
                    modifier = Modifier.weight(1f)
                ) { contento = if (contento == true) null else true }
                TogglePanal(
                    icono = IconoNubi.MOLESTO,
                    etiqueta = "Molesto",
                    activo = contento == false,
                    color = colores.coral,
                    colores = colores,
                    modifier = Modifier.weight(1f)
                ) { contento = if (contento == false) null else false }
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    val inicio = instanteDe(diaBase, hora, minuto)
                    val base = registro ?: Registro(bebeId = bebeId, tipo = TipoRegistro.BANO, inicio = inicio)
                    Repositorio.guardarRegistro(base.copy(inicio = inicio, banoContento = contento))
                    onCerrar()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colores.rosa, contentColor = colores.tinta),
                shape = RoundedCornerShape(NubiGeometria.radioChico),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar") }
        }
    }
}

// =====================================================================
// Pecho en la pestaña Día — se elige el lado en un menú anclado al botón
// de la botonera (ver BotoneraDia en Pantallas.kt) y luego se abre esta
// hoja para elegir la hora, igual que las demás.
// =====================================================================
@Composable
fun MenuPecho(expandido: Boolean, onCerrar: () -> Unit, onElegir: (LadoPecho) -> Unit) {
    DropdownMenu(expanded = expandido, onDismissRequest = onCerrar) {
        FilaMenuPecho("Pecho izquierdo") { onElegir(LadoPecho.IZQUIERDA) }
        FilaMenuPecho("Pecho derecho") { onElegir(LadoPecho.DERECHA) }
    }
}

@Composable
private fun FilaMenuPecho(titulo: String, onClick: () -> Unit) {
    val colores = NubiTheme.colores
    DropdownMenuItem(
        text = {
            Column {
                Text(titulo, style = MaterialTheme.typography.bodyLarge, color = colores.tinta)
                Text("Elegir hora", style = MaterialTheme.typography.bodySmall, color = colores.tintaTenue)
            }
        },
        leadingIcon = {
            BadgeIcono(
                IconoNubi.CORAZON,
                colorFondo = colores.melocoton,
                colorIcono = colores.tinta,
                tamano = 40.dp,
                tamanoIcono = 18.dp
            )
        },
        trailingIcon = { Icono(IconoNubi.RELOJ, tint = colores.tintaTenue, tamano = 16.dp) },
        onClick = onClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaPechoHora(
    bebeId: String,
    lado: LadoPecho,
    dia: LocalDate,
    horaInicial: Instant = Instant.now(),
    registro: Registro? = null,
    onCerrar: () -> Unit
) {
    val colores = NubiTheme.colores
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editando = registro != null
    val diaBase = registro?.inicio?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: dia
    val horaLocalInicial = remember { (registro?.inicio ?: horaInicial).atZone(ZoneId.systemDefault()).toLocalTime() }
    var hora by remember { mutableIntStateOf(horaLocalInicial.hour) }
    var minuto by remember { mutableIntStateOf(horaLocalInicial.minute) }
    val titulo = if (lado == LadoPecho.IZQUIERDA) "Pecho izquierdo" else "Pecho derecho"

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = sheetState, containerColor = colores.lienzo) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NubiGeometria.margen)
                .padding(bottom = 28.dp)
        ) {
            if (editando) {
                BarraEditarRegistro(onCancelar = onCerrar)
                Spacer(Modifier.height(14.dp))
                CabeceraHojaEditar(IconoNubi.CORAZON, colores.melocoton, titulo)
                Spacer(Modifier.height(14.dp))
                TarjetaHoraInicioEditar(hora, minuto, { hora = it }, { minuto = it })
            } else {
                CabeceraHoja(IconoNubi.CORAZON, colores.melocoton, titulo, "Elige la hora del registro")
                Spacer(Modifier.height(18.dp))
                RuedaHoraMinuto(hora = hora, minuto = minuto, onHoraCambiar = { hora = it }, onMinutoCambiar = { minuto = it })
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    val inicio = instanteDe(diaBase, hora, minuto)
                    val base = registro ?: Registro(bebeId = bebeId, tipo = TipoRegistro.PECHO, inicio = inicio)
                    Repositorio.guardarRegistro(base.copy(inicio = inicio, lado = lado))
                    onCerrar()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colores.melocoton, contentColor = colores.tinta),
                shape = RoundedCornerShape(NubiGeometria.radioChico),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar") }
        }
    }
}

// =====================================================================
// Bloqueo por prueba/premium — se abre en vez de la hoja de registro
// cuando se acaban los 7 días de prueba y no hay premium activo (ver
// Repositorio.puedeUsarApp). iOS todavía no tiene una pantalla para esto:
// este diseño es nuevo y a falta de que Alejandro lo revise.
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaPremiumBloqueado(onCerrar: () -> Unit) {
    val colores = NubiTheme.colores
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = sheetState, containerColor = colores.lienzo) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NubiGeometria.margen)
                .padding(bottom = 28.dp)
        ) {
            CabeceraHoja(
                IconoNubi.CANDADO,
                colores.lila,
                "Se acabó tu prueba gratuita",
                "Han pasado tus 7 días de prueba. Canjea un código de regalo o hazte premium para seguir registrando."
            )
            Spacer(Modifier.height(20.dp))
            BloqueCanjearCodigo(
                alCanjear = {
                    Repositorio.sincronizarEnSegundoPlano()
                    onCerrar()
                }
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Suscripción mensual, trimestral o anual: muy pronto.",
                style = MaterialTheme.typography.bodySmall,
                color = colores.tintaTenue,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
