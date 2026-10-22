package app.nubi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nubi.data.Bebe
import app.nubi.data.LadoPecho
import app.nubi.data.MotorEtapas
import app.nubi.data.MotorSueno
import app.nubi.data.PrediccionDia
import app.nubi.data.Registro
import app.nubi.data.Repositorio
import app.nubi.data.Supabase
import app.nubi.data.TipoPrediccion
import app.nubi.data.TipoRegistro
import app.nubi.ui.theme.NubiGeometria
import app.nubi.ui.theme.NubiTheme
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// =====================================================================
// Canjear código — se usa en dos sitios: al entrar por primera vez y
// desde Ajustes, igual que en iOS.
// =====================================================================
@Composable
fun BloqueCanjearCodigo(
    alCanjear: () -> Unit = {}
) {
    var codigo by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }
    val ambito = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = codigo,
            onValueChange = { codigo = it },
            label = { Text("Código de regalo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                ambito.launch {
                    enviando = true
                    mensaje = try {
                        Supabase.client.postgrest.rpc(
                            "canjear_codigo_regalo",
                            buildJsonObject { put("p_codigo", codigo.trim()) }
                        )
                        alCanjear()
                        "¡Listo! Premium activado."
                    } catch (e: Exception) {
                        // El servidor lanza 'codigo_invalido' o 'codigo_ya_usado';
                        // se traducen para que el mensaje signifique algo.
                        when {
                            e.message?.contains("codigo_ya_usado") == true ->
                                "Ese código ya se había usado."
                            e.message?.contains("codigo_invalido") == true ->
                                "No encontramos ese código. Revisa que esté bien escrito."
                            else -> "No se pudo canjear ahora mismo. Inténtalo en un momento."
                        }
                    }
                    enviando = false
                }
            },
            enabled = codigo.isNotBlank() && !enviando,
            shape = RoundedCornerShape(NubiGeometria.radioChico),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (enviando) "Canjeando…" else "Canjear")
        }
        mensaje?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// =====================================================================
// Primera entrada: canjear código o saltar
// =====================================================================
@Composable
fun PantallaBienvenida(alContinuar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NubiGeometria.margen),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Nubi", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Si te han regalado un código, puedes canjearlo ahora.",
            style = MaterialTheme.typography.bodyMedium,
            color = NubiTheme.colores.tintaSuave
        )
        Spacer(Modifier.height(24.dp))

        BloqueCanjearCodigo(alCanjear = alContinuar)

        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = alContinuar,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Saltar")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Tienes 7 días de prueba. Siempre puedes canjear un código más tarde desde Ajustes.",
            style = MaterialTheme.typography.bodySmall,
            color = NubiTheme.colores.tintaTenue,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// =====================================================================
// Alta del bebé — sin esto no hay nada que mostrar
// =====================================================================
@Composable
fun PantallaNuevoBebe(alGuardar: () -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var dia by remember { mutableStateOf("") }
    var mes by remember { mutableStateOf("") }
    var anio by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NubiGeometria.margen),
        verticalArrangement = Arrangement.Center
    ) {
        Text("¿Cómo se llama?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it },
            label = { Text("Nombre del bebé") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        Text("Fecha de nacimiento", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = dia, onValueChange = { dia = it.filter(Char::isDigit).take(2) },
                label = { Text("Día") }, singleLine = true, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = mes, onValueChange = { mes = it.filter(Char::isDigit).take(2) },
                label = { Text("Mes") }, singleLine = true, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = anio, onValueChange = { anio = it.filter(Char::isDigit).take(4) },
                label = { Text("Año") }, singleLine = true, modifier = Modifier.weight(1.4f)
            )
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val fecha = runCatching {
                    LocalDate.of(anio.toInt(), mes.toInt(), dia.toInt())
                }.getOrNull()

                when {
                    nombre.isBlank() -> error = "Pon el nombre del bebé."
                    fecha == null -> error = "Esa fecha no existe. Revísala."
                    fecha.isAfter(LocalDate.now()) -> error = "La fecha no puede ser futura."
                    else -> {
                        Repositorio.guardarBebe(
                            Bebe(
                                nombre = nombre.trim(),
                                fechaNacimiento = fecha.atTime(LocalTime.NOON)
                                    .atZone(ZoneId.systemDefault()).toInstant()
                            )
                        )
                        alGuardar()
                    }
                }
            },
            shape = RoundedCornerShape(NubiGeometria.radioChico),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Empezar")
        }
    }
}

// =====================================================================
// Pestaña Hoy — resumen del día.
//
// Aquí NO se registra nada: en iOS (VistaInicio.swift) esta pantalla son
// tarjetas de resumen y la etapa del bebé. Los botones de apuntar viven
// en la pestaña Día.
// =====================================================================
private val formatoHora: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun PantallaHoy(
    bebe: Bebe,
    registros: List<Registro>,
    oscuro: Boolean = false,
    alCambiarTema: () -> Unit = {},
    alAbrirAjustes: () -> Unit = {}
) {
    val colores = NubiTheme.colores
    val ahora = Instant.now()

    // El sueño en curso, siesta o nocturno (el que no tiene "fin" todavía).
    val suenoEnCurso = registros
        .filter { it.tipo.esSueno && it.fin == null }
        .maxByOrNull { it.inicio }

    // Biberón y pecho se muestran los dos, no solo el que se apuntó en
    // último lugar: si el bebé toma pecho y biberón, las dos tienen que
    // verse aquí (antes solo salía una).
    val ultimoBiberon = registros.filter { it.tipo == TipoRegistro.BIBERON }.maxByOrNull { it.inicio }
    val ultimoPecho = registros.filter { it.tipo == TipoRegistro.PECHO }.maxByOrNull { it.inicio }
    val ultimoPanal = registros.filter { it.tipo == TipoRegistro.PANAL }.maxByOrNull { it.inicio }

    // La etapa que le toca al bebé ahora mismo (ver MotorEtapas.kt), si hay
    // alguna definida para su edad.
    val etapaActual = remember(bebe.id) { MotorEtapas.etapaActual(bebe.edadEnDias()) }

    // Próxima ventana de sueño (ver MotorSueno.kt, traducido de VentanasDespierto.swift):
    // null si ya está durmiendo, si no hay suficientes datos del día, o si el
    // motor no puede cuadrar un plan. Se recalcula en cada recomposición: el
    // propio motor memoiza el resultado por minuto.
    val prediccion = MotorSueno.prediccionDelDia(bebe = bebe, registros = registros, ahora = ahora)

    var mostrarHojaPecho by remember { mutableStateOf(false) }
    var mostrarHojaBiberon by remember { mutableStateOf(false) }
    var mostrarHojaPanal by remember { mutableStateOf(false) }
    var mostrarHojaEtapa by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NubiGeometria.margen)
    ) {
        // Cabecera: saludo, nombre, edad, y dos accesos rápidos. El de
        // sol/luna alterna el modo día/noche ahí mismo (ya no se elige
        // desde Ajustes); el de controles abre Ajustes, igual que antes.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    saludoSegunHora(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.tintaSuave
                )
                Text(bebe.nombre, style = MaterialTheme.typography.displaySmall)
                Text(
                    textoEdadDetallada(bebe),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.tintaSuave
                )
            }
            Box(modifier = Modifier.clickable { alCambiarTema() }) {
                BadgeIcono(
                    if (oscuro) IconoNubi.SOL else IconoNubi.LUNA,
                    colorFondo = if (oscuro) colores.mantequilla else colores.lila,
                    colorIcono = colores.tinta,
                    tamano = 44.dp,
                    tamanoIcono = 20.dp
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.clickable { alAbrirAjustes() }) {
                BadgeIcono(
                    IconoNubi.CONTROLES,
                    colorFondo = colores.lienzoAlto,
                    colorIcono = colores.tintaSuave,
                    tamano = 44.dp,
                    tamanoIcono = 18.dp
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Card(
            shape = RoundedCornerShape(NubiGeometria.radio),
            colors = CardDefaults.cardColors(containerColor = colores.superficie),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (suenoEnCurso?.tipo == TipoRegistro.SIESTA) "SIESTA"
                        else if (suenoEnCurso?.tipo == TipoRegistro.NOCHE) "SUEÑO NOCTURNO"
                        else "SUEÑO",
                        style = MaterialTheme.typography.labelMedium,
                        color = colores.tintaTenue
                    )
                    if (suenoEnCurso != null) {
                        BadgeIcono(
                            if (suenoEnCurso.tipo == TipoRegistro.SIESTA) IconoNubi.NUBE else IconoNubi.LUNA,
                            colorFondo = if (suenoEnCurso.tipo == TipoRegistro.SIESTA) colores.menta else colores.lila,
                            colorIcono = colores.tinta,
                            tamano = 36.dp,
                            tamanoIcono = 16.dp
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                if (suenoEnCurso != null) {
                    Text(
                        textoDuracion(Duration.between(suenoEnCurso.inicio, ahora).toMinutes()),
                        style = MaterialTheme.typography.displaySmall
                    )
                    Text(
                        "Dormido desde ${formatoHora.format(suenoEnCurso.inicio)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colores.tintaSuave
                    )
                    Spacer(Modifier.height(14.dp))
                    if (suenoEnCurso.tipo == TipoRegistro.NOCHE) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            BotonAccion(
                                texto = "Despertar\nnocturno",
                                icono = IconoNubi.LUNA,
                                color = colores.coral,
                                modifier = Modifier.weight(1f)
                            ) {
                                Repositorio.guardarRegistro(
                                    Registro(bebeId = bebe.id, tipo = TipoRegistro.DESPERTAR, inicio = Instant.now())
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            BotonAccion(
                                texto = "Buenos días",
                                icono = IconoNubi.SOL,
                                color = colores.melocoton,
                                modifier = Modifier.weight(1f)
                            ) {
                                Repositorio.guardarRegistro(suenoEnCurso.copy(fin = Instant.now()))
                            }
                        }
                    } else {
                        BotonAccion(
                            texto = "Se ha despertado",
                            icono = IconoNubi.SOL,
                            color = colores.melocoton,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Repositorio.guardarRegistro(suenoEnCurso.copy(fin = Instant.now()))
                        }
                    }
                } else {
                    Text(
                        "Sin sueño en curso.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colores.tintaTenue
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        BotonAccion(
                            texto = "Siesta",
                            icono = IconoNubi.NUBE,
                            color = colores.menta,
                            modifier = Modifier.weight(1f)
                        ) {
                            Repositorio.guardarRegistro(
                                Registro(bebeId = bebe.id, tipo = TipoRegistro.SIESTA, inicio = Instant.now())
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        BotonAccion(
                            texto = "Sueño nocturno",
                            icono = IconoNubi.LUNA,
                            color = colores.lila,
                            modifier = Modifier.weight(1f)
                        ) {
                            Repositorio.guardarRegistro(
                                Registro(bebeId = bebe.id, tipo = TipoRegistro.NOCHE, inicio = Instant.now())
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        prediccion?.let { p ->
            TarjetaPrediccion(prediccion = p, ahora = ahora)
            Spacer(Modifier.height(10.dp))
        }

        FilaUltimaToma(
            ultimoBiberon = ultimoBiberon,
            ultimoPecho = ultimoPecho,
            onBiberon = { mostrarHojaBiberon = true },
            onPecho = { mostrarHojaPecho = true }
        )
        Spacer(Modifier.height(10.dp))

        ultimoPanal?.let { registro ->
            FilaResumenHoy(
                titulo = "Pañal · ${hace(registro.inicio)}",
                subtitulo = "Último a las ${formatoHora.format(registro.inicio)}",
                icono = IconoNubi.GOTA,
                colorBadge = colores.cielo,
                onClick = { mostrarHojaPanal = true }
            )
            Spacer(Modifier.height(10.dp))
        }

        // Etapa actual (ver MotorEtapas.kt): solo se muestra si hay una
        // definida para la edad del bebé, igual que en iOS.
        etapaActual?.let { etapa ->
            Card(
                shape = RoundedCornerShape(NubiGeometria.radioChico),
                colors = CardDefaults.cardColors(containerColor = colores.superficie),
                modifier = Modifier.fillMaxWidth().clickable { mostrarHojaEtapa = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BadgeIcono(
                        iconoDeEtapa(etapa.id),
                        colorFondo = colorDeEtapa(etapa.color, colores),
                        colorIcono = colores.tinta,
                        tamano = 40.dp,
                        tamanoIcono = 18.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(etapa.titulo, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Toca para ver consejos",
                            style = MaterialTheme.typography.bodySmall,
                            color = colores.tintaTenue
                        )
                    }
                    Icono(IconoNubi.CHEVRON, tint = colores.tintaTenue, tamano = 16.dp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (mostrarHojaPecho) {
        HojaPecho(bebeId = bebe.id) { mostrarHojaPecho = false }
    }
    if (mostrarHojaBiberon) {
        HojaBiberon(bebeId = bebe.id) { mostrarHojaBiberon = false }
    }
    if (mostrarHojaPanal) {
        HojaPanal(bebeId = bebe.id) { mostrarHojaPanal = false }
    }
    if (mostrarHojaEtapa) {
        etapaActual?.let { etapa ->
            HojaEtapa(etapa = etapa) { mostrarHojaEtapa = false }
        }
    }
}

@Composable
private fun FilaResumenHoy(
    titulo: String,
    subtitulo: String,
    icono: IconoNubi,
    colorBadge: Color,
    onClick: () -> Unit = {}
) {
    val colores = NubiTheme.colores
    Card(
        shape = RoundedCornerShape(NubiGeometria.radioChico),
        colors = CardDefaults.cardColors(containerColor = colores.superficie),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.bodyLarge)
                Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = colores.tintaTenue)
            }
            BadgeIcono(icono, colorFondo = colorBadge, colorIcono = colores.tinta)
        }
    }
}

/** Tarjeta deliberadamente escueta (igual que tarjetaPrediccion en iOS): qué
 *  ventana de sueño viene y si ya se ha pasado. El motor calcula además la
 *  vigilia prevista, el despertar de referencia y un diagnóstico del día,
 *  pero no se muestran aquí — llenarla de datos la hacía pesada de leer de
 *  un vistazo. */
@Composable
private fun TarjetaPrediccion(prediccion: PrediccionDia, ahora: Instant) {
    val colores = NubiTheme.colores
    val yaEnVentana = prediccion.yaEnVentana(ahora)
    val pasada = prediccion.pasada(ahora)
    val colorCategoria = if (prediccion.tipo == TipoPrediccion.NOCHE) colores.lila else colores.indigo

    Card(
        shape = RoundedCornerShape(NubiGeometria.radioChico),
        colors = CardDefaults.cardColors(containerColor = colores.superficie),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BadgeIcono(
                if (prediccion.tipo == TipoPrediccion.NOCHE) IconoNubi.LUNA else IconoNubi.RELOJ,
                colorFondo = colorCategoria,
                colorIcono = colores.tinta
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    textoPrediccion(prediccion, yaEnVentana, pasada),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    subtituloPrediccion(prediccion, ahora, yaEnVentana, pasada),
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.tintaTenue
                )
            }
        }
    }
}

private fun textoPrediccion(p: PrediccionDia, yaEnVentana: Boolean, pasada: Boolean): String = when (p.tipo) {
    TipoPrediccion.SIESTA -> when {
        yaEnVentana -> "Buena ventana ahora · hasta ${formatoHora.format(p.hasta)}"
        pasada -> "Se ha pasado la ventana ideal"
        else -> "Próxima siesta · ${formatoHora.format(p.desde)} – ${formatoHora.format(p.hasta)}"
    }
    TipoPrediccion.NOCHE ->
        if (yaEnVentana || pasada) "Es hora de dormir 🌙"
        else "Sueño nocturno · ${formatoHora.format(p.desde)}"
}

private fun subtituloPrediccion(p: PrediccionDia, ahora: Instant, yaEnVentana: Boolean, pasada: Boolean): String =
    when (p.tipo) {
        TipoPrediccion.SIESTA ->
            if (p.ajustadaPorHistorial) "Ajustado a su ritmo real" else "Basado en su edad"
        TipoPrediccion.NOCHE -> {
            if (yaEnVentana || pasada) {
                "Última ventana del día"
            } else {
                val restanteMin = Duration.between(ahora, p.desde).toMinutes()
                if (restanteMin > 0) "En ${textoDuracion(restanteMin)} · última siesta hecha"
                else "Última ventana del día"
            }
        }
    }

/** Un solo bloque de "Última toma" — como tarjetaAlimentacion en iOS: no
 *  hay una fila por tipo, sino una única línea con la toma más reciente
 *  de cualquiera de los dos (biberón o pecho), y a la derecha los DOS
 *  iconos de acceso rápido siempre visibles, para poder registrar uno u
 *  otro en cualquier momento sin que importe cuál se usó la última vez. */
@Composable
private fun FilaUltimaToma(
    ultimoBiberon: Registro?,
    ultimoPecho: Registro?,
    onBiberon: () -> Unit,
    onPecho: () -> Unit
) {
    val colores = NubiTheme.colores
    val ultimo = listOfNotNull(ultimoBiberon, ultimoPecho).maxByOrNull { it.inicio }

    Card(
        shape = RoundedCornerShape(NubiGeometria.radioChico),
        colors = CardDefaults.cardColors(containerColor = colores.superficie),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    ultimo?.let { "Última toma · ${hace(it.inicio)}" } ?: "Alimentación",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    ultimo?.let { subtituloRegistro(it) } ?: "Sin registrar todavía",
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.tintaTenue
                )
            }
            Spacer(Modifier.width(12.dp))
            BadgeIcono(
                IconoNubi.BIBERON,
                colorFondo = colores.mantequilla,
                colorIcono = colores.tinta,
                modifier = Modifier.clickable(onClick = onBiberon)
            )
            Spacer(Modifier.width(8.dp))
            BadgeIcono(
                IconoNubi.CORAZON,
                colorFondo = colores.melocoton,
                colorIcono = colores.tinta,
                modifier = Modifier.clickable(onClick = onPecho)
            )
        }
    }
}

@Composable
private fun BotonAccion(
    texto: String,
    icono: IconoNubi,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = NubiTheme.colores.tinta
        ),
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Icono(icono, tint = NubiTheme.colores.tinta, tamano = 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            texto,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
        )
    }
}

private fun saludoSegunHora(): String {
    val hora = LocalTime.now().hour
    return when {
        hora < 6 -> "Buenas noches"
        hora < 12 -> "Buenos días"
        hora < 20 -> "Buenas tardes"
        else -> "Buenas noches"
    }
}

private fun hace(instante: Instant): String {
    val minutos = Duration.between(instante, Instant.now()).toMinutes().coerceAtLeast(0)
    return when {
        minutos < 1 -> "ahora mismo"
        minutos < 60 -> "hace $minutos min"
        else -> {
            val horas = minutos / 60
            val resto = minutos % 60
            if (resto == 0L) "hace $horas h" else "hace $horas h $resto min"
        }
    }
}

private fun textoEdadDetallada(bebe: Bebe): String {
    val dias = bebe.edadEnDias()
    return when {
        dias < 14 -> if (dias == 1) "1 día" else "$dias días"
        dias < 61 -> "${dias / 7} semanas"
        else -> {
            val meses = dias / 30
            val resto = dias % 30
            if (resto == 0) "$meses meses" else "$meses meses y $resto días"
        }
    }
}

private fun textoDuracion(minutos: Long): String = when {
    minutos <= 0L -> "—"
    minutos < 60L -> "$minutos min"
    minutos % 60L == 0L -> "${minutos / 60} h"
    else -> "${minutos / 60} h ${minutos % 60} min"
}

// =====================================================================
// Pestaña Día — aquí sí se apunta. Portado sobre la captura real de
// VistaLinea.swift: título "Su día", navegador de días, el reloj, la
// barra de sueño de 24 h, la botonera de 8 iconos y la lista completa del
// día con línea de tiempo y borrado.
//
// Sigue faltando poder tocar un registro para editarlo (de momento el
// toque solo sirve para borrar) y las hojas con selector de hora propio
// (HojaSueño, HojaHora...): tocar un botón de la botonera apunta "ahora
// mismo", no deja elegir otra hora todavía.
// =====================================================================
private val formatoHoraCorta: DateTimeFormatter =
    DateTimeFormatter.ofPattern("H:mm").withZone(ZoneId.systemDefault())

private val zonaLocal = ZoneId.systemDefault()

private fun horaDecimalDe(instante: Instant): Double {
    val t = instante.atZone(zonaLocal)
    return t.hour + t.minute / 60.0
}

private fun textoDuracionCompacta(minutos: Long): String {
    val h = minutos / 60
    val m = minutos % 60
    return "${h}h%02d".format(m)
}

private fun textoFechaDia(dia: LocalDate): String {
    val hoy = LocalDate.now(zonaLocal)
    return when (dia) {
        hoy -> "Hoy"
        hoy.minusDays(1) -> "Ayer"
        else -> {
            val nombreDia = dia.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("es", "ES"))
            val nombreMes = dia.month.getDisplayName(TextStyle.SHORT, Locale("es", "ES"))
            "$nombreDia ${dia.dayOfMonth} $nombreMes".lowercase()
        }
    }
}

private fun textoEdadCorta(bebe: Bebe, hasta: Instant): String {
    val dias = bebe.edadEnDias(hasta)
    return when {
        dias < 14 -> "$dias d de vida"
        dias < 61 -> "${dias / 7} sem de vida"
        else -> "${(dias / 30.44).toInt()} m de vida"
    }
}

@Composable
fun PantallaDia(bebe: Bebe, registros: List<Registro>) {
    val colores = NubiTheme.colores
    var diaSeleccionado by remember { mutableStateOf(LocalDate.now(zonaLocal)) }
    val hoy = LocalDate.now(zonaLocal)

    // Todo lo que pertenece a este día: los sueños se recortan por si
    // cruzan la medianoche (tramoEnDia), el resto se mira por su inicio.
    val suenosDelDia = registros
        .filter { it.tipo.esSueno && it.fin != null }
        .mapNotNull { r -> tramoEnDia(r, diaSeleccionado, zonaLocal)?.let { r to it } }
    val eventosDelDia = registros.filter {
        !it.tipo.esSueno && it.inicio.atZone(zonaLocal).toLocalDate() == diaSeleccionado
    }
    // Un despertar nocturno resta del sueño (TipoRegistro.restaSueno): está
    // marcado dentro del tramo de una noche/siesta ya contada arriba, así
    // que su duración hay que descontarla para no contar como dormido el
    // rato que estuvo despierto.
    val minutosDespierto = registros
        .filter { it.tipo.restaSueno && it.fin != null }
        .sumOf { r -> tramoEnDia(r, diaSeleccionado, zonaLocal)?.let { (desde, hasta) -> ((hasta - desde) * 60).toLong() } ?: 0L }
    val minutosSueno = (suenosDelDia.sumOf { (_, tramo) -> ((tramo.second - tramo.first) * 60).toLong() } - minutosDespierto)
        .coerceAtLeast(0L)
    val tomas = eventosDelDia.count { it.tipo.esAlimentacion }

    // Si lo acumulado hoy ya cae dentro de lo que le toca dormir a su edad
    // (misma tabla de MotorSueno que usa la predicción de Hoy), la tarjeta
    // de sueño se pone en verde.
    val guiaEdad = MotorSueno.guiaAjustada(
        bebe.edadEnMeses(diaSeleccionado.atTime(12, 0).atZone(zonaLocal).toInstant())
    )
    val suenoSaludable = (minutosSueno / 60.0) in guiaEdad.suenoTotalHoras

    // La lista completa, en orden cronológico: los sueños con el inicio
    // real (aunque sea de ayer, para el chip "noche anterior") y el resto
    // de eventos del día.
    val listaCompleta = (suenosDelDia.map { it.first } + eventosDelDia).sortedBy { it.inicio }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NubiGeometria.margen)
    ) {
        Text("Su día", style = MaterialTheme.typography.displaySmall)
        Text(
            buildString {
                append(if (minutosSueno <= 0L) "Sin sueño aún" else "${textoDuracion(minutosSueno)} de sueño")
                append(" · ")
                append(if (tomas == 1) "1 toma" else "$tomas tomas")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colores.tintaSuave
        )

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BotonFlechaDia(mirar = true) {
                diaSeleccionado = diaSeleccionado.minusDays(1)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(textoFechaDia(diaSeleccionado), style = MaterialTheme.typography.titleMedium)
                Text(
                    textoEdadCorta(bebe, diaSeleccionado.atTime(12, 0).atZone(zonaLocal).toInstant()),
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.tintaTenue
                )
            }
            BotonFlechaDia(mirar = false, habilitado = diaSeleccionado < hoy) {
                if (diaSeleccionado < hoy) diaSeleccionado = diaSeleccionado.plusDays(1)
            }
        }

        Spacer(Modifier.height(16.dp))
        RelojDelDia(bebe = bebe, registros = registros, dia = diaSeleccionado)

        Spacer(Modifier.height(20.dp))
        BarraSuenoDia(suenosDelDia, diaSeleccionado, minutosSueno, suenoSaludable)

        Spacer(Modifier.height(16.dp))
        BotoneraDia(bebe.id, diaSeleccionado)

        Spacer(Modifier.height(20.dp))
        Text(
            "TODO LO DEL DÍA",
            style = MaterialTheme.typography.labelMedium,
            color = colores.tintaTenue
        )
        Text(
            "Toca un registro para editarlo · X para borrarlo",
            style = MaterialTheme.typography.bodySmall,
            color = colores.tintaTenue
        )
        Spacer(Modifier.height(10.dp))

        if (listaCompleta.isEmpty()) {
            Text(
                "Todavía no hay nada apuntado.",
                style = MaterialTheme.typography.bodyMedium,
                color = colores.tintaTenue
            )
        } else {
            listaCompleta.forEachIndexed { indice, registro ->
                FilaTimeline(
                    registro = registro,
                    diaMostrado = diaSeleccionado,
                    esUltimo = indice == listaCompleta.lastIndex,
                    onBorrar = { Repositorio.borrarRegistro(registro.id) }
                )

                // Entre dos sueños seguidos, cuánto tiempo pasó despierto.
                val siguiente = listaCompleta.getOrNull(indice + 1)
                if (registro.tipo.esSueno && registro.fin != null &&
                    siguiente != null && siguiente.tipo.esSueno
                ) {
                    val despierto = Duration.between(registro.fin, siguiente.inicio)
                    if (!despierto.isNegative && !despierto.isZero) {
                        Text(
                            "${textoDuracion(despierto.toMinutes())} despierto",
                            style = MaterialTheme.typography.bodySmall,
                            color = colores.tintaTenue,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 52.dp, top = 2.dp, bottom = 2.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BotonFlechaDia(mirar: Boolean, habilitado: Boolean = true, onClick: () -> Unit) {
    val colores = NubiTheme.colores
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(colores.lienzoAlto)
            .clickable(enabled = habilitado, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icono(
            IconoNubi.CHEVRON,
            tint = if (habilitado) colores.tintaSuave else colores.tintaTenue,
            tamano = 16.dp,
            modifier = if (mirar) Modifier.graphicsLayer(scaleX = -1f) else Modifier
        )
    }
}

/**
 * La barra horizontal de 0h a 24h con los tramos de sueño de este día, y
 * el total en una insignia encima. El marco de la tarjeta y el borde de
 * la insignia se ponen en menta cuando lo dormido hoy ya cae dentro de
 * lo que le toca a su edad (mismo cálculo que la predicción de Hoy);
 * si no, se quedan en coral.
 */
@Composable
private fun BarraSuenoDia(
    suenos: List<Pair<Registro, Pair<Double, Double>>>,
    dia: LocalDate,
    minutosTotales: Long,
    saludable: Boolean
) {
    val colores = NubiTheme.colores
    val esHoy = dia == LocalDate.now(zonaLocal)
    val fraccionAhora = if (esHoy) (horaDecimalDe(Instant.now()) / 24.0) else 0.5
    val bias = (fraccionAhora * 2 - 1).toFloat().coerceIn(-1f, 1f)
    val colorMarco = if (saludable) colores.menta else colores.coral

    Card(
        shape = RoundedCornerShape(NubiGeometria.radio),
        colors = CardDefaults.cardColors(containerColor = colores.superficie),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, colorMarco, RoundedCornerShape(NubiGeometria.radio))
    ) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SUEÑO DE HOY", style = MaterialTheme.typography.labelMedium, color = colores.tintaTenue)
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Canvas(Modifier.matchParentSize()) {
                    drawRoundRect(
                        color = colores.lienzoAlto,
                        size = size,
                        cornerRadius = CornerRadius(size.height / 2f)
                    )
                    suenos.forEach { (registro, tramo) ->
                        val (desde, hasta) = tramo
                        val x0 = (desde / 24.0 * size.width).toFloat()
                        val ancho = ((hasta - desde) / 24.0 * size.width).toFloat().coerceAtLeast(4f)
                        drawRoundRect(
                            color = if (registro.tipo == TipoRegistro.NOCHE) colores.lila else colores.menta,
                            topLeft = Offset(x0, 0f),
                            size = Size(ancho, size.height),
                            cornerRadius = CornerRadius(size.height / 2f)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(BiasAlignment(bias, 0f))
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(colores.superficie)
                        .border(2.5.dp, colorMarco, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        textoDuracionCompacta(minutosTotales),
                        style = MaterialTheme.typography.labelLarge,
                        color = colores.tinta
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0h", style = MaterialTheme.typography.labelSmall, color = colores.tintaTenue)
                Text("12h", style = MaterialTheme.typography.labelSmall, color = colores.tintaTenue)
                Text("24h", style = MaterialTheme.typography.labelSmall, color = colores.tintaTenue)
            }
        }
    }
}

/** La botonera: un círculo de color por tipo, igual que en la lista y el
 *  reloj — ocho tipos en vez de los seis de antes (faltaban despertar
 *  nocturno, medicina y baño). Cada uno abre su hoja de registro, salvo
 *  Pecho, que primero pregunta el lado en un menú anclado al propio
 *  botón (ver MenuPecho en HojasRegistro.kt). */
@Composable
private fun BotoneraDia(bebeId: String, dia: LocalDate) {
    val colores = NubiTheme.colores
    val datos by Repositorio.local.datos.collectAsState()
    // Mismo cálculo que Repositorio.puedeUsarApp(): sin perfil aún
    // (recién instalado, sin conexión) no se bloquea nada.
    val bloqueado = datos.perfil?.let { !it.puedeUsarApp() } ?: false

    var hojaAbierta by remember { mutableStateOf<TipoRegistro?>(null) }
    var menuPechoAbierto by remember { mutableStateOf(false) }
    var ladoPechoElegido by remember { mutableStateOf<LadoPecho?>(null) }
    var mostrarBloqueo by remember { mutableStateOf(false) }

    fun alTocar(abrir: () -> Unit) {
        if (bloqueado) mostrarBloqueo = true else abrir()
    }

    Card(
        shape = RoundedCornerShape(NubiGeometria.radio),
        colors = CardDefaults.cardColors(containerColor = colores.superficie),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TipoRegistro.entries.chunked(4).forEach { fila ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    fila.forEach { tipo ->
                        if (tipo == TipoRegistro.PECHO) {
                            Box {
                                BotonCircular(tipo, bloqueado = bloqueado) {
                                    alTocar { menuPechoAbierto = true }
                                }
                                MenuPecho(
                                    expandido = menuPechoAbierto,
                                    onCerrar = { menuPechoAbierto = false },
                                    onElegir = { lado ->
                                        menuPechoAbierto = false
                                        ladoPechoElegido = lado
                                    }
                                )
                            }
                        } else {
                            BotonCircular(tipo, bloqueado = bloqueado) {
                                alTocar { hojaAbierta = tipo }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Text(
                if (bloqueado) "Se acabó tu prueba gratuita" else "Toca para apuntarlo",
                style = MaterialTheme.typography.bodySmall,
                color = colores.tintaTenue
            )
        }
    }

    when (hojaAbierta) {
        TipoRegistro.SIESTA -> HojaSiestaDia(bebeId, dia) { hojaAbierta = null }
        TipoRegistro.NOCHE -> HojaSuenoNocturnoDia(bebeId, dia) { hojaAbierta = null }
        TipoRegistro.BIBERON -> HojaBiberon(bebeId, dia) { hojaAbierta = null }
        TipoRegistro.PANAL -> HojaPanal(bebeId, dia) { hojaAbierta = null }
        TipoRegistro.DESPERTAR -> HojaDespertarNocturno(bebeId, dia) { hojaAbierta = null }
        TipoRegistro.MEDICINA -> HojaMedicinaDia(bebeId, dia) { hojaAbierta = null }
        TipoRegistro.BANO -> HojaBanoDia(bebeId, dia) { hojaAbierta = null }
        TipoRegistro.PECHO, null -> Unit
    }

    ladoPechoElegido?.let { lado ->
        HojaPechoHora(bebeId = bebeId, lado = lado, dia = dia) { ladoPechoElegido = null }
    }

    if (mostrarBloqueo) {
        HojaPremiumBloqueado { mostrarBloqueo = false }
    }
}

@Composable
private fun BotonCircular(tipo: TipoRegistro, bloqueado: Boolean = false, onClick: () -> Unit) {
    val colores = NubiTheme.colores
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(colorDeTipo(tipo, colores))
            .graphicsLayer(alpha = if (bloqueado) 0.4f else 1f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icono(iconoDeTipo(tipo), tint = colores.tinta, tamano = 18.dp)
        if (bloqueado) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(colores.superficie),
                contentAlignment = Alignment.Center
            ) {
                Icono(IconoNubi.CANDADO, tint = colores.tintaSuave, tamano = 10.dp)
            }
        }
    }
}

/** Una fila de la lista "Todo lo del día": insignia + línea que conecta
 *  con la siguiente, título con sus chips, subtítulo, y una X para borrar. */
@Composable
private fun FilaTimeline(
    registro: Registro,
    diaMostrado: LocalDate,
    esUltimo: Boolean,
    onBorrar: () -> Unit
) {
    val colores = NubiTheme.colores
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.width(40.dp).height(if (esUltimo) 40.dp else 64.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            if (!esUltimo) {
                Box(
                    Modifier
                        .padding(top = 40.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(colores.separador)
                )
            }
            BadgeIcono(
                iconoDeTipo(registro.tipo),
                colorFondo = colorDeTipo(registro.tipo, colores),
                colorIcono = colores.tinta,
                tamano = 40.dp,
                tamanoIcono = 18.dp
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(top = 2.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(nombreTipo(registro.tipo), style = MaterialTheme.typography.bodyLarge)
                val esDeAyer = registro.inicio.atZone(zonaLocal).toLocalDate() != diaMostrado
                if (esDeAyer) {
                    Spacer(Modifier.width(8.dp))
                    ChipTexto("noche anterior")
                }
                registro.duracionSegundos?.let { seg ->
                    Spacer(Modifier.width(8.dp))
                    ChipTexto(textoDuracion(seg / 60))
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                subtituloRegistro(registro),
                style = MaterialTheme.typography.bodyMedium,
                color = colores.tintaSuave
            )
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onBorrar),
            contentAlignment = Alignment.Center
        ) {
            Icono(IconoNubi.EQUIS, tint = colores.tintaTenue, tamano = 12.dp)
        }
    }
}

@Composable
private fun ChipTexto(texto: String) {
    val colores = NubiTheme.colores
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colores.lienzoAlto)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(texto, style = MaterialTheme.typography.labelSmall, color = colores.tintaSuave)
    }
}

private fun subtituloRegistro(registro: Registro): String = when (registro.tipo) {
    TipoRegistro.NOCHE, TipoRegistro.SIESTA ->
        if (registro.fin != null) {
            "${formatoHoraCorta.format(registro.inicio)} – ${formatoHoraCorta.format(registro.fin)}"
        } else {
            "Desde las ${formatoHoraCorta.format(registro.inicio)}"
        }

    TipoRegistro.BIBERON -> {
        val partes = mutableListOf(formatoHoraCorta.format(registro.inicio))
        registro.cantidadBiberon?.let { cantidad ->
            val unidad = registro.unidadBiberon?.etiqueta ?: "ml"
            partes.add("${cantidad.toInt()} $unidad")
        }
        registro.tipoLeche?.let { partes.add(if (it.name == "MATERNA") "Materna" else "Fórmula") }
        partes.joinToString(" · ")
    }

    TipoRegistro.PECHO -> {
        val partes = mutableListOf(formatoHoraCorta.format(registro.inicio))
        registro.lado?.let { partes.add(if (it.name == "IZQUIERDA") "Izquierdo" else "Derecho") }
        partes.joinToString(" · ")
    }

    TipoRegistro.PANAL -> {
        val detalle = when {
            registro.panalPis == true && registro.panalCaca == true -> "Pis y caca"
            registro.panalCaca == true -> "Caca"
            registro.panalPis == true -> "Pis"
            else -> null
        }
        listOfNotNull(formatoHoraCorta.format(registro.inicio), detalle).joinToString(" · ")
    }

    TipoRegistro.MEDICINA ->
        listOfNotNull(formatoHoraCorta.format(registro.inicio), registro.nota.takeIf { it.isNotBlank() })
            .joinToString(" · ")

    else -> formatoHoraCorta.format(registro.inicio)
}

private fun nombreTipo(tipo: TipoRegistro): String = when (tipo) {
    TipoRegistro.SIESTA -> "Siesta"
    TipoRegistro.NOCHE -> "Sueño nocturno"
    TipoRegistro.BIBERON -> "Biberón"
    TipoRegistro.PECHO -> "Pecho"
    TipoRegistro.PANAL -> "Pañal"
    TipoRegistro.DESPERTAR -> "Despertar"
    TipoRegistro.MEDICINA -> "Medicina"
    TipoRegistro.BANO -> "Baño"
}

private fun textoEdad(bebe: Bebe): String {
    val dias = bebe.edadEnDias()
    return when {
        dias < 14 -> if (dias == 1) "1 día" else "$dias días"
        dias < 61 -> "${dias / 7} semanas"
        else -> "${(dias / 30.44).toInt()} meses"
    }
}

// =====================================================================
// Pestañas todavía por portar
// =====================================================================
@Composable
fun PantallaEnObras(nombre: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(nombre, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Pendiente de portar desde iOS.",
                style = MaterialTheme.typography.bodyMedium,
                color = NubiTheme.colores.tintaTenue
            )
        }
    }
}

// =====================================================================
// Ajustes
// =====================================================================
@Composable
fun PantallaAjustes(bebe: Bebe?, alCerrarSesion: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NubiGeometria.margen)
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium)

        bebe?.let {
            Spacer(Modifier.height(20.dp))
            Text("Bebé", style = MaterialTheme.typography.titleMedium)
            Text(
                "${it.nombre} · ${textoEdad(it)}",
                style = MaterialTheme.typography.bodyMedium,
                color = NubiTheme.colores.tintaSuave
            )
        }

        // El modo día/noche ya no se elige aquí: se alterna directamente
        // con el botón de sol/luna de la cabecera de Hoy.

        Spacer(Modifier.height(28.dp))
        Text("Canjear código de regalo", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        BloqueCanjearCodigo()

        Spacer(Modifier.height(32.dp))
        OutlinedButton(
            onClick = alCerrarSesion,
            shape = RoundedCornerShape(NubiGeometria.radioChico),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cerrar sesión")
        }
    }
}
