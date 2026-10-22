package app.nubi.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nubi.data.Bebe
import app.nubi.data.CategoriaAlimento
import app.nubi.data.ComidaComplementaria
import app.nubi.data.FotosNubi
import app.nubi.data.GustoComida
import app.nubi.data.Repositorio
import app.nubi.data.nuevoId
import app.nubi.ui.theme.NubiGeometria
import app.nubi.ui.theme.NubiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pestaña de arriba "Alimentación" (comida complementaria: papillas,
 * frutas, cereales...). La tarjeta de contadores, el botón "+ Registrar
 * comida", el estado vacío y la hoja "Nueva comida" (con sus fotos) son
 * un porte exacto de las capturas de iOS. La fila de cada comida ya
 * registrada es diseño propio: la captura que mandó Alejandro de esta
 * pantalla estaba en el estado "Aún no hay comidas", sin ninguna fila
 * real que copiar.
 */
@Composable
fun PantallaAlimentacion(bebe: Bebe, comidas: List<ComidaComplementaria>) {
    val colores = NubiTheme.colores
    var mostrarNuevaComida by remember { mutableStateOf(false) }
    var comidaEditando by remember { mutableStateOf<ComidaComplementaria?>(null) }

    val gustaron = comidas.count { it.gusto == GustoComida.LE_GUSTO }
    val noGustaron = comidas.count { it.gusto == GustoComida.NO_LE_GUSTO }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(NubiGeometria.margen)
    ) {
        Text("Alimentación", style = MaterialTheme.typography.headlineLarge, color = colores.tinta)
        Spacer(Modifier.height(18.dp))

        Card(
            shape = RoundedCornerShape(NubiGeometria.radio),
            colors = CardDefaults.cardColors(containerColor = colores.superficie),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
                EstadisticaAlimentacion("comidas", comidas.size, colores.tinta, Modifier.weight(1f))
                EstadisticaAlimentacion("gustaron", gustaron, colores.menta, Modifier.weight(1f))
                EstadisticaAlimentacion("no gustaron", noGustaron, colores.coral, Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { mostrarNuevaComida = true },
            colors = ButtonDefaults.buttonColors(containerColor = colores.menta, contentColor = colores.tinta),
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icono(IconoNubi.MAS, tint = colores.tinta, tamano = 16.dp)
            Spacer(Modifier.width(6.dp))
            Text("Registrar comida", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))
        if (comidas.isEmpty()) {
            Card(
                shape = RoundedCornerShape(NubiGeometria.radio),
                colors = CardDefaults.cardColors(containerColor = colores.superficie),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BadgeIcono(
                        IconoNubi.CRONOMETRO,
                        colorFondo = colores.menta,
                        colorIcono = colores.tinta,
                        tamano = 60.dp,
                        tamanoIcono = 26.dp
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("Aún no hay comidas", style = MaterialTheme.typography.titleLarge, color = colores.tinta)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Registra la primera papilla, fruta o cereal de tu bebé.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colores.tintaSuave,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            comidas.sortedByDescending { it.fecha }.forEach { comida ->
                FilaComida(
                    comida = comida,
                    onEditar = { comidaEditando = comida },
                    onBorrar = {
                        comida.fotos.forEach { FotosNubi.borrar(it) }
                        Repositorio.borrarComida(comida.id)
                    }
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    if (mostrarNuevaComida) {
        HojaComida(bebeId = bebe.id, onCerrar = { mostrarNuevaComida = false })
    }
    comidaEditando?.let { comida ->
        HojaComida(bebeId = bebe.id, comida = comida, onCerrar = { comidaEditando = null })
    }
}

@Composable
private fun EstadisticaAlimentacion(etiqueta: String, numero: Int, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(numero.toString(), style = MaterialTheme.typography.headlineMedium, color = color)
        Spacer(Modifier.height(2.dp))
        Text(etiqueta, style = MaterialTheme.typography.bodySmall, color = NubiTheme.colores.tintaTenue)
    }
}

@Composable
private fun FilaComida(comida: ComidaComplementaria, onEditar: () -> Unit, onBorrar: () -> Unit) {
    val colores = NubiTheme.colores
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEditar)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BadgeIcono(
            IconoNubi.CRONOMETRO,
            colorFondo = colores.menta,
            colorIcono = colores.tinta,
            tamano = 40.dp,
            tamanoIcono = 18.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                comida.nombre.ifBlank { etiquetaCategoria(comida.categoria) },
                style = MaterialTheme.typography.bodyLarge,
                color = colores.tinta
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${etiquetaCategoria(comida.categoria)} · ${textoFechaHoraAlim(comida.fecha)}",
                style = MaterialTheme.typography.bodyMedium,
                color = colores.tintaSuave
            )
        }
        when (comida.gusto) {
            GustoComida.LE_GUSTO -> Icono(IconoNubi.PULGAR_ARRIBA, tint = colores.menta, tamano = 18.dp)
            GustoComida.NO_LE_GUSTO -> Icono(IconoNubi.PULGAR_ABAJO, tint = colores.coral, tamano = 18.dp)
            null -> {}
        }
        Spacer(Modifier.width(10.dp))
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
// Hoja "Nueva comida" — porte exacto de la captura de iOS.
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaComida(
    bebeId: String,
    comida: ComidaComplementaria? = null,
    onCerrar: () -> Unit
) {
    val colores = NubiTheme.colores
    val contexto = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var fecha by remember { mutableStateOf(comida?.fecha ?: Instant.now()) }
    var nombre by remember { mutableStateOf(comida?.nombre ?: "") }
    var categoria by remember { mutableStateOf(comida?.categoria ?: CategoriaAlimento.OTRO) }
    var fotos by remember { mutableStateOf(comida?.fotos ?: emptyList()) }
    var gusto by remember { mutableStateOf(comida?.gusto) }
    var nota by remember { mutableStateOf(comida?.nota ?: "") }

    var mostrarSelectorFecha by remember { mutableStateOf(false) }
    var mostrarSelectorHora by remember { mutableStateOf(false) }
    var mostrarCategorias by remember { mutableStateOf(false) }
    var ficheroTemporalCamara by remember { mutableStateOf<java.io.File?>(null) }

    val lanzadorCamara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { exito ->
        val fichero = ficheroTemporalCamara
        if (exito && fichero != null && fotos.size < 2) {
            fotos = fotos + fichero.absolutePath
        }
    }
    val lanzadorGaleria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && fotos.size < 2) {
            FotosNubi.copiarDesdeUri(contexto, uri)?.let { ruta -> fotos = fotos + ruta }
        }
    }

    fun guardar() {
        val base = comida ?: ComidaComplementaria(id = nuevoId(), bebeId = bebeId, fecha = fecha)
        Repositorio.guardarComida(
            base.copy(
                fecha = fecha,
                nombre = nombre,
                categoria = categoria,
                gusto = gusto,
                nota = nota,
                fotos = fotos
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
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colores.lienzoAlto)
                        .clickable(onClick = onCerrar),
                    contentAlignment = Alignment.Center
                ) {
                    Icono(IconoNubi.EQUIS, tint = colores.tinta, tamano = 14.dp)
                }
                Text(
                    if (comida != null) "Editar comida" else "Nueva comida",
                    style = MaterialTheme.typography.titleLarge,
                    color = colores.tinta
                )
            }
            Spacer(Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(NubiGeometria.radio),
                colors = CardDefaults.cardColors(containerColor = colores.superficie),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    EtiquetaSeccionAlim("Fecha y hora")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChipFechaHoraAlim(textoFechaConAnoAlim(fecha)) { mostrarSelectorFecha = true }
                        ChipFechaHoraAlim(formatoHoraAlim.format(fecha)) { mostrarSelectorHora = true }
                    }

                    Spacer(Modifier.height(16.dp))
                    EtiquetaSeccionAlim("¿Qué comió?")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nombre, onValueChange = { nombre = it },
                        placeholder = { Text("Puré de calabaza") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))
                    EtiquetaSeccionAlim("Categoría")
                    Spacer(Modifier.height(8.dp))
                    Box {
                        Row(
                            modifier = Modifier.clickable { mostrarCategorias = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(etiquetaCategoria(categoria), style = MaterialTheme.typography.bodyLarge, color = colores.lila)
                            Spacer(Modifier.width(4.dp))
                            Icono(IconoNubi.CHEVRON, tint = colores.lila, tamano = 12.dp, modifier = Modifier.rotate(90f))
                        }
                        DropdownMenu(expanded = mostrarCategorias, onDismissRequest = { mostrarCategorias = false }) {
                            CategoriaAlimento.entries.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(etiquetaCategoria(c)) },
                                    onClick = { categoria = c; mostrarCategorias = false }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Card(
                shape = RoundedCornerShape(NubiGeometria.radio),
                colors = CardDefaults.cardColors(containerColor = colores.superficie),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    EtiquetaSeccionAlim("Fotos (máx 2)")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BotonFotoAlim(IconoNubi.CAMARA, "Hacer foto", habilitado = fotos.size < 2) {
                            val fichero = FotosNubi.nuevoFichero(contexto)
                            ficheroTemporalCamara = fichero
                            lanzadorCamara.launch(FotosNubi.uriParaFichero(contexto, fichero))
                        }
                        BotonFotoAlim(IconoNubi.GALERIA, "Elegir foto", habilitado = fotos.size < 2) {
                            lanzadorGaleria.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    }
                    if (fotos.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            fotos.forEach { ruta ->
                                MiniaturaFotoAlim(ruta) { fotos = fotos.filterNot { it == ruta } }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Card(
                shape = RoundedCornerShape(NubiGeometria.radio),
                colors = CardDefaults.cardColors(containerColor = colores.superficie),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    EtiquetaSeccionAlim("¿Le gustó?")
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        OpcionGusto(
                            icono = IconoNubi.PULGAR_ARRIBA,
                            etiqueta = "Le gustó",
                            seleccionado = gusto == GustoComida.LE_GUSTO,
                            colorActivo = colores.menta,
                            modifier = Modifier.weight(1f)
                        ) { gusto = if (gusto == GustoComida.LE_GUSTO) null else GustoComida.LE_GUSTO }
                        OpcionGusto(
                            icono = IconoNubi.PULGAR_ABAJO,
                            etiqueta = "No le gustó",
                            seleccionado = gusto == GustoComida.NO_LE_GUSTO,
                            colorActivo = colores.coral,
                            modifier = Modifier.weight(1f)
                        ) { gusto = if (gusto == GustoComida.NO_LE_GUSTO) null else GustoComida.NO_LE_GUSTO }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Card(
                shape = RoundedCornerShape(NubiGeometria.radio),
                colors = CardDefaults.cardColors(containerColor = colores.superficie),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    EtiquetaSeccionAlim("Nota")
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = nota, onValueChange = { nota = it },
                        placeholder = { Text("Cantidad, reacción, cómo se lo tomó…") },
                        minLines = 2, maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { guardar() },
                colors = ButtonDefaults.buttonColors(containerColor = colores.menta, contentColor = colores.tinta),
                shape = RoundedCornerShape(NubiGeometria.radioChico),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar") }
        }
    }

    if (mostrarSelectorFecha) {
        val estado = rememberDatePickerState(initialSelectedDateMillis = fechaAMillisUtcAlim(fecha))
        DatePickerDialog(
            onDismissRequest = { mostrarSelectorFecha = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { millis ->
                        val diaNuevo = millisUtcAFechaAlim(millis).atZone(zonaAlim).toLocalDate()
                        val horaVieja = fecha.atZone(zonaAlim).toLocalTime()
                        fecha = diaNuevo.atTime(horaVieja).atZone(zonaAlim).toInstant()
                    }
                    mostrarSelectorFecha = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { mostrarSelectorFecha = false }) { Text("Cancelar") } }
        ) { DatePicker(state = estado) }
    }

    if (mostrarSelectorHora) {
        val horaActual = fecha.atZone(zonaAlim).toLocalTime()
        val estado = rememberTimePickerState(initialHour = horaActual.hour, initialMinute = horaActual.minute, is24Hour = true)
        DatePickerDialog(
            onDismissRequest = { mostrarSelectorHora = false },
            confirmButton = {
                TextButton(onClick = {
                    val dia = fecha.atZone(zonaAlim).toLocalDate()
                    fecha = dia.atTime(estado.hour, estado.minute).atZone(zonaAlim).toInstant()
                    mostrarSelectorHora = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { mostrarSelectorHora = false }) { Text("Cancelar") } }
        ) { TimePicker(state = estado) }
    }
}

@Composable
private fun BotonFotoAlim(icono: IconoNubi, etiqueta: String, habilitado: Boolean, onClick: () -> Unit) {
    val colores = NubiTheme.colores
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colores.lila.copy(alpha = if (habilitado) 0.3f else 0.12f))
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icono(icono, tint = if (habilitado) colores.tinta else colores.tintaTenue, tamano = 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(etiqueta, style = MaterialTheme.typography.labelLarge, color = if (habilitado) colores.tinta else colores.tintaTenue)
    }
}

@Composable
private fun MiniaturaFotoAlim(ruta: String, onQuitar: () -> Unit) {
    val colores = NubiTheme.colores
    val bitmap by produceState<ImageBitmap?>(initialValue = null, ruta) {
        value = withContext(Dispatchers.IO) {
            try {
                BitmapFactory.decodeFile(ruta)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    Box(modifier = Modifier.size(72.dp)) {
        val imagen = bitmap
        if (imagen != null) {
            Image(
                bitmap = imagen,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(NubiGeometria.radioChico))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(NubiGeometria.radioChico))
                    .background(colores.lienzoAlto)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(colores.superficie)
                .clickable(onClick = onQuitar),
            contentAlignment = Alignment.Center
        ) {
            Icono(IconoNubi.EQUIS, tint = colores.tinta, tamano = 9.dp)
        }
    }
}

@Composable
private fun OpcionGusto(
    icono: IconoNubi,
    etiqueta: String,
    seleccionado: Boolean,
    colorActivo: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colores = NubiTheme.colores
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(NubiGeometria.radioChico))
            .then(
                if (seleccionado) Modifier.background(colorActivo.copy(alpha = 0.22f)) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icono(icono, tint = if (seleccionado) colorActivo else colores.tintaTenue, tamano = 26.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            etiqueta,
            style = MaterialTheme.typography.labelMedium,
            color = if (seleccionado) colores.tinta else colores.tintaTenue
        )
    }
}

@Composable
private fun ChipFechaHoraAlim(texto: String, onClick: () -> Unit) {
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
private fun EtiquetaSeccionAlim(texto: String) {
    Text(
        texto.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = NubiTheme.colores.tintaTenue
    )
}

private fun etiquetaCategoria(c: CategoriaAlimento): String = when (c) {
    CategoriaAlimento.FRUTA -> "Fruta"
    CategoriaAlimento.VERDURA -> "Verdura"
    CategoriaAlimento.PROTEINA -> "Proteína"
    CategoriaAlimento.CEREAL -> "Cereal"
    CategoriaAlimento.LACTEO -> "Lácteo"
    CategoriaAlimento.OTRO -> "Otro"
}

private val zonaAlim: ZoneId = ZoneId.systemDefault()

private val formatoHoraAlim: DateTimeFormatter =
    DateTimeFormatter.ofPattern("H:mm").withZone(zonaAlim)

private fun textoFechaConAnoAlim(fecha: Instant): String {
    val dia = fecha.atZone(zonaAlim).toLocalDate()
    val mes = dia.month.getDisplayName(TextStyle.SHORT, Locale("es", "ES"))
        .lowercase(Locale("es", "ES"))
        .replaceFirstChar { it.titlecase(Locale("es", "ES")) }
    return "${dia.dayOfMonth} $mes ${dia.year}"
}

private fun textoFechaHoraAlim(fecha: Instant): String =
    "${textoFechaConAnoAlim(fecha)} · ${formatoHoraAlim.format(fecha)}"

private fun fechaAMillisUtcAlim(fecha: Instant): Long =
    fecha.atZone(zonaAlim).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun millisUtcAFechaAlim(millis: Long): Instant =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zonaAlim).toInstant()
