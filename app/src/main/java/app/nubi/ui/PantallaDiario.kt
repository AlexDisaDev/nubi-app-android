package app.nubi.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nubi.data.Bebe
import app.nubi.data.EntradaDiario
import app.nubi.data.Repositorio
import app.nubi.ui.theme.NubiGeometria
import app.nubi.ui.theme.NubiTheme
import app.nubi.i18n.L10n
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/**
 * La pestaña "Diario" (ver VistaDiario.swift): notas libres sobre el día a
 * día del bebé, con la opción de marcarlas como hito. iOS limita a 5
 * entradas sin suscripción y enseña un paywall al llegar al límite; Android
 * todavía no tiene capa de suscripciones, así que aquí se escribe sin
 * límite — se añadirá ese límite cuando exista Google Play Billing.
 */
@Composable
fun PantallaDiario(bebe: Bebe, entradas: List<EntradaDiario>) {
    val colores = NubiTheme.colores
    var editando by remember { mutableStateOf<EntradaDiario?>(null) }
    var soloHitos by remember { mutableStateOf(false) }

    val entradasOrdenadas = remember(entradas) { entradas.sortedByDescending { it.fecha } }
    val hitos = remember(entradas) { entradas.count { it.hito } }
    val visibles = remember(entradasOrdenadas, soloHitos) {
        if (soloHitos) entradasOrdenadas.filter { it.hito } else entradasOrdenadas
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NubiGeometria.margen)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(L10n.t("Diario"), style = MaterialTheme.typography.displaySmall)
                Text(
                    subtituloDiario(entradas.size, hitos),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.tintaSuave
                )
            }
            Box(modifier = Modifier.clickable {
                editando = EntradaDiario(bebeId = bebe.id, fecha = Instant.now())
            }) {
                BadgeIcono(
                    IconoNubi.LAPIZ, colorFondo = colores.lila, colorIcono = colores.tinta,
                    tamano = 42.dp, tamanoIcono = 18.dp
                )
            }
        }

        if (entradas.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SelectorTodoHitos(soloHitos) { soloHitos = it }
        }

        Spacer(Modifier.height(16.dp))

        if (visibles.isEmpty()) {
            Tarjeta {
                EstadoVacioDiario(
                    icono = IconoNubi.LIBRETA,
                    titulo = if (soloHitos) L10n.t("Ningún hito todavía") else L10n.t("El cuaderno está en blanco"),
                    texto = if (soloHitos) L10n.t("Marca una entrada como hito cuando pase algo por primera vez.")
                    else L10n.t("La primera sonrisa, la noche que durmió del tirón, lo que dijo el pediatra. Dentro de un año no te acordarás si no lo escribes."),
                    color = colores.mantequilla
                )
            }
            if (!soloHitos) {
                Spacer(Modifier.height(14.dp))
                BotonPrimarioDiario(L10n.t("Escribe tu nota"), IconoNubi.LAPIZ, colores.lila) {
                    editando = EntradaDiario(bebeId = bebe.id, fecha = Instant.now())
                }
            }
        } else {
            visibles.forEachIndexed { i, e ->
                TarjetaEntrada(e, bebe) { editando = e }
                if (i != visibles.lastIndex) Spacer(Modifier.height(12.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    editando?.let { e ->
        HojaDiario(
            entrada = e,
            esNueva = entradas.none { it.id == e.id },
            onGuardar = { Repositorio.guardarEntradaDiario(it); editando = null },
            onBorrar = { Repositorio.borrarEntradaDiario(e.id); editando = null },
            onCerrar = { editando = null }
        )
    }
}

@Composable
private fun SelectorTodoHitos(soloHitos: Boolean, onCambiar: (Boolean) -> Unit) {
    val colores = NubiTheme.colores
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(colores.lienzoAlto)
            .padding(4.dp)
    ) {
        listOf(false to L10n.t("Todo"), true to L10n.t("Solo hitos")).forEach { (valor, titulo) ->
            val sel = soloHitos == valor
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) colores.superficie else Color.Transparent)
                    .clickable { onCambiar(valor) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(titulo, style = MaterialTheme.typography.labelLarge, color = if (sel) colores.tinta else colores.tintaTenue)
            }
        }
    }
}

@Composable
private fun TarjetaEntrada(e: EntradaDiario, bebe: Bebe, onClick: () -> Unit) {
    val colores = NubiTheme.colores
    Card(
        shape = RoundedCornerShape(NubiGeometria.radio),
        colors = CardDefaults.cardColors(containerColor = colores.superficie),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    textoFechaLargaDiario(e.fecha),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
                    color = colores.tintaSuave
                )
                Text(
                    " · ${textoEdadEnFecha(e.fecha, bebe)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colores.tintaTenue
                )
                Spacer(Modifier.weight(1f))
                if (e.hito) Icono(IconoNubi.ESTRELLA, tint = colores.mantequilla, tamano = 16.dp)
            }
            Text(tituloVisibleEntrada(e), style = MaterialTheme.typography.titleLarge, color = colores.tinta)
            if (e.texto.isNotEmpty()) {
                Text(
                    e.texto,
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.tintaSuave,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaDiario(
    entrada: EntradaDiario,
    esNueva: Boolean,
    onGuardar: (EntradaDiario) -> Unit,
    onBorrar: () -> Unit,
    onCerrar: () -> Unit
) {
    val colores = NubiTheme.colores
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var fecha by remember { mutableStateOf(entrada.fecha) }
    var hito by remember { mutableStateOf(entrada.hito) }
    var titulo by remember { mutableStateOf(entrada.titulo) }
    var texto by remember { mutableStateOf(entrada.texto) }
    var mostrarSelectorFecha by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = sheetState, containerColor = colores.lienzo) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NubiGeometria.margen)
                .padding(bottom = 28.dp)
        ) {
            Text(if (esNueva) L10n.t("Nueva entrada") else L10n.t("Entrada"), style = MaterialTheme.typography.titleLarge, color = colores.tinta)
            Spacer(Modifier.height(18.dp))

            Tarjeta {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EtiquetaSeccionDiario(L10n.t("Fecha"))
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = { mostrarSelectorFecha = true }) { Text(textoFechaCortaDiario(fecha)) }
                    }
                    HorizontalDivider(color = colores.separador)
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { hito = !hito },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icono(IconoNubi.ESTRELLA, tint = colores.mantequilla, tamano = 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            L10n.t("Marcar como hito"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colores.tinta,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = hito, onCheckedChange = { hito = it })
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            Tarjeta {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = titulo, onValueChange = { titulo = it },
                        placeholder = { Text(L10n.t("Título")) },
                        textStyle = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(color = colores.separador)
                    OutlinedTextField(
                        value = texto, onValueChange = { texto = it },
                        placeholder = { Text(L10n.t("Hoy ha pasado que…")) },
                        minLines = 6, maxLines = 20,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            Button(
                onClick = {
                    if (titulo.isEmpty() && texto.isEmpty()) {
                        onCerrar()
                    } else {
                        onGuardar(entrada.copy(fecha = fecha, titulo = titulo, texto = texto, hito = hito))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = colores.lila, contentColor = colores.tinta),
                shape = RoundedCornerShape(NubiGeometria.radioChico),
                modifier = Modifier.fillMaxWidth()
            ) { Text(L10n.t("Guardar")) }

            if (!esNueva) {
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onBorrar, modifier = Modifier.fillMaxWidth()) {
                    Icono(IconoNubi.PAPELERA, tint = colores.indigo, tamano = 14.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(L10n.t("Borrar esta entrada"), color = colores.indigo)
                }
            }
        }
    }

    if (mostrarSelectorFecha) {
        val estado = rememberDatePickerState(initialSelectedDateMillis = fechaAMillisUtcDiario(fecha))
        DatePickerDialog(
            onDismissRequest = { mostrarSelectorFecha = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { fecha = millisUtcAFechaDiario(it) }
                    mostrarSelectorFecha = false
                }) { Text(L10n.t("Aceptar")) }
            },
            dismissButton = { TextButton(onClick = { mostrarSelectorFecha = false }) { Text(L10n.t("Cancelar")) } }
        ) { DatePicker(state = estado) }
    }
}

// -----------------------------------------------------------------
// Piezas comunes de esta pantalla
// -----------------------------------------------------------------

@Composable
private fun Tarjeta(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(NubiGeometria.radio),
        colors = CardDefaults.cardColors(containerColor = NubiTheme.colores.superficie),
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

@Composable
private fun EtiquetaSeccionDiario(texto: String) {
    Text(
        texto.uppercase(L10n.localeJava),
        style = MaterialTheme.typography.labelSmall,
        color = NubiTheme.colores.tintaTenue,
        letterSpacing = 0.5.sp
    )
}

@Composable
private fun BotonPrimarioDiario(texto: String, icono: IconoNubi, color: Color, onClick: () -> Unit) {
    val colores = NubiTheme.colores
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = colores.tinta),
        shape = RoundedCornerShape(NubiGeometria.radioChico),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icono(icono, tint = colores.tinta, tamano = 16.dp)
        Spacer(Modifier.width(8.dp))
        Text(texto)
    }
}

@Composable
private fun EstadoVacioDiario(icono: IconoNubi, titulo: String, texto: String, color: Color) {
    val colores = NubiTheme.colores
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp, horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BadgeIcono(icono, colorFondo = color, colorIcono = colores.tinta, tamano = 64.dp, tamanoIcono = 28.dp)
        Spacer(Modifier.height(14.dp))
        Text(
            titulo,
            style = MaterialTheme.typography.titleLarge,
            color = colores.tinta,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = colores.tintaSuave,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// -----------------------------------------------------------------
// Ayudantes de texto y fecha
// -----------------------------------------------------------------

private fun subtituloDiario(n: Int, hitos: Int): String {
    val entradasTxt = L10n.nEntradas(n)
    if (hitos == 0) return entradasTxt
    return "$entradasTxt · ${L10n.nHitos(hitos)}"
}

private fun tituloVisibleEntrada(e: EntradaDiario): String {
    if (e.titulo.isNotEmpty()) return e.titulo
    val primera = e.texto.split("\n").firstOrNull { it.isNotEmpty() } ?: L10n.t("Sin título")
    return primera.take(60)
}

/** Edad del bebé en la fecha de la entrada, igual que Fmt.edadEnFecha en iOS. */
private fun textoEdadEnFecha(fecha: Instant, bebe: Bebe): String {
    val dias = java.time.Duration.between(bebe.fechaNacimiento, fecha).toDays().toInt()
    return L10n.textoEdadAbrev(dias)
}

private val zonaDiario: ZoneId = ZoneId.systemDefault()

private fun textoFechaCortaDiario(fecha: Instant): String {
    val dia = fecha.atZone(zonaDiario).toLocalDate()
    val mes = dia.month.getDisplayName(TextStyle.SHORT, L10n.localeJava)
    return "${dia.dayOfMonth} $mes".lowercase()
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(L10n.localeJava) else it.toString() }
}

private fun textoFechaLargaDiario(fecha: Instant): String {
    val dia = fecha.atZone(zonaDiario).toLocalDate()
    val nombreDia = dia.dayOfWeek.getDisplayName(TextStyle.FULL, L10n.localeJava)
    val nombreMes = dia.month.getDisplayName(TextStyle.FULL, L10n.localeJava)
    return "$nombreDia, ${dia.dayOfMonth} de $nombreMes"
        .replaceFirstChar { it.titlecase(L10n.localeJava) }
}

private fun fechaAMillisUtcDiario(fecha: Instant): Long =
    fecha.atZone(zonaDiario).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun millisUtcAFechaDiario(millis: Long): Instant =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zonaDiario).toInstant()
