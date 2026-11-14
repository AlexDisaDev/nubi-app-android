package app.nubi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nubi.data.Bebe
import app.nubi.data.Medida
import app.nubi.data.Repositorio
import app.nubi.ui.theme.NubiColores
import app.nubi.ui.theme.NubiGeometria
import app.nubi.ui.theme.NubiTheme
import app.nubi.i18n.L10n
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

// =====================================================================
// Pestaña Crece (VistaCrecimiento.swift): peso, estatura y perímetro
// craneal, con una gráfica sencilla de evolución, el historial de
// medidas y la hoja para añadir o editar una.
// =====================================================================

// [titulo] se queda en español, sin pasar por L10n.t() aquí: un enum se
// inicializa una sola vez, la primera vez que la clase se toca, así que
// traducirlo en el propio constructor lo dejaba congelado en el idioma
// que hubiera en ese instante para siempre (el mismo bug que las pestañas
// de Navegacion.kt). Ahora es la clave, y L10n.t(m.titulo) se llama en
// cada sitio donde se pinta.
private enum class Magnitud(val titulo: String, val unidad: String, val icono: IconoNubi) {
    PESO("Peso", "kg", IconoNubi.BASCULA),
    ALTURA("Estatura", "cm", IconoNubi.REGLA),
    PERIMETRO("Cabeza", "cm", IconoNubi.ONDAS);

    val decimales: Int get() = if (this == PESO) 2 else 1

    fun valor(m: Medida): Double? = when (this) {
        PESO -> m.pesoKg
        ALTURA -> m.alturaCm
        PERIMETRO -> m.perimetroCm
    }
}

/** Mismo patrón que EtiquetaSeccion en el resto de pantallas (PantallaSalud.kt
 *  y compañía) y que EtiquetaSeccion en Componentes.swift de iOS: mayúsculas
 *  con tracking, en vez del texto normal que llevaba esta pantalla — era una
 *  de las cosas que hacían que "Evolución de peso" e "Historial" se vieran
 *  más apagados aquí que en iOS. */
@Composable
private fun EtiquetaSeccionCrece(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto.uppercase(L10n.localeJava),
        style = MaterialTheme.typography.labelSmall,
        color = NubiTheme.colores.tintaTenue,
        letterSpacing = 0.5.sp,
        modifier = modifier
    )
}

private fun colorDeMagnitud(m: Magnitud, c: NubiColores): Color = when (m) {
    Magnitud.PESO -> c.menta
    Magnitud.ALTURA -> c.cielo
    Magnitud.PERIMETRO -> c.rosa
}

private data class PuntoSerie(val fecha: Instant, val valor: Double)

private val zonaCrece: ZoneId = ZoneId.systemDefault()

private fun formatoNumero(valor: Double, decimales: Int): String =
    String.format(Locale.US, "%.${decimales}f", valor)

private fun numeroDesde(texto: String): Double? {
    val limpio = texto.replace(',', '.').trim()
    if (limpio.isEmpty()) return null
    val valor = limpio.toDoubleOrNull() ?: return null
    return if (valor > 0) valor else null
}

private fun textoFechaCorta(fecha: Instant): String {
    val dia = fecha.atZone(zonaCrece).toLocalDate()
    val mes = dia.month.getDisplayName(TextStyle.SHORT, L10n.localeJava)
    return "${dia.dayOfMonth} $mes".lowercase()
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(L10n.localeJava) else it.toString() }
}

private fun textoFechaLarga(fecha: Instant): String {
    val dia = fecha.atZone(zonaCrece).toLocalDate()
    val nombreDia = dia.dayOfWeek.getDisplayName(TextStyle.FULL, L10n.localeJava)
    val nombreMes = dia.month.getDisplayName(TextStyle.FULL, L10n.localeJava)
    return "$nombreDia, ${dia.dayOfMonth} de $nombreMes"
        .replaceFirstChar { it.titlecase(L10n.localeJava) }
}

private fun resumenMedida(m: Medida): String {
    val partes = mutableListOf<String>()
    m.pesoKg?.let { partes.add("${formatoNumero(it, 2)} kg") }
    m.alturaCm?.let { partes.add("${formatoNumero(it, 1)} cm") }
    m.perimetroCm?.let { partes.add("${L10n.t("Cabeza").lowercase()} ${formatoNumero(it, 1)} cm") }
    return if (partes.isEmpty()) L10n.t("Sin datos") else partes.joinToString(" · ")
}

/** La medida más reciente que tenga valor para esta magnitud, con su fecha. */
private fun ultimaDe(medidasAscendentes: List<Medida>, m: Magnitud): Pair<Double, Instant>? =
    medidasAscendentes.asReversed().firstNotNullOfOrNull { med -> m.valor(med)?.let { it to med.fecha } }

@Composable
fun PantallaCrece(bebe: Bebe, medidas: List<Medida>) {
    val colores = NubiTheme.colores
    var magnitud by remember { mutableStateOf(Magnitud.PESO) }
    var editando by remember { mutableStateOf<Medida?>(null) }

    val medidasOrdenadas = remember(medidas) { medidas.sortedBy { it.fecha } }
    val serie = remember(medidasOrdenadas, magnitud) {
        medidasOrdenadas.mapNotNull { m -> magnitud.valor(m)?.let { PuntoSerie(m.fecha, it) } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NubiGeometria.margen)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(L10n.t("Cómo crece"), style = MaterialTheme.typography.displaySmall)
                Text(
                    if (medidas.isEmpty()) L10n.t("Sin medidas todavía") else L10n.medidasAnotadas(medidas.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.tintaSuave
                )
            }
            Box(modifier = Modifier.clickable {
                editando = Medida(bebeId = bebe.id, fecha = Instant.now())
            }) {
                BadgeIcono(
                    IconoNubi.MAS,
                    colorFondo = colores.lila,
                    colorIcono = colores.tinta,
                    tamano = 42.dp,
                    tamanoIcono = 18.dp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Magnitud.entries.forEach { m ->
                CeldaResumen(m, ultimaDe(medidasOrdenadas, m), modifier = Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(16.dp))

        SelectorMagnitud(seleccion = magnitud, onSeleccion = { magnitud = it })

        Spacer(Modifier.height(16.dp))

        if (serie.isEmpty()) {
            Card(
                shape = RoundedCornerShape(NubiGeometria.radio),
                colors = CardDefaults.cardColors(containerColor = colores.superficie),
                modifier = Modifier.fillMaxWidth()
            ) {
                EstadoVacio(
                    icono = magnitud.icono,
                    titulo = L10n.aunNoHayMedida(magnitud.titulo),
                    texto = L10n.t("Apunta lo que te digan en la revisión y verás cómo evoluciona entre visita y visita."),
                    color = colorDeMagnitud(magnitud, colores)
                )
            }
        } else {
            TarjetaEvolucion(magnitud, serie, colores)
        }

        if (medidasOrdenadas.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            TarjetaHistorial(medidasOrdenadas.asReversed(), onEditar = { editando = it })
        }

        Spacer(Modifier.height(16.dp))
        Text(
            L10n.t("Nubi guarda tus medidas y te enseña la tendencia. Los percentiles y su interpretación corresponden a tu pediatra."),
            style = MaterialTheme.typography.bodySmall,
            color = colores.tintaTenue
        )
        Spacer(Modifier.height(24.dp))
    }

    editando?.let { m ->
        HojaMedida(
            medida = m,
            esNueva = medidasOrdenadas.none { it.id == m.id },
            onGuardar = { actualizada -> Repositorio.guardarMedida(actualizada); editando = null },
            onBorrar = { Repositorio.borrarMedida(m.id); editando = null },
            onCerrar = { editando = null }
        )
    }
}

@Composable
private fun CeldaResumen(m: Magnitud, dato: Pair<Double, Instant>?, modifier: Modifier = Modifier) {
    val colores = NubiTheme.colores
    Card(
        shape = RoundedCornerShape(NubiGeometria.radioChico),
        colors = CardDefaults.cardColors(containerColor = colores.superficie),
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp)) {
            BadgeIcono(
                m.icono,
                colorFondo = colorDeMagnitud(m, colores),
                colorIcono = colores.tinta,
                tamano = 30.dp,
                tamanoIcono = 14.dp
            )
            Spacer(Modifier.height(7.dp))
            if (dato != null) {
                val (valor, fecha) = dato
                Text(
                    formatoNumero(valor, m.decimales),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colores.tinta,
                    maxLines = 1
                )
                Text(
                    "${m.unidad} · ${textoFechaCorta(fecha)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colores.tintaTenue,
                    maxLines = 1
                )
            } else {
                Text("—", style = MaterialTheme.typography.headlineSmall, color = colores.tintaTenue)
                Text(L10n.t(m.titulo), style = MaterialTheme.typography.labelSmall, color = colores.tintaTenue)
            }
        }
    }
}

@Composable
private fun SelectorMagnitud(seleccion: Magnitud, onSeleccion: (Magnitud) -> Unit) {
    val colores = NubiTheme.colores
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(colores.lienzoAlto)
            .padding(4.dp)
    ) {
        Magnitud.entries.forEach { m ->
            val seleccionada = seleccion == m
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (seleccionada) colores.superficie else Color.Transparent)
                    .clickable { onSeleccion(m) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    L10n.t(m.titulo),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (seleccionada) colores.tinta else colores.tintaTenue
                )
            }
        }
    }
}

@Composable
private fun EstadoVacio(icono: IconoNubi, titulo: String, texto: String, color: Color) {
    val colores = NubiTheme.colores
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 26.dp, horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BadgeIcono(icono, colorFondo = color, colorIcono = colores.tinta, tamano = 64.dp, tamanoIcono = 28.dp)
        Spacer(Modifier.height(14.dp))
        Text(
            titulo,
            style = MaterialTheme.typography.titleLarge,
            color = colores.tinta,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = colores.tintaSuave,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TarjetaEvolucion(magnitud: Magnitud, serie: List<PuntoSerie>, colores: NubiColores) {
    Card(
        shape = RoundedCornerShape(NubiGeometria.radio),
        colors = CardDefaults.cardColors(containerColor = colores.superficie),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EtiquetaSeccionCrece(
                    "${L10n.t("Evolución de")} ${L10n.t(magnitud.titulo).lowercase()}",
                    modifier = Modifier.weight(1f)
                )
                if (serie.size >= 2) {
                    val delta = serie.last().valor - serie.first().valor
                    val signo = if (delta >= 0) "+" else ""
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            // Translúcida con texto índigo, como la Pastilla
                            // de iOS (Componentes.swift) — un relleno sólido
                            // del color de la magnitud se veía más plano.
                            .background(colorDeMagnitud(magnitud, colores).copy(alpha = 0.20f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "$signo${formatoNumero(delta, magnitud.decimales)} ${magnitud.unidad}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = colores.indigo
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            GraficaLinea(
                puntos = serie,
                color = colores.indigo,
                colorFondo = colores.superficie,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(textoFechaCorta(serie.first().fecha), style = MaterialTheme.typography.bodySmall, color = colores.tintaTenue)
                Text(textoFechaCorta(serie.last().fecha), style = MaterialTheme.typography.bodySmall, color = colores.tintaTenue)
            }
        }
    }
}

/**
 * Puerto de GraficaLinea (Componentes.swift, iOS): curva suave (Bézier
 * cúbica con tangentes horizontales en el punto medio de cada tramo, no
 * segmentos rectos), área bajo la curva con degradado, y puntos "huecos"
 * (círculo del color de la tarjeta con un aro de color alrededor) en vez
 * de puntos sólidos — así las dos apps dibujan la misma gráfica.
 */
@Composable
private fun GraficaLinea(puntos: List<PuntoSerie>, color: Color, colorFondo: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (puntos.isEmpty()) return@Canvas

        if (puntos.size == 1) {
            val centro = Offset(size.width / 2f, size.height / 2f)
            drawCircle(colorFondo, radius = 4.5.dp.toPx(), center = centro)
            drawCircle(color, radius = 4.5.dp.toPx(), center = centro, style = Stroke(width = 2.4.dp.toPx()))
            return@Canvas
        }

        // Igual que en iOS: un margen vertical del 18% del rango (mínimo
        // 0.4) para que la curva nunca toque el borde superior/inferior.
        val valores = puntos.map { it.valor }
        val minV = valores.min()
        val maxV = valores.max()
        val margenV = maxOf((maxV - minV) * 0.18, 0.4)
        val rangoMin = minV - margenV
        val rango = (maxV + margenV - rangoMin).takeIf { it > 0.0001 } ?: 1.0

        val margenX = 6.dp.toPx()
        val margenY = 8.dp.toPx()
        val anchoUtil = size.width - margenX * 2
        val altoUtil = size.height - margenY * 2

        fun puntoEn(i: Int): Offset {
            val x = margenX + anchoUtil * i / (puntos.size - 1).toFloat()
            val fy = ((puntos[i].valor - rangoMin) / rango).toFloat()
            val y = size.height - margenY - fy * altoUtil
            return Offset(x, y)
        }

        val coords = puntos.indices.map(::puntoEn)

        val curva = Path().apply {
            moveTo(coords[0].x, coords[0].y)
            for (i in 1 until coords.size) {
                val a = coords[i - 1]
                val b = coords[i]
                val mx = (a.x + b.x) / 2f
                cubicTo(mx, a.y, mx, b.y, b.x, b.y)
            }
        }

        val area = Path().apply {
            addPath(curva)
            lineTo(coords.last().x, size.height)
            lineTo(coords.first().x, size.height)
            close()
        }
        drawPath(
            area,
            brush = Brush.verticalGradient(
                listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0.02f))
            )
        )

        drawPath(
            curva,
            color = color,
            style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val radioPunto = 4.5.dp.toPx()
        for (p in coords) {
            drawCircle(colorFondo, radius = radioPunto, center = p)
            drawCircle(color, radius = radioPunto, center = p, style = Stroke(width = 2.4.dp.toPx()))
        }
    }
}

@Composable
private fun TarjetaHistorial(medidas: List<Medida>, onEditar: (Medida) -> Unit) {
    val colores = NubiTheme.colores
    Card(
        shape = RoundedCornerShape(NubiGeometria.radio),
        colors = CardDefaults.cardColors(containerColor = colores.superficie),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            EtiquetaSeccionCrece(L10n.t("Historial"))
            Spacer(Modifier.height(10.dp))
            medidas.forEachIndexed { indice, m ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditar(m) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(textoFechaLarga(m.fecha), style = MaterialTheme.typography.bodyMedium, color = colores.tinta)
                        Text(resumenMedida(m), style = MaterialTheme.typography.bodySmall, color = colores.tintaSuave)
                    }
                    Icono(IconoNubi.CHEVRON, tint = colores.tintaTenue, tamano = 14.dp)
                }
                if (indice != medidas.lastIndex) HorizontalDivider(color = colores.separador)
            }
        }
    }
}

// ---------------------------------------------------------------------
// Hoja de alta/edición de una medida.
// ---------------------------------------------------------------------

private fun fechaAMillisUtc(fecha: Instant): Long =
    fecha.atZone(zonaCrece).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun millisUtcAFecha(millis: Long): Instant =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zonaCrece).toInstant()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaMedida(
    medida: Medida,
    esNueva: Boolean,
    onGuardar: (Medida) -> Unit,
    onBorrar: () -> Unit,
    onCerrar: () -> Unit
) {
    val colores = NubiTheme.colores
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var fecha by remember { mutableStateOf(medida.fecha) }
    var peso by remember { mutableStateOf(medida.pesoKg?.let { formatoNumero(it, 2) } ?: "") }
    var altura by remember { mutableStateOf(medida.alturaCm?.let { formatoNumero(it, 1) } ?: "") }
    var perimetro by remember { mutableStateOf(medida.perimetroCm?.let { formatoNumero(it, 1) } ?: "") }
    var nota by remember { mutableStateOf(medida.nota) }
    var mostrarSelectorFecha by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = sheetState, containerColor = colores.lienzo) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NubiGeometria.margen)
                .padding(bottom = 28.dp)
        ) {
            Text(
                if (esNueva) L10n.t("Nueva medida") else L10n.t("Medida"),
                style = MaterialTheme.typography.titleLarge,
                color = colores.tinta
            )
            Spacer(Modifier.height(18.dp))

            Card(
                shape = RoundedCornerShape(NubiGeometria.radio),
                colors = CardDefaults.cardColors(containerColor = colores.superficie),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(L10n.t("Fecha"), style = MaterialTheme.typography.labelMedium, color = colores.tintaTenue)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { mostrarSelectorFecha = true }) {
                        Text(textoFechaLarga(fecha))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Card(
                shape = RoundedCornerShape(NubiGeometria.radio),
                colors = CardDefaults.cardColors(containerColor = colores.superficie),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    CampoMedida(L10n.t("Peso"), "kg", peso, { peso = it }, IconoNubi.BASCULA, colores.menta)
                    HorizontalDivider(color = colores.separador)
                    CampoMedida(L10n.t("Estatura"), "cm", altura, { altura = it }, IconoNubi.REGLA, colores.cielo)
                    HorizontalDivider(color = colores.separador)
                    CampoMedida(L10n.t("Perímetro craneal"), "cm", perimetro, { perimetro = it }, IconoNubi.ONDAS, colores.rosa)
                }
            }

            Spacer(Modifier.height(14.dp))

            Card(
                shape = RoundedCornerShape(NubiGeometria.radio),
                colors = CardDefaults.cardColors(containerColor = colores.superficie),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(L10n.t("Nota"), style = MaterialTheme.typography.labelMedium, color = colores.tintaTenue)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nota,
                        onValueChange = { nota = it },
                        placeholder = { Text(L10n.t("Lo que te dijeron en la consulta…")) },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = {
                    val actualizada = medida.copy(
                        fecha = fecha,
                        pesoKg = numeroDesde(peso),
                        alturaCm = numeroDesde(altura),
                        perimetroCm = numeroDesde(perimetro),
                        nota = nota
                    )
                    if (!actualizada.vacia) onGuardar(actualizada) else onCerrar()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colores.lila, contentColor = colores.tinta),
                shape = RoundedCornerShape(NubiGeometria.radioChico),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(L10n.t("Guardar"))
            }

            if (!esNueva) {
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onBorrar, modifier = Modifier.fillMaxWidth()) {
                    Icono(IconoNubi.PAPELERA, tint = colores.indigo, tamano = 14.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(L10n.t("Borrar esta medida"), color = colores.indigo)
                }
            }
        }
    }

    if (mostrarSelectorFecha) {
        val estado = rememberDatePickerState(initialSelectedDateMillis = fechaAMillisUtc(fecha))
        DatePickerDialog(
            onDismissRequest = { mostrarSelectorFecha = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { fecha = millisUtcAFecha(it) }
                    mostrarSelectorFecha = false
                }) { Text(L10n.t("Aceptar")) }
            },
            dismissButton = {
                TextButton(onClick = { mostrarSelectorFecha = false }) { Text(L10n.t("Cancelar")) }
            }
        ) {
            DatePicker(state = estado)
        }
    }
}

@Composable
private fun CampoMedida(
    titulo: String,
    unidad: String,
    valor: String,
    onValorChange: (String) -> Unit,
    icono: IconoNubi,
    color: Color
) {
    val colores = NubiTheme.colores
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BadgeIcono(icono, colorFondo = color, colorIcono = colores.tinta, tamano = 34.dp, tamanoIcono = 15.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            titulo,
            style = MaterialTheme.typography.bodyLarge,
            color = colores.tinta,
            modifier = Modifier.weight(1f)
        )
        TextField(
            value = valor,
            onValueChange = onValorChange,
            singleLine = true,
            placeholder = {
                Text("—", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            },
            textStyle = MaterialTheme.typography.headlineSmall.copy(color = colores.tinta, textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.width(84.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(unidad, style = MaterialTheme.typography.bodySmall, color = colores.tintaTenue, modifier = Modifier.width(26.dp))
    }
}
