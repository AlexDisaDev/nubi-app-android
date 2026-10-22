package app.nubi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nubi.data.Bebe
import app.nubi.data.CalendarioVacunal
import app.nubi.data.CatalogoVacunas
import app.nubi.data.Cita
import app.nubi.data.Comunidad
import app.nubi.data.Enfermedad
import app.nubi.data.EstadoVacuna
import app.nubi.data.GrupoVacunal
import app.nubi.data.GrupoVacunas
import app.nubi.data.ItemVacuna
import app.nubi.data.Medicina
import app.nubi.data.Repositorio
import app.nubi.data.TipoCita
import app.nubi.data.VacunaPropia
import app.nubi.ui.theme.NubiColores
import app.nubi.ui.theme.NubiGeometria
import app.nubi.ui.theme.NubiTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private enum class SeccionSalud(val titulo: String) {
    VACUNAS("Vacunas"), CITAS("Citas"), ENFERMEDADES("Enfermedades")
}

/**
 * La pestaña "Salud" (ver VistaSalud.swift): vacunas del calendario oficial
 * español (CalendarioVacunal.kt) más las añadidas a mano, citas médicas y
 * un registro de enfermedades.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaSalud(
    bebe: Bebe,
    citas: List<Cita>,
    vacunas: List<EstadoVacuna>,
    gruposVacunas: List<GrupoVacunas>,
    vacunasPropias: List<VacunaPropia>,
    enfermedades: List<Enfermedad>,
    comunidadGuardada: String
) {
    val colores = NubiTheme.colores
    var seccion by remember { mutableStateOf(SeccionSalud.VACUNAS) }
    var editandoCita by remember { mutableStateOf<Cita?>(null) }
    var editandoEnfermedad by remember { mutableStateOf<Enfermedad?>(null) }
    var eligiendoComunidad by remember { mutableStateOf(false) }
    var creandoGrupo by remember { mutableStateOf(false) }
    var anadiendoAGrupo by remember { mutableStateOf<GrupoVacunas?>(null) }

    val comunidad = remember(comunidadGuardada) {
        runCatching { Comunidad.valueOf(comunidadGuardada) }.getOrDefault(Comunidad.COMUN)
    }
    val gruposCalendario = remember(comunidad) { CalendarioVacunal.grupos(comunidad) }

    val citasProximas = remember(citas) { citas.filterNot { it.pasada() }.sortedBy { it.fecha } }
    val citasPasadas = remember(citas) { citas.filter { it.pasada() }.sortedByDescending { it.fecha } }
    val proximaCitaId = citasProximas.firstOrNull()?.id

    val enfermedadesActivas = remember(enfermedades) {
        enfermedades.filter { it.activa }.sortedByDescending { it.fechaInicio }
    }
    val enfermedadesPasadas = remember(enfermedades) {
        enfermedades.filterNot { it.activa }.sortedByDescending { it.fechaInicio }
    }

    val totalVacunasCalendario = remember(comunidad) { CalendarioVacunal.totalItems(comunidad) }
    val totalVacunasPuestas = remember(vacunas, vacunasPropias) {
        vacunas.count { it.puesta } + vacunasPropias.count { it.puesta }
    }

    val subtitulo = when (seccion) {
        SeccionSalud.VACUNAS -> {
            val total = totalVacunasCalendario + vacunasPropias.size
            if (total == 0) "Sin vacunas añadidas" else "$totalVacunasPuestas de $total marcadas"
        }

        SeccionSalud.CITAS -> {
            val n = citasProximas.size
            when {
                n == 0 -> "Sin citas pendientes"
                n == 1 -> "1 cita pendiente"
                else -> "$n citas pendientes"
            }
        }

        SeccionSalud.ENFERMEDADES -> {
            val activas = enfermedadesActivas.size
            val total = enfermedades.size
            when {
                total == 0 -> "Sin enfermedades registradas"
                activas == 0 -> if (total == 1) "1 enfermedad pasada" else "$total enfermedades pasadas"
                else -> "$activas en curso · $total en total"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NubiGeometria.margen)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Salud", style = MaterialTheme.typography.displaySmall)
                Text(subtitulo, style = MaterialTheme.typography.bodyMedium, color = colores.tintaSuave)
            }
            when (seccion) {
                SeccionSalud.CITAS -> Box(modifier = Modifier.clickable {
                    editandoCita = Cita(bebeId = bebe.id, fecha = Instant.now())
                }) {
                    BadgeIcono(
                        IconoNubi.MAS, colorFondo = colores.lila, colorIcono = colores.tinta,
                        tamano = 42.dp, tamanoIcono = 18.dp
                    )
                }

                SeccionSalud.ENFERMEDADES -> Box(modifier = Modifier.clickable {
                    editandoEnfermedad = Enfermedad(bebeId = bebe.id, fechaInicio = Instant.now())
                }) {
                    BadgeIcono(
                        IconoNubi.MAS, colorFondo = colores.lila, colorIcono = colores.tinta,
                        tamano = 42.dp, tamanoIcono = 18.dp
                    )
                }

                SeccionSalud.VACUNAS -> {}
            }
        }

        Spacer(Modifier.height(16.dp))
        SelectorSeccionSalud(seccion) { seccion = it }
        Spacer(Modifier.height(16.dp))

        when (seccion) {
            SeccionSalud.VACUNAS -> BloqueVacunas(
                bebe = bebe,
                comunidad = comunidad,
                gruposCalendario = gruposCalendario,
                vacunas = vacunas,
                gruposVacunas = gruposVacunas,
                vacunasPropias = vacunasPropias,
                onElegirComunidad = { eligiendoComunidad = true },
                onCrearGrupo = { creandoGrupo = true },
                onAnadirAGrupo = { anadiendoAGrupo = it }
            )

            SeccionSalud.CITAS -> BloqueCitas(
                citasProximas = citasProximas,
                citasPasadas = citasPasadas,
                proximaCitaId = proximaCitaId,
                onNueva = { editandoCita = Cita(bebeId = bebe.id, fecha = Instant.now()) },
                onEditar = { editandoCita = it }
            )

            SeccionSalud.ENFERMEDADES -> BloqueEnfermedades(
                enfermedadesActivas = enfermedadesActivas,
                enfermedadesPasadas = enfermedadesPasadas,
                onNueva = { editandoEnfermedad = Enfermedad(bebeId = bebe.id, fechaInicio = Instant.now()) },
                onEditar = { editandoEnfermedad = it }
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    editandoCita?.let { c ->
        HojaCita(
            cita = c,
            esNueva = citas.none { it.id == c.id },
            onGuardar = { Repositorio.guardarCita(it); editandoCita = null },
            onBorrar = { Repositorio.borrarCita(c.id); editandoCita = null },
            onCerrar = { editandoCita = null }
        )
    }
    editandoEnfermedad?.let { e ->
        HojaEnfermedad(
            enfermedad = e,
            esNueva = enfermedades.none { it.id == e.id },
            onGuardar = { Repositorio.guardarEnfermedad(it); editandoEnfermedad = null },
            onBorrar = { Repositorio.borrarEnfermedad(e.id); editandoEnfermedad = null },
            onCerrar = { editandoEnfermedad = null }
        )
    }
    if (eligiendoComunidad) {
        HojaComunidad(
            seleccion = comunidad,
            onElegir = { Repositorio.guardarComunidad(it.name); eligiendoComunidad = false },
            onCerrar = { eligiendoComunidad = false }
        )
    }
    if (creandoGrupo) {
        HojaNuevaEdad(
            onCrear = { nombre, meses -> Repositorio.anadirGrupoPropio(bebe.id, nombre, meses); creandoGrupo = false },
            onCerrar = { creandoGrupo = false }
        )
    }
    anadiendoAGrupo?.let { g ->
        HojaAnadirVacuna(
            grupo = g,
            onAnadir = { nombre, puesta, fecha ->
                Repositorio.anadirVacunaPropia(g.id, nombre, puesta, fecha)
                anadiendoAGrupo = null
            },
            onCerrar = { anadiendoAGrupo = null }
        )
    }
}

@Composable
private fun SelectorSeccionSalud(seleccion: SeccionSalud, onSeleccion: (SeccionSalud) -> Unit) {
    val colores = NubiTheme.colores
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(colores.lienzoAlto)
            .padding(4.dp)
    ) {
        SeccionSalud.entries.forEach { s ->
            val seleccionada = seleccion == s
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (seleccionada) colores.superficie else Color.Transparent)
                    .clickable { onSeleccion(s) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    s.titulo,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (seleccionada) colores.tinta else colores.tintaTenue
                )
            }
        }
    }
}

// -----------------------------------------------------------------
// Vacunas
// -----------------------------------------------------------------

@Composable
private fun BloqueVacunas(
    bebe: Bebe,
    comunidad: Comunidad,
    gruposCalendario: List<GrupoVacunal>,
    vacunas: List<EstadoVacuna>,
    gruposVacunas: List<GrupoVacunas>,
    vacunasPropias: List<VacunaPropia>,
    onElegirComunidad: () -> Unit,
    onCrearGrupo: () -> Unit,
    onAnadirAGrupo: (GrupoVacunas) -> Unit
) {
    val colores = NubiTheme.colores
    val meses = bebe.edadEnMeses()

    SelectorComunidad(comunidad, onClick = onElegirComunidad)
    Spacer(Modifier.height(14.dp))

    if (comunidad == Comunidad.OTROS) {
        Tarjeta {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EtiquetaSeccion("Otros países")
                Text(
                    "Cada país tiene su propio calendario oficial",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colores.tinta
                )
                Text(
                    "Para no mostrar un calendario que no corresponda a tu país, aquí puedes anotar manualmente las vacunas que le pongan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.tintaSuave
                )
            }
        }
        Spacer(Modifier.height(14.dp))
    } else {
        val siguiente = CalendarioVacunal.siguienteGrupo(meses, comunidad)
        if (siguiente != null) {
            Tarjeta {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    BadgeIcono(
                        IconoNubi.JERINGA, colorFondo = colores.cielo, colorIcono = colores.tinta,
                        tamano = 40.dp, tamanoIcono = 18.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        EtiquetaSeccion("Siguiente tanda")
                        Text(siguiente.etiqueta, style = MaterialTheme.typography.titleLarge, color = colores.tinta)
                        Text(
                            siguiente.items.joinToString(" · ") { it.nombre },
                            style = MaterialTheme.typography.bodySmall,
                            color = colores.tintaSuave,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        val grupoActualId = CalendarioVacunal.grupoActual(meses, comunidad)?.id
        gruposCalendario.forEach { grupo ->
            val clavesGrupo = grupo.items.map { it.id }.toSet()
            val puestas = vacunas.count { it.clave in clavesGrupo && it.puesta }
            val color = colorGrupo(puestas, grupo.items.size, grupo.meses, meses, colores)
            Tarjeta {
                Desplegable(
                    titulo = grupo.etiqueta,
                    subtitulo = estadoGrupoTexto(puestas, grupo.items.size),
                    icono = IconoNubi.JERINGA,
                    colorIcono = color,
                    abiertoInicial = grupo.id == grupoActualId
                ) {
                    grupo.items.forEach { item ->
                        val estado = vacunas.find { it.clave == item.id }
                        FilaVacunaCalendario(
                            item = item,
                            puesta = estado?.puesta == true,
                            fecha = estado?.fecha,
                            onToggle = { Repositorio.marcarVacuna(bebe.id, item.id, estado?.puesta != true) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }

    BloquePropio(bebe, gruposVacunas, vacunasPropias, onCrearGrupo, onAnadirAGrupo)

    if (comunidad != Comunidad.OTROS) {
        Spacer(Modifier.height(10.dp))
        Text(CalendarioVacunal.avisoGeneral, style = MaterialTheme.typography.bodySmall, color = colores.tintaTenue)
    }
}

@Composable
private fun SelectorComunidad(comunidad: Comunidad, onClick: () -> Unit) {
    val colores = NubiTheme.colores
    Tarjeta {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BadgeIcono(
                    IconoNubi.BOTIQUIN, colorFondo = colores.menta, colorIcono = colores.tinta,
                    tamano = 34.dp, tamanoIcono = 15.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    EtiquetaSeccion("Calendario de")
                    Text(
                        comunidad.nombre,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colores.tinta
                    )
                }
                Icono(IconoNubi.CHEVRON, tint = colores.tintaTenue, tamano = 14.dp)
            }
            Text(CalendarioVacunal.aviso(comunidad), style = MaterialTheme.typography.bodySmall, color = colores.tintaSuave)
        }
    }
}

@Composable
private fun BloquePropio(
    bebe: Bebe,
    gruposVacunas: List<GrupoVacunas>,
    vacunasPropias: List<VacunaPropia>,
    onCrearGrupo: () -> Unit,
    onAnadirAGrupo: (GrupoVacunas) -> Unit
) {
    val colores = NubiTheme.colores
    val grupos = remember(gruposVacunas) { gruposVacunas.sortedBy { it.meses } }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        EtiquetaSeccion("Añadidas por ti")
        Spacer(Modifier.weight(1f))
        BotonSecundarioChico("Añadir edad", IconoNubi.MAS, onCrearGrupo)
    }
    Spacer(Modifier.height(10.dp))

    if (grupos.isEmpty()) {
        Tarjeta {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Tu propio bloque",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colores.tinta
                )
                Text(
                    "Crea una edad (“5 meses”, “antes del viaje”) y añade dentro las vacunas que le hayan puesto y no estén en el calendario de arriba.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.tintaSuave
                )
            }
        }
    } else {
        grupos.forEach { grupo ->
            val vacunasDeEsteGrupo = vacunasPropias.filter { it.grupoId == grupo.id }
            val puestas = vacunasDeEsteGrupo.count { it.puesta }
            Tarjeta {
                Desplegable(
                    titulo = grupo.etiqueta,
                    subtitulo = if (vacunasDeEsteGrupo.isEmpty()) "Sin vacunas todavía"
                    else estadoGrupoTexto(puestas, vacunasDeEsteGrupo.size),
                    icono = IconoNubi.JERINGA,
                    colorIcono = colores.mantequilla,
                    abiertoInicial = true
                ) {
                    vacunasDeEsteGrupo.forEach { v ->
                        FilaVacunaPropia(
                            vacuna = v,
                            onToggle = { Repositorio.marcarVacunaPropia(v.id, !v.puesta) },
                            onBorrar = { Repositorio.borrarVacunaPropia(v.id) }
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        BotonSecundarioChico("Añadir vacuna", IconoNubi.MAS) { onAnadirAGrupo(grupo) }
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier.size(34.dp).clickable { Repositorio.borrarGrupoPropio(grupo.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icono(IconoNubi.PAPELERA, tint = colores.tintaTenue, tamano = 15.dp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun Desplegable(
    titulo: String,
    subtitulo: String,
    icono: IconoNubi,
    colorIcono: Color,
    abiertoInicial: Boolean,
    contenido: @Composable () -> Unit
) {
    var abierto by remember { mutableStateOf(abiertoInicial) }
    val colores = NubiTheme.colores
    Column(Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { abierto = !abierto },
            verticalAlignment = Alignment.CenterVertically
        ) {
            BadgeIcono(icono, colorFondo = colorIcono, colorIcono = colores.tinta, tamano = 38.dp, tamanoIcono = 16.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    titulo,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colores.tinta
                )
                Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = colores.tintaSuave)
            }
            Icono(
                IconoNubi.CHEVRON, tint = colores.tintaTenue, tamano = 14.dp,
                modifier = Modifier.rotate(if (abierto) 90f else 0f)
            )
        }
        if (abierto) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = colores.separador)
            Spacer(Modifier.height(10.dp))
            Column { contenido() }
        }
    }
}

@Composable
private fun FilaVacunaCalendario(item: ItemVacuna, puesta: Boolean, fecha: Instant?, onToggle: () -> Unit) {
    val colores = NubiTheme.colores
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Casilla(puesta)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.nombre,
                style = MaterialTheme.typography.bodyMedium,
                color = if (puesta) colores.tintaSuave else colores.tinta,
                textDecoration = if (puesta) TextDecoration.LineThrough else null
            )
            if (item.detalle.isNotEmpty()) {
                Text(item.detalle, style = MaterialTheme.typography.bodySmall, color = colores.tintaTenue)
            }
            if (puesta && fecha != null) {
                Text(
                    "Puesta el ${textoFechaCorta(fecha)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = colores.indigo
                )
            }
        }
    }
}

@Composable
private fun FilaVacunaPropia(vacuna: VacunaPropia, onToggle: () -> Unit, onBorrar: () -> Unit) {
    val colores = NubiTheme.colores
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.clickable(onClick = onToggle)) { Casilla(vacuna.puesta) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                vacuna.nombre,
                style = MaterialTheme.typography.bodyMedium,
                color = if (vacuna.puesta) colores.tintaSuave else colores.tinta,
                textDecoration = if (vacuna.puesta) TextDecoration.LineThrough else null
            )
            if (vacuna.puesta && vacuna.fecha != null) {
                Text(
                    "Puesta el ${textoFechaCorta(vacuna.fecha)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = colores.indigo
                )
            }
        }
        Box(
            modifier = Modifier.size(30.dp).clickable(onClick = onBorrar),
            contentAlignment = Alignment.Center
        ) {
            Icono(IconoNubi.EQUIS, tint = colores.tintaTenue, tamano = 12.dp)
        }
    }
}

@Composable
private fun Casilla(puesta: Boolean) {
    val colores = NubiTheme.colores
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (puesta) colores.menta else Color.Transparent)
            .border(1.8.dp, if (puesta) Color.Transparent else colores.tintaTenue, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (puesta) Icono(IconoNubi.CHECK, tint = colores.tinta, tamano = 15.dp)
    }
}

// -----------------------------------------------------------------
// Citas
// -----------------------------------------------------------------

@Composable
private fun BloqueCitas(
    citasProximas: List<Cita>,
    citasPasadas: List<Cita>,
    proximaCitaId: String?,
    onNueva: () -> Unit,
    onEditar: (Cita) -> Unit
) {
    val colores = NubiTheme.colores
    if (citasProximas.isEmpty() && citasPasadas.isEmpty()) {
        Tarjeta {
            EstadoVacio(
                icono = IconoNubi.CALENDARIO,
                titulo = "Ninguna cita apuntada",
                texto = "Anota la próxima revisión para no perderla de vista.",
                color = colores.menta
            )
        }
        Spacer(Modifier.height(14.dp))
        BotonPrimario("Apuntar una cita", IconoNubi.MAS, colores.lila, onNueva)
    } else {
        if (citasProximas.isNotEmpty()) {
            Tarjeta {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    EtiquetaSeccion("Pendientes")
                    citasProximas.forEachIndexed { i, c ->
                        FilaCita(c, destacada = c.id == proximaCitaId, onClick = { onEditar(c) })
                        if (i != citasProximas.lastIndex) HorizontalDivider(color = colores.separador)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        if (citasPasadas.isNotEmpty()) {
            val mostrar = citasPasadas.take(10)
            Tarjeta {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    EtiquetaSeccion("Ya pasadas")
                    mostrar.forEachIndexed { i, c ->
                        FilaCita(c, destacada = false, onClick = { onEditar(c) })
                        if (i != mostrar.lastIndex) HorizontalDivider(color = colores.separador)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaCita(c: Cita, destacada: Boolean, onClick: () -> Unit) {
    val colores = NubiTheme.colores
    val pasada = c.pasada()
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BadgeIcono(
            iconoTipoCita(c.tipo),
            colorFondo = if (pasada) colores.lila.copy(alpha = 0.25f) else colorTipoCita(c.tipo, colores),
            colorIcono = colores.tinta, tamano = 38.dp, tamanoIcono = 17.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tituloVisibleCita(c),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (pasada) colores.tintaSuave else colores.tinta
            )
            Text(
                if (pasada) textoFechaCorta(c.fecha)
                else textoCuandoFalta(c.fecha) + if (c.lugar.isNotEmpty()) " · ${c.lugar}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = colores.tintaSuave
            )
        }
        if (destacada) {
            Pastilla("Próxima", colores.menta)
            Spacer(Modifier.width(8.dp))
        }
        Icono(IconoNubi.CHEVRON, tint = colores.tintaTenue, tamano = 13.dp)
    }
}

// -----------------------------------------------------------------
// Enfermedades
// -----------------------------------------------------------------

@Composable
private fun BloqueEnfermedades(
    enfermedadesActivas: List<Enfermedad>,
    enfermedadesPasadas: List<Enfermedad>,
    onNueva: () -> Unit,
    onEditar: (Enfermedad) -> Unit
) {
    val colores = NubiTheme.colores
    if (enfermedadesActivas.isEmpty() && enfermedadesPasadas.isEmpty()) {
        Tarjeta {
            EstadoVacio(
                icono = IconoNubi.BOTIQUIN,
                titulo = "Ninguna enfermedad registrada",
                texto = "Anota resfriados, gripes, alergias o cualquier proceso de salud que quieras recordar.",
                color = colores.coral
            )
        }
        Spacer(Modifier.height(14.dp))
        BotonPrimario("Registra una enfermedad", IconoNubi.MAS, colores.lila, onNueva)
    } else {
        if (enfermedadesActivas.isNotEmpty()) {
            Tarjeta {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    EtiquetaSeccion("En curso")
                    enfermedadesActivas.forEachIndexed { i, e ->
                        FilaEnfermedad(e) { onEditar(e) }
                        if (i != enfermedadesActivas.lastIndex) HorizontalDivider(color = colores.separador)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        if (enfermedadesPasadas.isNotEmpty()) {
            val mostrar = enfermedadesPasadas.take(20)
            Tarjeta {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    EtiquetaSeccion("Pasadas")
                    mostrar.forEachIndexed { i, e ->
                        FilaEnfermedad(e) { onEditar(e) }
                        if (i != mostrar.lastIndex) HorizontalDivider(color = colores.separador)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaEnfermedad(e: Enfermedad, onClick: () -> Unit) {
    val colores = NubiTheme.colores
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BadgeIcono(
            IconoNubi.BOTIQUIN,
            colorFondo = if (e.activa) colores.coral else colores.lila.copy(alpha = 0.25f),
            colorIcono = colores.tinta, tamano = 38.dp, tamanoIcono = 17.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.nombre.ifEmpty { "Sin nombre" },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (e.activa) colores.tinta else colores.tintaSuave
            )
            Text(textoDuracionEnfermedad(e), style = MaterialTheme.typography.bodySmall, color = colores.tintaSuave)
            if (e.medicacion.isNotEmpty()) {
                Text(
                    "💊 ${e.medicacion}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colores.tintaTenue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (e.activa) {
            Pastilla("En curso", colores.coral)
            Spacer(Modifier.width(8.dp))
        }
        Icono(IconoNubi.CHEVRON, tint = colores.tintaTenue, tamano = 13.dp)
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
private fun EtiquetaSeccion(texto: String) {
    Text(
        texto.uppercase(Locale("es", "ES")),
        style = MaterialTheme.typography.labelSmall,
        color = NubiTheme.colores.tintaTenue,
        letterSpacing = 0.5.sp
    )
}

@Composable
private fun Pastilla(texto: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = NubiTheme.colores.tinta
        )
    }
}

@Composable
private fun BotonPrimario(texto: String, icono: IconoNubi, color: Color, onClick: () -> Unit) {
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
private fun BotonSecundarioChico(texto: String, icono: IconoNubi, onClick: () -> Unit) {
    val colores = NubiTheme.colores
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colores.lienzoAlto)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icono(icono, tint = colores.tinta, tamano = 12.dp)
        Spacer(Modifier.width(5.dp))
        Text(texto, style = MaterialTheme.typography.labelMedium, color = colores.tinta)
    }
}

@Composable
private fun EstadoVacio(icono: IconoNubi, titulo: String, texto: String, color: Color) {
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
// Hojas (ModalBottomSheet)
// -----------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaCita(cita: Cita, esNueva: Boolean, onGuardar: (Cita) -> Unit, onBorrar: () -> Unit, onCerrar: () -> Unit) {
    val colores = NubiTheme.colores
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tipo by remember { mutableStateOf(cita.tipo) }
    var titulo by remember { mutableStateOf(cita.titulo) }
    var fecha by remember { mutableStateOf(cita.fecha) }
    var lugar by remember { mutableStateOf(cita.lugar) }
    var nota by remember { mutableStateOf(cita.nota) }
    var recordar by remember { mutableStateOf(cita.recordar) }
    var mostrarSelectorFecha by remember { mutableStateOf(false) }
    var mostrarSelectorHora by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = sheetState, containerColor = colores.lienzo) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NubiGeometria.margen)
                .padding(bottom = 28.dp)
        ) {
            Text(if (esNueva) "Nueva cita" else "Cita", style = MaterialTheme.typography.titleLarge, color = colores.tinta)
            Spacer(Modifier.height(18.dp))

            Tarjeta {
                Column(Modifier.padding(16.dp)) {
                    EtiquetaSeccion("Con quién")
                    Spacer(Modifier.height(10.dp))
                    SelectorTipoCita(tipo) { tipo = it }
                }
            }
            Spacer(Modifier.height(14.dp))

            Tarjeta {
                Column(Modifier.padding(16.dp)) {
                    Text("Motivo", style = MaterialTheme.typography.labelMedium, color = colores.tintaTenue)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = titulo, onValueChange = { titulo = it },
                        placeholder = { Text("Revisión de los 4 meses") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(color = colores.separador, modifier = Modifier.padding(vertical = 12.dp))
                    Text("Cuándo", style = MaterialTheme.typography.labelMedium, color = colores.tintaTenue)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { mostrarSelectorFecha = true }) { Text(textoFechaCorta(fecha)) }
                        OutlinedButton(onClick = { mostrarSelectorHora = true }) { Text(formatoHoraSalud.format(fecha)) }
                    }
                    HorizontalDivider(color = colores.separador, modifier = Modifier.padding(vertical = 12.dp))
                    Text("Dónde", style = MaterialTheme.typography.labelMedium, color = colores.tintaTenue)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = lugar, onValueChange = { lugar = it },
                        placeholder = { Text("Centro de salud") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            Tarjeta {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { recordar = !recordar },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Avisarme la tarde de antes", style = MaterialTheme.typography.bodyMedium, color = colores.tinta)
                            Text("Aviso local en este móvil", style = MaterialTheme.typography.labelSmall, color = colores.tintaTenue)
                        }
                        Switch(checked = recordar, onCheckedChange = { recordar = it })
                    }
                    HorizontalDivider(color = colores.separador, modifier = Modifier.padding(vertical = 12.dp))
                    Text("Notas", style = MaterialTheme.typography.labelMedium, color = colores.tintaTenue)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = nota, onValueChange = { nota = it },
                        placeholder = { Text("Preguntas que quieres hacer…") },
                        minLines = 2, maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            Button(
                onClick = {
                    onGuardar(cita.copy(tipo = tipo, titulo = titulo, fecha = fecha, lugar = lugar, nota = nota, recordar = recordar))
                },
                colors = ButtonDefaults.buttonColors(containerColor = colores.lila, contentColor = colores.tinta),
                shape = RoundedCornerShape(NubiGeometria.radioChico),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar cita") }

            if (!esNueva) {
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onBorrar, modifier = Modifier.fillMaxWidth()) {
                    Icono(IconoNubi.PAPELERA, tint = colores.indigo, tamano = 14.dp)
                    Spacer(Modifier.width(7.dp))
                    Text("Borrar esta cita", color = colores.indigo)
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
                    estado.selectedDateMillis?.let { millis ->
                        val diaNuevo = millisUtcAFecha(millis).atZone(zonaSalud).toLocalDate()
                        val horaVieja = fecha.atZone(zonaSalud).toLocalTime()
                        fecha = diaNuevo.atTime(horaVieja).atZone(zonaSalud).toInstant()
                    }
                    mostrarSelectorFecha = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { mostrarSelectorFecha = false }) { Text("Cancelar") } }
        ) { DatePicker(state = estado) }
    }

    if (mostrarSelectorHora) {
        val horaActual = fecha.atZone(zonaSalud).toLocalTime()
        val estado = rememberTimePickerState(initialHour = horaActual.hour, initialMinute = horaActual.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { mostrarSelectorHora = false },
            confirmButton = {
                TextButton(onClick = {
                    val dia = fecha.atZone(zonaSalud).toLocalDate()
                    fecha = dia.atTime(estado.hour, estado.minute).atZone(zonaSalud).toInstant()
                    mostrarSelectorHora = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { mostrarSelectorHora = false }) { Text("Cancelar") } },
            text = { TimePicker(state = estado) }
        )
    }
}

@Composable
private fun SelectorTipoCita(seleccion: TipoCita, onSeleccion: (TipoCita) -> Unit) {
    val colores = NubiTheme.colores
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(colores.lienzoAlto)
            .padding(4.dp)
    ) {
        TipoCita.entries.forEach { t ->
            val sel = seleccion == t
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) colores.superficie else Color.Transparent)
                    .clickable { onSeleccion(t) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tituloTipoCita(t),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (sel) colores.tinta else colores.tintaTenue
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaEnfermedad(
    enfermedad: Enfermedad,
    esNueva: Boolean,
    onGuardar: (Enfermedad) -> Unit,
    onBorrar: () -> Unit,
    onCerrar: () -> Unit
) {
    val colores = NubiTheme.colores
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var nombre by remember { mutableStateOf(enfermedad.nombre) }
    var fechaInicio by remember { mutableStateOf(enfermedad.fechaInicio) }
    var enCurso by remember { mutableStateOf(enfermedad.activa) }
    var fechaFin by remember { mutableStateOf(enfermedad.fechaFin ?: Instant.now()) }
    var medicacion by remember { mutableStateOf(enfermedad.medicacion) }
    var notas by remember { mutableStateOf(enfermedad.notas) }
    var mostrarSelectorInicio by remember { mutableStateOf(false) }
    var mostrarSelectorFin by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = sheetState, containerColor = colores.lienzo) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NubiGeometria.margen)
                .padding(bottom = 28.dp)
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BadgeIcono(IconoNubi.BOTIQUIN, colorFondo = colores.coral, colorIcono = colores.tinta, tamano = 54.dp, tamanoIcono = 24.dp)
                Spacer(Modifier.height(10.dp))
                Text(
                    if (esNueva) "Nueva enfermedad" else "Editar enfermedad",
                    style = MaterialTheme.typography.titleLarge, color = colores.tinta
                )
            }
            Spacer(Modifier.height(18.dp))

            Tarjeta {
                Column(Modifier.padding(16.dp)) {
                    Text("Enfermedad", style = MaterialTheme.typography.labelMedium, color = colores.tintaTenue)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = nombre, onValueChange = { nombre = it },
                        placeholder = { Text("Resfriado, gripe, otitis…") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(color = colores.separador, modifier = Modifier.padding(vertical = 12.dp))
                    Text("Fecha de inicio", style = MaterialTheme.typography.labelMedium, color = colores.tintaTenue)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { mostrarSelectorInicio = true }) { Text(textoFechaLarga(fechaInicio)) }
                    HorizontalDivider(color = colores.separador, modifier = Modifier.padding(vertical = 12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            enCurso = !enCurso
                            if (enCurso) fechaFin = Instant.now()
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Todavía en curso", style = MaterialTheme.typography.bodyMedium, color = colores.tinta)
                            Text("Desactívalo para marcar la fecha de fin", style = MaterialTheme.typography.labelSmall, color = colores.tintaTenue)
                        }
                        Switch(checked = enCurso, onCheckedChange = { enCurso = it; if (it) fechaFin = Instant.now() })
                    }
                    if (!enCurso) {
                        HorizontalDivider(color = colores.separador, modifier = Modifier.padding(vertical = 12.dp))
                        Text("Fecha de fin", style = MaterialTheme.typography.labelMedium, color = colores.tintaTenue)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { mostrarSelectorFin = true }) { Text(textoFechaLarga(fechaFin)) }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            Tarjeta {
                Column(Modifier.padding(16.dp)) {
                    Text("Medicación", style = MaterialTheme.typography.labelMedium, color = colores.tintaTenue)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = medicacion, onValueChange = { medicacion = it },
                        placeholder = { Text("Apiretal cada 8h, Augmentine…") },
                        minLines = 2, maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            Tarjeta {
                Column(Modifier.padding(16.dp)) {
                    Text("Notas", style = MaterialTheme.typography.labelMedium, color = colores.tintaTenue)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = notas, onValueChange = { notas = it },
                        placeholder = { Text("Síntomas, evolución, visitas al médico…") },
                        minLines = 3, maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            Button(
                onClick = {
                    onGuardar(
                        enfermedad.copy(
                            nombre = nombre,
                            fechaInicio = fechaInicio,
                            fechaFin = if (enCurso) null else fechaFin,
                            medicacion = medicacion,
                            notas = notas
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = colores.lila, contentColor = colores.tinta),
                shape = RoundedCornerShape(NubiGeometria.radioChico),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar") }

            if (!esNueva) {
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onBorrar, modifier = Modifier.fillMaxWidth()) {
                    Icono(IconoNubi.PAPELERA, tint = colores.indigo, tamano = 14.dp)
                    Spacer(Modifier.width(7.dp))
                    Text("Borrar esta enfermedad", color = colores.indigo)
                }
            }
        }
    }

    if (mostrarSelectorInicio) {
        val estado = rememberDatePickerState(initialSelectedDateMillis = fechaAMillisUtc(fechaInicio))
        DatePickerDialog(
            onDismissRequest = { mostrarSelectorInicio = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { fechaInicio = millisUtcAFecha(it) }
                    mostrarSelectorInicio = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { mostrarSelectorInicio = false }) { Text("Cancelar") } }
        ) { DatePicker(state = estado) }
    }
    if (mostrarSelectorFin) {
        val estado = rememberDatePickerState(initialSelectedDateMillis = fechaAMillisUtc(fechaFin))
        DatePickerDialog(
            onDismissRequest = { mostrarSelectorFin = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { fechaFin = millisUtcAFecha(it) }
                    mostrarSelectorFin = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { mostrarSelectorFin = false }) { Text("Cancelar") } }
        ) { DatePicker(state = estado) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaComunidad(seleccion: Comunidad, onElegir: (Comunidad) -> Unit, onCerrar: () -> Unit) {
    val colores = NubiTheme.colores
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = sheetState, containerColor = colores.lienzo) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NubiGeometria.margen)
                .padding(bottom = 28.dp)
        ) {
            Text("Calendario", style = MaterialTheme.typography.titleLarge, color = colores.tinta)
            Spacer(Modifier.height(10.dp))
            Text(
                "Elige el calendario oficial que quieres ver. Si no estás en España, selecciona Otros países y añade manualmente las vacunas.",
                style = MaterialTheme.typography.bodySmall, color = colores.tintaSuave
            )
            Spacer(Modifier.height(14.dp))
            Tarjeta {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Comunidad.entries.forEachIndexed { i, c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onElegir(c) }
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                c.nombre,
                                style = if (seleccion == c) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                else MaterialTheme.typography.bodyLarge,
                                color = colores.tinta,
                                modifier = Modifier.weight(1f)
                            )
                            if (seleccion == c) Icono(IconoNubi.CHECK, tint = colores.indigo, tamano = 16.dp)
                        }
                        if (i != Comunidad.entries.lastIndex) HorizontalDivider(color = colores.separador)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaNuevaEdad(onCrear: (String, Double) -> Unit, onCerrar: () -> Unit) {
    val colores = NubiTheme.colores
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var usarTexto by remember { mutableStateOf(false) }
    var meses by remember { mutableIntStateOf(5) }
    var etiqueta by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = sheetState, containerColor = colores.lienzo) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NubiGeometria.margen)
                .padding(bottom = 28.dp)
        ) {
            Text("Nueva edad", style = MaterialTheme.typography.titleLarge, color = colores.tinta)
            Spacer(Modifier.height(18.dp))
            Tarjeta {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SelectorPorTextoOMeses(usarTexto) { usarTexto = it }
                    if (usarTexto) {
                        Column {
                            Text("Nombre del bloque", style = MaterialTheme.typography.labelMedium, color = colores.tintaTenue)
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = etiqueta, onValueChange = { etiqueta = it },
                                placeholder = { Text("Antes del viaje") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Column {
                            Text("Edad", style = MaterialTheme.typography.labelMedium, color = colores.tintaTenue)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                TextButton(onClick = { if (meses > 0) meses-- }) {
                                    Text("–", style = MaterialTheme.typography.headlineSmall, color = colores.tinta)
                                }
                                Text(
                                    if (meses == 1) "1 mes" else "$meses meses",
                                    style = MaterialTheme.typography.bodyLarge, color = colores.tinta
                                )
                                TextButton(onClick = { if (meses < 216) meses++ }) {
                                    Text("+", style = MaterialTheme.typography.headlineSmall, color = colores.tinta)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    val nombre = if (usarTexto) etiqueta.trim() else if (meses == 1) "1 mes" else "$meses meses"
                    if (nombre.isNotEmpty()) onCrear(nombre, if (usarTexto) 999.0 else meses.toDouble())
                },
                colors = ButtonDefaults.buttonColors(containerColor = colores.lila, contentColor = colores.tinta),
                shape = RoundedCornerShape(NubiGeometria.radioChico),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Crear bloque") }
        }
    }
}

@Composable
private fun SelectorPorTextoOMeses(usarTexto: Boolean, onCambiar: (Boolean) -> Unit) {
    val colores = NubiTheme.colores
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(colores.lienzoAlto)
            .padding(4.dp)
    ) {
        listOf(false to "Por meses", true to "Texto libre").forEach { (valor, titulo) ->
            val sel = usarTexto == valor
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaAnadirVacuna(grupo: GrupoVacunas, onAnadir: (String, Boolean, Instant?) -> Unit, onCerrar: () -> Unit) {
    val colores = NubiTheme.colores
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var busqueda by remember { mutableStateOf("") }
    var yaPuesta by remember { mutableStateOf(true) }
    var fecha by remember { mutableStateOf(Instant.now()) }
    var mostrarSelectorFecha by remember { mutableStateOf(false) }

    val resultados = remember(busqueda) { CatalogoVacunas.buscar(busqueda) }
    val textoLimpio = busqueda.trim()
    val esNombreNuevo = textoLimpio.isNotEmpty() && resultados.none { it.equals(textoLimpio, ignoreCase = true) }

    fun anadir(nombre: String) {
        onAnadir(nombre, yaPuesta, if (yaPuesta) fecha else null)
    }

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = sheetState, containerColor = colores.lienzo) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NubiGeometria.margen)
                .padding(bottom = 28.dp)
        ) {
            Text(grupo.etiqueta, style = MaterialTheme.typography.titleLarge, color = colores.tinta)
            Spacer(Modifier.height(18.dp))

            Tarjeta {
                Column(Modifier.padding(16.dp)) {
                    Text("Buscar o escribir", style = MaterialTheme.typography.labelMedium, color = colores.tintaTenue)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = busqueda, onValueChange = { busqueda = it },
                        placeholder = { Text("Nombre de la vacuna") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            Tarjeta {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { yaPuesta = !yaPuesta },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ya se la han puesto", style = MaterialTheme.typography.bodyMedium, color = colores.tinta, modifier = Modifier.weight(1f))
                        Switch(checked = yaPuesta, onCheckedChange = { yaPuesta = it })
                    }
                    if (yaPuesta) {
                        HorizontalDivider(color = colores.separador, modifier = Modifier.padding(vertical = 12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Fecha", style = MaterialTheme.typography.labelMedium, color = colores.tintaTenue, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { mostrarSelectorFecha = true }) { Text(textoFechaCorta(fecha)) }
                        }
                    }
                }
            }

            if (esNombreNuevo) {
                Spacer(Modifier.height(14.dp))
                BotonPrimario("Añadir “$textoLimpio”", IconoNubi.MAS, colores.mantequilla) { anadir(textoLimpio) }
            }

            if (resultados.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Tarjeta {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        resultados.forEachIndexed { i, nombre ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { anadir(nombre) }
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BadgeIcono(IconoNubi.JERINGA, colorFondo = colores.cielo, colorIcono = colores.tinta, tamano = 30.dp, tamanoIcono = 13.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(nombre, style = MaterialTheme.typography.bodyMedium, color = colores.tinta, modifier = Modifier.weight(1f))
                                Icono(IconoNubi.MAS, tint = colores.tintaTenue, tamano = 13.dp)
                            }
                            if (i != resultados.lastIndex) HorizontalDivider(color = colores.separador)
                        }
                    }
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
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { mostrarSelectorFecha = false }) { Text("Cancelar") } }
        ) { DatePicker(state = estado) }
    }
}

// -----------------------------------------------------------------
// Ayudantes de texto y fecha
// -----------------------------------------------------------------

private fun estadoGrupoTexto(puestas: Int, total: Int): String = when {
    puestas == 0 -> if (total == 1) "1 vacuna" else "$total vacunas"
    puestas == total -> "Todas puestas"
    else -> "$puestas de $total puestas"
}

private fun colorGrupo(puestas: Int, total: Int, mesesGrupo: Double, mesesBebe: Double, c: NubiColores): Color = when {
    puestas == total && total > 0 -> c.menta
    mesesGrupo <= mesesBebe -> c.melocoton
    else -> c.lila.copy(alpha = 0.5f)
}

private fun tituloTipoCita(t: TipoCita): String = when (t) {
    TipoCita.PEDIATRA -> "Pediatra"
    TipoCita.ENFERMERIA -> "Enfermería"
    TipoCita.OTRA -> "Otra"
}

private fun iconoTipoCita(t: TipoCita): IconoNubi = when (t) {
    TipoCita.PEDIATRA -> IconoNubi.BOTIQUIN
    TipoCita.ENFERMERIA -> IconoNubi.JERINGA
    TipoCita.OTRA -> IconoNubi.CALENDARIO
}

private fun colorTipoCita(t: TipoCita, c: NubiColores): Color = when (t) {
    TipoCita.PEDIATRA -> c.menta
    TipoCita.ENFERMERIA -> c.cielo
    TipoCita.OTRA -> c.mantequilla
}

private fun tituloVisibleCita(c: Cita): String =
    c.titulo.ifEmpty { "Revisión con ${tituloTipoCita(c.tipo).lowercase()}" }

private fun textoDuracionEnfermedad(e: Enfermedad): String {
    val ini = textoFechaCorta(e.fechaInicio)
    val fin = e.fechaFin
    return if (fin != null) "$ini – ${textoFechaCorta(fin)}" else "$ini – en curso"
}

private val zonaSalud: ZoneId = ZoneId.systemDefault()

private val formatoHoraSalud: DateTimeFormatter =
    DateTimeFormatter.ofPattern("H:mm").withZone(zonaSalud)

private fun textoFechaCorta(fecha: Instant): String {
    val dia = fecha.atZone(zonaSalud).toLocalDate()
    val mes = dia.month.getDisplayName(TextStyle.SHORT, Locale("es", "ES"))
    return "${dia.dayOfMonth} $mes".lowercase()
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "ES")) else it.toString() }
}

private fun textoFechaLarga(fecha: Instant): String {
    val dia = fecha.atZone(zonaSalud).toLocalDate()
    val nombreDia = dia.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("es", "ES"))
    val nombreMes = dia.month.getDisplayName(TextStyle.FULL, Locale("es", "ES"))
    return "$nombreDia, ${dia.dayOfMonth} de $nombreMes"
        .replaceFirstChar { it.titlecase(Locale("es", "ES")) }
}

private fun textoCuandoFalta(fecha: Instant, ahora: Instant = Instant.now()): String {
    val hoy = ahora.atZone(zonaSalud).toLocalDate()
    val dia = fecha.atZone(zonaSalud).toLocalDate()
    return when {
        dia == hoy -> "hoy a las ${formatoHoraSalud.format(fecha)}"
        dia == hoy.plusDays(1) -> "mañana a las ${formatoHoraSalud.format(fecha)}"
        dia == hoy.minusDays(1) -> "ayer"
        else -> {
            val dias = ChronoUnit.DAYS.between(hoy, dia).toInt()
            if (dias in 1..29) (if (dias == 1) "en 1 día" else "en $dias días") else textoFechaCorta(fecha)
        }
    }
}

private fun fechaAMillisUtc(fecha: Instant): Long =
    fecha.atZone(zonaSalud).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun millisUtcAFecha(millis: Long): Instant =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zonaSalud).toInstant()

// =====================================================================
// Medicina — se abre desde la botonera de la pestaña Día (ver
// BotoneraDia en Pantallas.kt). A diferencia de Registro, esto se guarda
// en su propia tabla "medicinas" (ver Modelos.kt y Repositorio.kt).
//
// "Avisar de la siguiente toma" se deja preparado en la interfaz, pero
// todavía no programa ningún aviso real de Android: Medicina no tiene
// donde guardar esa preferencia (solo un horaSiguiente concreto), y
// armar una notificación de verdad es trabajo de una fase posterior.
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaMedicinaDia(
    bebeId: String,
    dia: LocalDate,
    horaInicial: Instant = Instant.now(),
    onCerrar: () -> Unit
) {
    val colores = NubiTheme.colores
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var nombre by remember { mutableStateOf("") }
    var fecha by remember {
        mutableStateOf(dia.atTime(horaInicial.atZone(zonaSalud).toLocalTime()).atZone(zonaSalud).toInstant())
    }
    var avisar by remember { mutableStateOf(false) }
    var nota by remember { mutableStateOf("") }
    var mostrarSelectorFecha by remember { mutableStateOf(false) }
    var mostrarSelectorHora by remember { mutableStateOf(false) }

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
                    IconoNubi.JERINGA,
                    colorFondo = Color.White,
                    colorIcono = Color.Black,
                    tamano = 54.dp,
                    tamanoIcono = 24.dp
                )
                Spacer(Modifier.height(10.dp))
                Text("Medicina", style = MaterialTheme.typography.titleLarge, color = colores.tinta)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Anota la toma y programa un aviso",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.tintaSuave
                )
            }
            Spacer(Modifier.height(18.dp))

            Tarjeta {
                Column(Modifier.padding(16.dp)) {
                    EtiquetaSeccion("Nombre de la medicina")
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = nombre, onValueChange = { nombre = it },
                        placeholder = { Text("Apiretal, Augmentine…") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(color = colores.separador, modifier = Modifier.padding(vertical = 12.dp))
                    EtiquetaSeccion("Hora de la toma")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { mostrarSelectorFecha = true }) { Text(textoFechaCorta(fecha)) }
                        OutlinedButton(onClick = { mostrarSelectorHora = true }) { Text(formatoHoraSalud.format(fecha)) }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            Tarjeta {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clickable { avisar = !avisar },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Avisar de la siguiente toma", style = MaterialTheme.typography.bodyMedium, color = colores.tinta)
                        Text(
                            "Te avisamos cuando llegue la hora",
                            style = MaterialTheme.typography.labelSmall,
                            color = colores.tintaTenue
                        )
                    }
                    Switch(checked = avisar, onCheckedChange = { avisar = it })
                }
            }
            Spacer(Modifier.height(14.dp))

            Tarjeta {
                Column(Modifier.padding(16.dp)) {
                    EtiquetaSeccion("Notas")
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = nota, onValueChange = { nota = it },
                        placeholder = { Text("Dosis, indicaciones, reacciones…") },
                        minLines = 2, maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            Button(
                onClick = {
                    Repositorio.guardarMedicina(
                        Medicina(bebeId = bebeId, fechaToma = fecha, nombre = nombre, nota = nota)
                    )
                    onCerrar()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colores.cielo, contentColor = colores.tinta),
                shape = RoundedCornerShape(NubiGeometria.radioChico),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar") }
        }
    }

    if (mostrarSelectorFecha) {
        val estado = rememberDatePickerState(initialSelectedDateMillis = fechaAMillisUtc(fecha))
        DatePickerDialog(
            onDismissRequest = { mostrarSelectorFecha = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { millis ->
                        val diaNuevo = millisUtcAFecha(millis).atZone(zonaSalud).toLocalDate()
                        val horaVieja = fecha.atZone(zonaSalud).toLocalTime()
                        fecha = diaNuevo.atTime(horaVieja).atZone(zonaSalud).toInstant()
                    }
                    mostrarSelectorFecha = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { mostrarSelectorFecha = false }) { Text("Cancelar") } }
        ) { DatePicker(state = estado) }
    }

    if (mostrarSelectorHora) {
        val horaActual = fecha.atZone(zonaSalud).toLocalTime()
        val estado = rememberTimePickerState(initialHour = horaActual.hour, initialMinute = horaActual.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { mostrarSelectorHora = false },
            confirmButton = {
                TextButton(onClick = {
                    val diaActual = fecha.atZone(zonaSalud).toLocalDate()
                    fecha = diaActual.atTime(estado.hour, estado.minute).atZone(zonaSalud).toInstant()
                    mostrarSelectorHora = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { mostrarSelectorHora = false }) { Text("Cancelar") } },
            text = { TimePicker(state = estado) }
        )
    }
}
