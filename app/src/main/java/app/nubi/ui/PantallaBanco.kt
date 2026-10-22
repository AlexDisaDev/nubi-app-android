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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nubi.data.AlmacenamientoLeche
import app.nubi.data.Bebe
import app.nubi.data.ExtraccionLeche
import app.nubi.data.Repositorio
import app.nubi.data.UnidadBiberon
import app.nubi.data.UsoLeche
import app.nubi.data.nuevoId
import app.nubi.ui.theme.NubiColores
import app.nubi.ui.theme.NubiGeometria
import app.nubi.ui.theme.NubiTheme
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pestaña de arriba "Banco de leche": cuánta leche extraída hay disponible
 * (extracciones menos usos), y el registro de ambos movimientos.
 *
 * El diseño de la tarjeta de total, el toggle ml/oz, los botones
 * "+ Extracción"/"Ordenar" y la hoja "Nueva extracción" son un porte
 * exacto de las capturas de iOS que mandó Alejandro. La lista de
 * movimientos de abajo y el botón de "+ Uso" NO tienen captura de
 * referencia (las dos que mandó estaban vacías, en 0): son diseño propio,
 * a la espera de que llegue una captura con datos para ajustarlos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaBanco(bebe: Bebe, extracciones: List<ExtraccionLeche>, usosLeche: List<UsoLeche>) {
    val colores = NubiTheme.colores
    var unidad by remember { mutableStateOf(UnidadBiberon.OZ) }
    var orden by remember { mutableStateOf(OrdenBanco.MAS_RECIENTE) }
    var mostrarOrdenar by remember { mutableStateOf(false) }
    var mostrarNuevaExtraccion by remember { mutableStateOf(false) }
    var mostrarNuevoUso by remember { mutableStateOf(false) }
    var extraccionEditando by remember { mutableStateOf<ExtraccionLeche?>(null) }
    var usoEditando by remember { mutableStateOf<UsoLeche?>(null) }

    val totalMl = extracciones.sumOf { it.cantidadMl } - usosLeche.sumOf { it.cantidadMl }

    val movimientos = remember(extracciones, usosLeche, orden) {
        val todos: List<Movimiento> = extracciones.map { Movimiento.Extraccion(it) } + usosLeche.map { Movimiento.Uso(it) }
        when (orden) {
            OrdenBanco.MAS_RECIENTE -> todos.sortedByDescending { it.fecha }
            OrdenBanco.MENOS_RECIENTE -> todos.sortedBy { it.fecha }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(NubiGeometria.margen)
    ) {
        Text("Banco de leche", style = MaterialTheme.typography.headlineLarge, color = colores.tinta)
        Spacer(Modifier.height(18.dp))

        Card(
            shape = RoundedCornerShape(NubiGeometria.radio),
            colors = CardDefaults.cardColors(containerColor = colores.superficie),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        textoCantidadBanco(totalMl, unidad),
                        style = MaterialTheme.typography.displayLarge,
                        color = colores.tinta
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        unidad.etiqueta,
                        style = MaterialTheme.typography.titleLarge,
                        color = colores.tinta,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "disponibles en el banco",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.tintaSuave
                )
                Spacer(Modifier.height(14.dp))
                Box(modifier = Modifier.widthIn(max = 180.dp)) {
                    SelectorSegmentadoBanco(
                        opciones = listOf(UnidadBiberon.ML to "ml", UnidadBiberon.OZ to "oz"),
                        actual = unidad,
                        onCambiar = { unidad = it }
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    Text(
                        "${extracciones.size} extracciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.tintaTenue
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "${usosLeche.size} usos",
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.tintaTenue
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { mostrarNuevaExtraccion = true },
                colors = ButtonDefaults.buttonColors(containerColor = colores.cielo, contentColor = colores.tinta),
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f)
            ) {
                Icono(IconoNubi.MAS, tint = colores.tinta, tamano = 16.dp)
                Spacer(Modifier.width(6.dp))
                Text("Extracción", fontWeight = FontWeight.SemiBold)
            }

            Box(modifier = Modifier.weight(1f)) {
                Button(
                    onClick = { mostrarOrdenar = true },
                    colors = ButtonDefaults.buttonColors(containerColor = colores.mantequilla, contentColor = colores.tinta),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icono(IconoNubi.RELOJ, tint = colores.tinta, tamano = 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Ordenar", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(4.dp))
                    Icono(IconoNubi.CHEVRON, tint = colores.tinta, tamano = 11.dp, modifier = Modifier.rotate(90f))
                }
                DropdownMenu(expanded = mostrarOrdenar, onDismissRequest = { mostrarOrdenar = false }) {
                    DropdownMenuItem(
                        text = { Text("Más reciente") },
                        leadingIcon = { if (orden == OrdenBanco.MAS_RECIENTE) Icono(IconoNubi.CHECK, tint = colores.lila, tamano = 16.dp) },
                        onClick = { orden = OrdenBanco.MAS_RECIENTE; mostrarOrdenar = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Menos reciente") },
                        leadingIcon = { if (orden == OrdenBanco.MENOS_RECIENTE) Icono(IconoNubi.CHECK, tint = colores.lila, tamano = 16.dp) },
                        onClick = { orden = OrdenBanco.MENOS_RECIENTE; mostrarOrdenar = false }
                    )
                }
            }
        }

        // No hay captura de iOS para registrar un "uso": este botón, más
        // discreto que los dos de arriba, es diseño propio.
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = { mostrarNuevoUso = true }, modifier = Modifier.fillMaxWidth()) {
            Icono(IconoNubi.MAS, tint = colores.tintaSuave, tamano = 14.dp)
            Spacer(Modifier.width(6.dp))
            Text("Registrar uso", color = colores.tintaSuave)
        }

        if (movimientos.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            movimientos.forEach { mov ->
                FilaMovimientoBanco(
                    movimiento = mov,
                    unidad = unidad,
                    onEditar = {
                        when (mov) {
                            is Movimiento.Extraccion -> extraccionEditando = mov.dato
                            is Movimiento.Uso -> usoEditando = mov.dato
                        }
                    },
                    onBorrar = {
                        when (mov) {
                            is Movimiento.Extraccion -> Repositorio.borrarExtraccion(mov.dato.id)
                            is Movimiento.Uso -> Repositorio.borrarUsoLeche(mov.dato.id)
                        }
                    }
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    if (mostrarNuevaExtraccion) {
        HojaExtraccion(bebeId = bebe.id, unidadInicial = unidad, onCerrar = { mostrarNuevaExtraccion = false })
    }
    extraccionEditando?.let { ext ->
        HojaExtraccion(
            bebeId = bebe.id,
            extraccion = ext,
            unidadInicial = unidad,
            onCerrar = { extraccionEditando = null }
        )
    }
    if (mostrarNuevoUso) {
        HojaUso(bebeId = bebe.id, unidadInicial = unidad, onCerrar = { mostrarNuevoUso = false })
    }
    usoEditando?.let { uso ->
        HojaUso(bebeId = bebe.id, uso = uso, unidadInicial = unidad, onCerrar = { usoEditando = null })
    }
}

private enum class OrdenBanco { MAS_RECIENTE, MENOS_RECIENTE }

private sealed interface Movimiento {
    val fecha: Instant
    data class Extraccion(val dato: ExtraccionLeche) : Movimiento {
        override val fecha get() = dato.fecha
    }
    data class Uso(val dato: UsoLeche) : Movimiento {
        override val fecha get() = dato.fecha
    }
}

@Composable
private fun FilaMovimientoBanco(
    movimiento: Movimiento,
    unidad: UnidadBiberon,
    onEditar: () -> Unit,
    onBorrar: () -> Unit
) {
    val colores = NubiTheme.colores
    val esExtraccion = movimiento is Movimiento.Extraccion
    val cantidadMl = when (movimiento) {
        is Movimiento.Extraccion -> movimiento.dato.cantidadMl
        is Movimiento.Uso -> movimiento.dato.cantidadMl
    }
    val fecha = movimiento.fecha

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEditar)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BadgeIcono(
            IconoNubi.BIBERON,
            colorFondo = if (esExtraccion) colores.cielo else colores.melocoton,
            colorIcono = colores.tinta,
            tamano = 40.dp,
            tamanoIcono = 18.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (esExtraccion) "Extracción" else "Uso",
                style = MaterialTheme.typography.bodyLarge,
                color = colores.tinta
            )
            Spacer(Modifier.height(2.dp))
            val subtitulo = buildString {
                append(textoFechaHoraBanco(fecha))
                if (movimiento is Movimiento.Extraccion) {
                    append(" · ")
                    append(if (movimiento.dato.almacenamiento == AlmacenamientoLeche.CONGELADOR) "Congelador" else "Frigorífico")
                }
            }
            Text(subtitulo, style = MaterialTheme.typography.bodyMedium, color = colores.tintaSuave)
        }
        Text(
            "${if (esExtraccion) "+" else "−"}${textoCantidadBanco(cantidadMl, unidad)} ${unidad.etiqueta}",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (esExtraccion) colores.tinta else colores.tintaSuave
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onBorrar),
            contentAlignment = Alignment.Center
        ) {
            Icono(IconoNubi.EQUIS, tint = colores.tintaTenue, tamano = 11.dp)
        }
    }
}

// =====================================================================
// Hoja "Nueva extracción" — porte exacto de la captura de iOS.
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaExtraccion(
    bebeId: String,
    extraccion: ExtraccionLeche? = null,
    unidadInicial: UnidadBiberon = UnidadBiberon.OZ,
    onCerrar: () -> Unit
) {
    val colores = NubiTheme.colores
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editando = extraccion != null

    var unidad by remember { mutableStateOf(unidadInicial) }
    var cantidad by remember {
        mutableDoubleStateOf(
            extraccion?.let { it.cantidadMl / unidadInicial.factor } ?: if (unidadInicial == UnidadBiberon.ML) 120.0 else 4.0
        )
    }
    var almacenamiento by remember { mutableStateOf(extraccion?.almacenamiento ?: AlmacenamientoLeche.CONGELADOR) }
    var fecha by remember { mutableStateOf(extraccion?.fecha ?: Instant.now()) }
    var mostrarSelectorFecha by remember { mutableStateOf(false) }
    var mostrarSelectorHora by remember { mutableStateOf(false) }

    val paso = if (unidad == UnidadBiberon.ML) 10.0 else 1.0
    val minimo = if (unidad == UnidadBiberon.ML) 10.0 else 1.0
    val maximo = if (unidad == UnidadBiberon.ML) 500.0 else 17.0

    fun guardar() {
        val cantidadMl = cantidad * unidad.factor
        val base = extraccion ?: ExtraccionLeche(id = nuevoId(), bebeId = bebeId, fecha = fecha, cantidadMl = cantidadMl)
        Repositorio.guardarExtraccion(base.copy(fecha = fecha, cantidadMl = cantidadMl, almacenamiento = almacenamiento))
        onCerrar()
    }

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = sheetState, containerColor = colores.lienzo) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NubiGeometria.margen)
                .padding(bottom = 28.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCerrar) { Text("Cancelar") }
                Spacer(Modifier.weight(1f))
                Text(
                    if (editando) "Editar extracción" else "Extracción",
                    style = MaterialTheme.typography.titleMedium,
                    color = colores.tinta
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(64.dp))
            }
            Spacer(Modifier.height(10.dp))

            Card(
                shape = RoundedCornerShape(NubiGeometria.radio),
                colors = CardDefaults.cardColors(containerColor = colores.superficie),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BadgeIcono(
                        IconoNubi.BIBERON,
                        colorFondo = colores.cielo,
                        colorIcono = colores.tinta,
                        tamano = 54.dp,
                        tamanoIcono = 24.dp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (editando) "Editar extracción" else "Nueva extracción",
                        style = MaterialTheme.typography.titleLarge,
                        color = colores.tinta
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            Card(
                shape = RoundedCornerShape(NubiGeometria.radio),
                colors = CardDefaults.cardColors(containerColor = colores.superficie),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    EtiquetaSeccionBanco("Fecha y hora")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChipFechaHoraBanco(textoFechaConAnoBanco(fecha)) { mostrarSelectorFecha = true }
                        ChipFechaHoraBanco(formatoHoraBanco.format(fecha)) { mostrarSelectorHora = true }
                    }

                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EtiquetaSeccionBanco("Cantidad")
                        Box(modifier = Modifier.widthIn(max = 140.dp)) {
                            SelectorSegmentadoBanco(
                                opciones = listOf(UnidadBiberon.ML to "ml", UnidadBiberon.OZ to "oz"),
                                actual = unidad,
                                onCambiar = { nueva ->
                                    val cantidadMl = cantidad * unidad.factor
                                    unidad = nueva
                                    cantidad = cantidadMl / nueva.factor
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BotonCirculoBanco(masOMenos = false, colores = colores) {
                            cantidad = (cantidad - paso).coerceAtLeast(minimo)
                        }
                        Text(
                            "${textoCantidadBanco(cantidad * unidad.factor, unidad)} ${unidad.etiqueta}",
                            style = MaterialTheme.typography.displayMedium,
                            color = colores.tinta
                        )
                        BotonCirculoBanco(masOMenos = true, colores = colores) {
                            cantidad = (cantidad + paso).coerceAtMost(maximo)
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    EtiquetaSeccionBanco("Almacenamiento")
                    Spacer(Modifier.height(8.dp))
                    SelectorSegmentadoBanco(
                        opciones = listOf(
                            AlmacenamientoLeche.FRIGORIFICO to "Frigorífico",
                            AlmacenamientoLeche.CONGELADOR to "Congelador"
                        ),
                        actual = almacenamiento,
                        onCambiar = { almacenamiento = it }
                    )
                    Spacer(Modifier.height(6.dp))
                    val textoDuracion = if (almacenamiento == AlmacenamientoLeche.FRIGORIFICO)
                        "Dura hasta ${almacenamiento.duracionDias} días en el frigorífico"
                    else
                        "Dura hasta ${almacenamiento.duracionDias / 30} meses en el congelador"
                    Text(textoDuracion, style = MaterialTheme.typography.bodySmall, color = colores.tintaTenue)
                }
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { guardar() },
                colors = ButtonDefaults.buttonColors(containerColor = colores.cielo, contentColor = colores.tinta),
                shape = RoundedCornerShape(NubiGeometria.radioChico),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar") }
        }
    }

    if (mostrarSelectorFecha) {
        val estado = rememberDatePickerState(initialSelectedDateMillis = fechaAMillisUtcBanco(fecha))
        DatePickerDialog(
            onDismissRequest = { mostrarSelectorFecha = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { millis ->
                        val diaNuevo = millisUtcAFechaBanco(millis).atZone(zonaBanco).toLocalDate()
                        val horaVieja = fecha.atZone(zonaBanco).toLocalTime()
                        fecha = diaNuevo.atTime(horaVieja).atZone(zonaBanco).toInstant()
                    }
                    mostrarSelectorFecha = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { mostrarSelectorFecha = false }) { Text("Cancelar") } }
        ) { DatePicker(state = estado) }
    }

    if (mostrarSelectorHora) {
        val horaActual = fecha.atZone(zonaBanco).toLocalTime()
        val estado = rememberTimePickerState(initialHour = horaActual.hour, initialMinute = horaActual.minute, is24Hour = true)
        DatePickerDialog(
            onDismissRequest = { mostrarSelectorHora = false },
            confirmButton = {
                TextButton(onClick = {
                    val dia = fecha.atZone(zonaBanco).toLocalDate()
                    fecha = dia.atTime(estado.hour, estado.minute).atZone(zonaBanco).toInstant()
                    mostrarSelectorHora = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { mostrarSelectorHora = false }) { Text("Cancelar") } }
        ) { TimePicker(state = estado) }
    }
}

// =====================================================================
// Hoja "Nuevo uso" — diseño propio (no hay captura de iOS): mismos
// campos que la extracción salvo el almacenamiento, que no aplica.
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaUso(
    bebeId: String,
    uso: UsoLeche? = null,
    unidadInicial: UnidadBiberon = UnidadBiberon.OZ,
    onCerrar: () -> Unit
) {
    val colores = NubiTheme.colores
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editando = uso != null

    var unidad by remember { mutableStateOf(unidadInicial) }
    var cantidad by remember {
        mutableDoubleStateOf(
            uso?.let { it.cantidadMl / unidadInicial.factor } ?: if (unidadInicial == UnidadBiberon.ML) 120.0 else 4.0
        )
    }
    var fecha by remember { mutableStateOf(uso?.fecha ?: Instant.now()) }
    var mostrarSelectorFecha by remember { mutableStateOf(false) }
    var mostrarSelectorHora by remember { mutableStateOf(false) }

    val paso = if (unidad == UnidadBiberon.ML) 10.0 else 1.0
    val minimo = if (unidad == UnidadBiberon.ML) 10.0 else 1.0
    val maximo = if (unidad == UnidadBiberon.ML) 500.0 else 17.0

    fun guardar() {
        val cantidadMl = cantidad * unidad.factor
        val base = uso ?: UsoLeche(id = nuevoId(), bebeId = bebeId, fecha = fecha, cantidadMl = cantidadMl)
        Repositorio.guardarUsoLeche(base.copy(fecha = fecha, cantidadMl = cantidadMl))
        onCerrar()
    }

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = sheetState, containerColor = colores.lienzo) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NubiGeometria.margen)
                .padding(bottom = 28.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCerrar) { Text("Cancelar") }
                Spacer(Modifier.weight(1f))
                Text(
                    if (editando) "Editar uso" else "Uso de leche",
                    style = MaterialTheme.typography.titleMedium,
                    color = colores.tinta
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(64.dp))
            }
            Spacer(Modifier.height(10.dp))

            Card(
                shape = RoundedCornerShape(NubiGeometria.radio),
                colors = CardDefaults.cardColors(containerColor = colores.superficie),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    EtiquetaSeccionBanco("Fecha y hora")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChipFechaHoraBanco(textoFechaConAnoBanco(fecha)) { mostrarSelectorFecha = true }
                        ChipFechaHoraBanco(formatoHoraBanco.format(fecha)) { mostrarSelectorHora = true }
                    }

                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EtiquetaSeccionBanco("Cantidad")
                        Box(modifier = Modifier.widthIn(max = 140.dp)) {
                            SelectorSegmentadoBanco(
                                opciones = listOf(UnidadBiberon.ML to "ml", UnidadBiberon.OZ to "oz"),
                                actual = unidad,
                                onCambiar = { nueva ->
                                    val cantidadMl = cantidad * unidad.factor
                                    unidad = nueva
                                    cantidad = cantidadMl / nueva.factor
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BotonCirculoBanco(masOMenos = false, colores = colores) {
                            cantidad = (cantidad - paso).coerceAtLeast(minimo)
                        }
                        Text(
                            "${textoCantidadBanco(cantidad * unidad.factor, unidad)} ${unidad.etiqueta}",
                            style = MaterialTheme.typography.displayMedium,
                            color = colores.tinta
                        )
                        BotonCirculoBanco(masOMenos = true, colores = colores) {
                            cantidad = (cantidad + paso).coerceAtMost(maximo)
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { guardar() },
                colors = ButtonDefaults.buttonColors(containerColor = colores.melocoton, contentColor = colores.tinta),
                shape = RoundedCornerShape(NubiGeometria.radioChico),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar") }
        }
    }

    if (mostrarSelectorFecha) {
        val estado = rememberDatePickerState(initialSelectedDateMillis = fechaAMillisUtcBanco(fecha))
        DatePickerDialog(
            onDismissRequest = { mostrarSelectorFecha = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { millis ->
                        val diaNuevo = millisUtcAFechaBanco(millis).atZone(zonaBanco).toLocalDate()
                        val horaVieja = fecha.atZone(zonaBanco).toLocalTime()
                        fecha = diaNuevo.atTime(horaVieja).atZone(zonaBanco).toInstant()
                    }
                    mostrarSelectorFecha = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { mostrarSelectorFecha = false }) { Text("Cancelar") } }
        ) { DatePicker(state = estado) }
    }

    if (mostrarSelectorHora) {
        val horaActual = fecha.atZone(zonaBanco).toLocalTime()
        val estado = rememberTimePickerState(initialHour = horaActual.hour, initialMinute = horaActual.minute, is24Hour = true)
        DatePickerDialog(
            onDismissRequest = { mostrarSelectorHora = false },
            confirmButton = {
                TextButton(onClick = {
                    val dia = fecha.atZone(zonaBanco).toLocalDate()
                    fecha = dia.atTime(estado.hour, estado.minute).atZone(zonaBanco).toInstant()
                    mostrarSelectorHora = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { mostrarSelectorHora = false }) { Text("Cancelar") } }
        ) { TimePicker(state = estado) }
    }
}

// =====================================================================
// Piezas comunes de esta pantalla (cada fichero tiene las suyas: "private"
// en Kotlin es por fichero, no por paquete).
// =====================================================================

@Composable
private fun ChipFechaHoraBanco(texto: String, onClick: () -> Unit) {
    val colores = NubiTheme.colores
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colores.lienzoAlto)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(texto, style = MaterialTheme.typography.labelLarge, color = colores.tinta)
    }
}

@Composable
private fun BotonCirculoBanco(masOMenos: Boolean, colores: NubiColores, onClick: () -> Unit) {
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
private fun EtiquetaSeccionBanco(texto: String) {
    Text(
        texto.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = NubiTheme.colores.tintaTenue
    )
}

@Composable
private fun <T> SelectorSegmentadoBanco(
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

private val zonaBanco: ZoneId = ZoneId.systemDefault()

private val formatoHoraBanco: DateTimeFormatter =
    DateTimeFormatter.ofPattern("H:mm").withZone(zonaBanco)

private fun textoFechaConAnoBanco(fecha: Instant): String {
    val dia = fecha.atZone(zonaBanco).toLocalDate()
    val mes = dia.month.getDisplayName(TextStyle.SHORT, Locale("es", "ES"))
        .lowercase(Locale("es", "ES"))
        .replaceFirstChar { it.titlecase(Locale("es", "ES")) }
    return "${dia.dayOfMonth} $mes ${dia.year}"
}

private fun textoFechaHoraBanco(fecha: Instant): String =
    "${textoFechaConAnoBanco(fecha)} · ${formatoHoraBanco.format(fecha)}"

private fun fechaAMillisUtcBanco(fecha: Instant): Long =
    fecha.atZone(zonaBanco).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun millisUtcAFechaBanco(millis: Long): Instant =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zonaBanco).toInstant()

/** cantidadMl siempre está en ml (ver ExtraccionLeche/UsoLeche): esto solo
 *  decide cómo se muestra, igual que textoCantidadBiberon en HojasRegistro.kt. */
private fun textoCantidadBanco(cantidadMl: Double, unidad: UnidadBiberon): String {
    val valor = cantidadMl / unidad.factor
    if (unidad == UnidadBiberon.ML) return valor.roundToInt().toString()
    return if (valor == valor.roundToInt().toDouble()) valor.roundToInt().toString()
    else String.format(Locale.US, "%.1f", valor)
}
