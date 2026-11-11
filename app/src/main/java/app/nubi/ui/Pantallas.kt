package app.nubi.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nubi.R
import app.nubi.data.Bebe
import app.nubi.data.FotosNubi
import app.nubi.data.GuiaEdadAjustada
import app.nubi.data.LadoPecho
import app.nubi.data.MotorEtapas
import app.nubi.data.MotorSueno
import app.nubi.data.Perfil
import app.nubi.data.Premium
import app.nubi.data.PrediccionDia
import app.nubi.data.Registro
import app.nubi.data.Repositorio
import app.nubi.data.Supabase
import app.nubi.data.TipoPrediccion
import app.nubi.data.TipoRegistro
import app.nubi.data.UnidadBiberon
import app.nubi.i18n.IdiomaNubi
import app.nubi.i18n.L10n
import app.nubi.notificaciones.NotificadorPrueba
import app.nubi.ui.theme.NubiGeometria
import app.nubi.ui.theme.NubiTheme
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

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
            label = { Text(L10n.t("Código de regalo")) },
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
                        L10n.t("¡Listo! Premium activado.")
                    } catch (e: Exception) {
                        // El servidor lanza 'codigo_invalido' o 'codigo_ya_usado';
                        // se traducen para que el mensaje signifique algo.
                        when {
                            e.message?.contains("codigo_ya_usado") == true ->
                                L10n.t("Ese código ya se había usado.")
                            e.message?.contains("codigo_invalido") == true ->
                                L10n.t("No encontramos ese código. Revisa que esté bien escrito.")
                            else -> L10n.t("No se pudo canjear ahora mismo. Inténtalo en un momento.")
                        }
                    }
                    enviando = false
                }
            },
            enabled = codigo.isNotBlank() && !enviando,
            shape = RoundedCornerShape(NubiGeometria.radioChico),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (enviando) L10n.t("Canjeando…") else L10n.t("Canjear"))
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
            L10n.t("Si te han regalado un código, puedes canjearlo ahora."),
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
            Text(L10n.t("Saltar"))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            L10n.t("Tienes 7 días de prueba. Siempre puedes canjear un código más tarde desde Ajustes."),
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

/**
 * Círculo de foto del bebé, tocable para elegir una de la galería, con un
 * lapicero pequeño de "editar" en la esquina — igual que iOS (NubiApp.swift
 * y VistaAjustes.swift: PhotosPicker + badge de lápiz). Se usa en el alta
 * (PantallaNuevoBebe), en Ajustes y en la cabecera de la pestaña Hoy.
 *
 * Solo pide la foto a la galería y la deja ya comprimida en base64
 * (FotosNubi.comprimirParaPerfil): quien llama no toca Bitmaps ni Uris.
 */
@Composable
private fun SelectorFotoBebe(
    base64: String?,
    inicialNombre: String,
    onFotoSeleccionada: (String) -> Unit,
    onQuitarFoto: () -> Unit,
    tamano: androidx.compose.ui.unit.Dp = 72.dp
) {
    val colores = NubiTheme.colores
    val contexto = LocalContext.current

    val lanzadorGaleria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            FotosNubi.comprimirParaPerfil(contexto, uri)?.let(onFotoSeleccionada)
        }
    }

    val bitmap = remember(base64) { base64?.let(FotosNubi::decodificarPerfil) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(tamano)
                    .clip(CircleShape)
                    .clickable {
                        lanzadorGaleria.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(colores.lila.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            inicialNombre.trim().take(1).uppercase().ifEmpty { "?" },
                            style = MaterialTheme.typography.headlineSmall,
                            color = colores.indigo
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(colores.indigo)
                    .border(2.dp, colores.lienzoAlto, CircleShape)
                    .clickable {
                        lanzadorGaleria.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icono(IconoNubi.LAPIZ, tint = Color.White, tamano = 13.dp)
            }
        }
        if (base64 != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                L10n.t("Quitar foto"),
                style = MaterialTheme.typography.labelMedium,
                color = colores.coral,
                modifier = Modifier.clickable(onClick = onQuitarFoto)
            )
        }
    }
}

/**
 * Foto del bebé junto a su nombre en la cabecera de Hoy (ver fotoBebeCabecera
 * en VistaInicio.swift de iOS). Sin foto guardada, muestra su inicial en vez
 * de dejar un hueco vacío. Tocarla la cambia al momento (abre la galería y
 * guarda directamente), sin pasar por Ajustes.
 */
@Composable
private fun FotoBebeCabecera(base64: String?, nombre: String, onFotoSeleccionada: (String) -> Unit) {
    val colores = NubiTheme.colores
    val contexto = LocalContext.current
    val lanzadorGaleria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            FotosNubi.comprimirParaPerfil(contexto, uri)?.let(onFotoSeleccionada)
        }
    }
    val bitmap = remember(base64) { base64?.let(FotosNubi::decodificarPerfil) }

    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .clickable {
                lanzadorGaleria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            .border(1.dp, colores.tinta.copy(alpha = 0.08f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(colores.lila.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    nombre.trim().take(1).uppercase().ifEmpty { "?" },
                    style = MaterialTheme.typography.titleMedium,
                    color = colores.indigo
                )
            }
        }
    }
}

@Composable
fun PantallaNuevoBebe(alGuardar: () -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var dia by remember { mutableStateOf("") }
    var mes by remember { mutableStateOf("") }
    var anio by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var fotoBase64 by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NubiGeometria.margen)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text(L10n.t("¿Cómo se llama?"), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        // Foto opcional, igual que en iOS (NubiApp.swift): mismo selector
        // que luego se repite en Ajustes.
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SelectorFotoBebe(
                base64 = fotoBase64,
                inicialNombre = nombre,
                onFotoSeleccionada = { fotoBase64 = it },
                onQuitarFoto = { fotoBase64 = null }
            )
        }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it },
            label = { Text(L10n.t("Nombre del bebé")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        Text(L10n.t("Fecha de nacimiento"), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = dia, onValueChange = { dia = it.filter(Char::isDigit).take(2) },
                label = { Text(L10n.t("Día")) }, singleLine = true, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = mes, onValueChange = { mes = it.filter(Char::isDigit).take(2) },
                label = { Text(L10n.t("Mes")) }, singleLine = true, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = anio, onValueChange = { anio = it.filter(Char::isDigit).take(4) },
                label = { Text(L10n.t("Año")) }, singleLine = true, modifier = Modifier.weight(1.4f)
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

                val miId = Supabase.client.auth.currentUserOrNull()?.id

                when {
                    nombre.isBlank() -> error = L10n.t("Pon el nombre del bebé.")
                    fecha == null -> error = L10n.t("Esa fecha no existe. Revísala.")
                    fecha.isAfter(LocalDate.now()) -> error = L10n.t("La fecha no puede ser futura.")
                    // No debería pasar nunca (esta pantalla solo se ve con
                    // sesión iniciada), pero mejor avisar que guardar un bebé
                    // que luego nunca podría subirse a Supabase.
                    miId == null -> error = L10n.t("No hay sesión iniciada. Vuelve a entrar e inténtalo de nuevo.")
                    else -> {
                        val nuevoBebe = Bebe(
                            usuarioId = miId,
                            nombre = nombre.trim(),
                            fechaNacimiento = fecha.atTime(LocalTime.NOON)
                                .atZone(ZoneId.systemDefault()).toInstant()
                        )
                        Repositorio.guardarBebe(nuevoBebe)
                        fotoBase64?.let { Repositorio.guardarFotoBebe(it, idBebe = nuevoBebe.id) }
                        alGuardar()
                    }
                }
            },
            shape = RoundedCornerShape(NubiGeometria.radioChico),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(L10n.t("Empezar"))
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
    premium: Boolean,
    oscuro: Boolean = false,
    fotoBase64: String? = null,
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
    // El idioma entra en la clave del remember a propósito: los textos de la
    // etapa se resuelven al construirla (MotorEtapas.t), así que sin esto se
    // quedaban en el idioma que hubiera al abrir la app.
    val etapaActual = remember(bebe.id, L10n.idiomaEfectivo) {
        MotorEtapas.etapaActual(bebe.edadEnDias())
    }

    // Próxima ventana de sueño (ver MotorSueno.kt, traducido de VentanasDespierto.swift):
    // null si ya está durmiendo, si no hay suficientes datos del día, o si el
    // motor no puede cuadrar un plan. Se recalcula en cada recomposición: el
    // propio motor memoiza el resultado por minuto.
    val prediccion = MotorSueno.prediccionDelDia(bebe = bebe, registros = registros, ahora = ahora)

    var mostrarHojaPecho by remember { mutableStateOf(false) }
    var mostrarHojaBiberon by remember { mutableStateOf(false) }
    var mostrarHojaPanal by remember { mutableStateOf(false) }
    var mostrarHojaEtapa by remember { mutableStateOf(false) }
    var mostrarHojaAvisoComida by remember { mutableStateOf(false) }
    // Se abre al tocar algo bloqueado de esta pantalla (predicción, toma,
    // pañal). El registro de sueño/siesta de arriba NUNCA pasa por aquí:
    // es justo lo que se deja gratis para que el motor (MotorSueno.kt)
    // tenga datos que aprender durante los 7 días de prueba.
    var mostrarBloqueo by remember { mutableStateOf(false) }

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
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Foto a la izquierda del nombre/edad. Tocarla la
                    // cambia, sin tener que entrar en Ajustes — igual que iOS.
                    FotoBebeCabecera(
                        base64 = fotoBase64,
                        nombre = bebe.nombre,
                        onFotoSeleccionada = { Repositorio.guardarFotoBebe(it) }
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(bebe.nombre, style = MaterialTheme.typography.displaySmall)
                        Text(
                            textoEdadDetallada(bebe),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colores.tintaSuave
                        )
                    }
                }
            }
            Box(modifier = Modifier.clickable { alCambiarTema() }) {
                // Igual que iOS (modoOscuro en VistaInicio.swift): el icono
                // va coloreado (mantequilla/indigo) sobre un fondo translúcido
                // muy sutil, no sobre un círculo sólido — si no, el sol se ve
                // como un contorno oscuro con fondo amarillo en vez de un sol
                // amarillo de verdad.
                BadgeIcono(
                    if (oscuro) IconoNubi.SOL else IconoNubi.LUNA,
                    colorFondo = if (oscuro) colores.tinta.copy(alpha = 0.15f) else colores.lila.copy(alpha = 0.25f),
                    colorIcono = if (oscuro) colores.mantequilla else colores.indigo,
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
                        if (suenoEnCurso?.tipo == TipoRegistro.SIESTA) L10n.t("Siesta").uppercase()
                        else if (suenoEnCurso?.tipo == TipoRegistro.NOCHE) L10n.t("Sueño nocturno").uppercase()
                        else L10n.t("Sueño").uppercase(),
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
                        "${L10n.t("Dormido desde")} ${formatoHora.format(suenoEnCurso.inicio)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colores.tintaSuave
                    )
                    Spacer(Modifier.height(14.dp))
                    if (suenoEnCurso.tipo == TipoRegistro.NOCHE) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            BotonAccion(
                                // "Despertar nocturno" (y sus traducciones) son siempre dos
                                // palabras separadas por un único espacio — se parte ahí para
                                // que quepa en dos líneas en el botón, igual que antes.
                                texto = L10n.t("Despertar nocturno").replaceFirst(" ", "\n"),
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
                                texto = L10n.t("Buenos días"),
                                icono = IconoNubi.SOL,
                                color = colores.melocoton,
                                modifier = Modifier.weight(1f)
                            ) {
                                Repositorio.guardarRegistro(suenoEnCurso.copy(fin = Instant.now()))
                            }
                        }
                    } else {
                        BotonAccion(
                            texto = L10n.t("Se ha despertado"),
                            icono = IconoNubi.SOL,
                            color = colores.melocoton,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Repositorio.guardarRegistro(suenoEnCurso.copy(fin = Instant.now()))
                        }
                    }
                } else {
                    Text(
                        L10n.t("Sin sueño en curso."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colores.tintaTenue
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        BotonAccion(
                            texto = L10n.t("Siesta"),
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
                            texto = L10n.t("Sueño nocturno"),
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
            if (premium) {
                TarjetaPrediccion(prediccion = p, ahora = ahora)
            } else {
                TarjetaPrediccionBloqueada { mostrarBloqueo = true }
            }
            Spacer(Modifier.height(10.dp))
        }

        FilaUltimaToma(
            ultimoBiberon = ultimoBiberon,
            ultimoPecho = ultimoPecho,
            bloqueado = !premium,
            oscuro = oscuro,
            onReloj = { if (premium) mostrarHojaAvisoComida = true else mostrarBloqueo = true },
            onBiberon = { if (premium) mostrarHojaBiberon = true else mostrarBloqueo = true },
            onPecho = { if (premium) mostrarHojaPecho = true else mostrarBloqueo = true }
        )
        Spacer(Modifier.height(10.dp))

        // A diferencia de las demás tarjetas de esta pantalla, esta siempre
        // está visible: el botón de "Hoy" es para apuntar un pañal en el
        // momento (el de "Día" es para uno de hace rato), así que tiene que
        // poder tocarse aunque todavía no haya ningún pañal registrado hoy.
        FilaResumenHoy(
            titulo = ultimoPanal?.let { "${L10n.t("Pañal")} · ${hace(it.inicio)}" } ?: L10n.t("Pañal"),
            subtitulo = ultimoPanal?.let { "${L10n.t("Último a las")} ${formatoHora.format(it.inicio)}" }
                ?: L10n.t("Sin registrar todavía"),
            icono = IconoNubi.PANAL,
            colorBadge = colores.cielo,
            bloqueado = !premium,
            onClick = { if (premium) mostrarHojaPanal = true else mostrarBloqueo = true }
        )
        Spacer(Modifier.height(10.dp))

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
                            L10n.t("Toca para ver consejos"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colores.tintaTenue
                        )
                    }
                    Icono(IconoNubi.CHEVRON, tint = colores.tintaTenue, tamano = 16.dp)
                }
            }
        }

        // Regalar Nubi: mismo sitio que en iOS (VistaInicio.swift), al final
        // del scroll de Hoy, siempre visible.
        Spacer(Modifier.height(14.dp))
        TarjetaDinamicaRegalarInstagram()

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
    if (mostrarHojaAvisoComida) {
        HojaAvisoComida { mostrarHojaAvisoComida = false }
    }
    if (mostrarBloqueo) {
        HojaPremiumBloqueado { mostrarBloqueo = false }
    }
}

/**
 * Se abre desde el reloj a la izquierda del biberón en la pestaña Hoy (ver
 * HojaAvisoComida en VistaInicio.swift de iOS). Deja decidir cada cuánto
 * tiempo, contado desde la última toma (biberón o pecho), se quiere el
 * aviso "Ya es la hora de comer de {bebé}" — en horas y minutos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaAvisoComida(onCerrar: () -> Unit) {
    val colores = NubiTheme.colores
    val datos by Repositorio.local.datos.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var activo by remember { mutableStateOf(datos.avisoComidaActivo) }
    // Igual que iOS: si el intervalo guardado fuera 0 (no debería pasar,
    // pero por si acaso), se parte de 3h en vez de un contador a cero.
    var horas by remember {
        mutableIntStateOf(datos.avisoComidaIntervaloMin / 60)
    }
    var minutos by remember { mutableIntStateOf(datos.avisoComidaIntervaloMin % 60) }
    LaunchedEffect(Unit) {
        if (horas == 0 && minutos == 0) horas = 3
    }

    fun guardar() {
        val total = (horas * 60 + minutos).coerceAtLeast(15)
        Repositorio.guardarAvisoComida(activo = activo, intervaloMin = total)
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
                Text(L10n.t("Aviso de comida"), style = MaterialTheme.typography.titleLarge, color = colores.tinta)
            }
            Spacer(Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(NubiGeometria.radio),
                colors = CardDefaults.cardColors(containerColor = colores.superficie),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    BadgeIcono(IconoNubi.RELOJ, colorFondo = colores.indigo, colorIcono = Color.White, tamano = 54.dp, tamanoIcono = 24.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(L10n.t("Aviso de comida"), style = MaterialTheme.typography.titleMedium, color = colores.tinta)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        L10n.t("Un aviso cuando pase el tiempo que marques desde la última toma"),
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.tintaSuave,
                        textAlign = TextAlign.Center
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            L10n.t("Avisar cuando toque comer"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colores.tinta
                        )
                        Switch(checked = activo, onCheckedChange = { activo = it })
                    }

                    if (activo) {
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = colores.separador)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            L10n.t("Cada").uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = colores.tintaTenue
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ContadorAvisoComida(
                                valor = horas, unidad = "h", paso = 1, minimo = 0, maximo = 12,
                                onCambiar = { horas = it }
                            )
                            ContadorAvisoComida(
                                valor = minutos, unidad = L10n.t("min"), paso = 5, minimo = 0, maximo = 55,
                                onCambiar = { minutos = it }
                            )
                        }
                    }
                }
            }

            if (activo) {
                Spacer(Modifier.height(14.dp))
                Card(
                    shape = RoundedCornerShape(NubiGeometria.radioChico),
                    colors = CardDefaults.cardColors(containerColor = colores.superficie),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(L10n.t("Resumen:"), style = MaterialTheme.typography.bodyMedium, color = colores.tintaSuave)
                        Text(
                            "${horas} h ${minutos} min",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colores.tinta
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { guardar() },
                shape = RoundedCornerShape(NubiGeometria.radioChico),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(L10n.t("Guardar"))
            }
        }
    }
}

@Composable
private fun RowScope.ContadorAvisoComida(valor: Int, unidad: String, paso: Int, minimo: Int, maximo: Int, onCambiar: (Int) -> Unit) {
    val colores = NubiTheme.colores
    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor.toString(), style = MaterialTheme.typography.displaySmall, color = colores.tinta)
        Text(unidad, style = MaterialTheme.typography.bodySmall, color = colores.tintaSuave)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colores.lila)
                    .clickable { onCambiar((valor - paso).coerceIn(minimo, maximo)) },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.width(14.dp).height(2.dp).clip(RoundedCornerShape(50)).background(colores.tinta))
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colores.lila)
                    .clickable { onCambiar((valor + paso).coerceIn(minimo, maximo)) },
                contentAlignment = Alignment.Center
            ) {
                Icono(IconoNubi.MAS, tint = colores.tinta, tamano = 16.dp)
            }
        }
    }
}

@Composable
private fun FilaResumenHoy(
    titulo: String,
    subtitulo: String,
    icono: IconoNubi,
    colorBadge: Color,
    bloqueado: Boolean = false,
    onClick: () -> Unit = {}
) {
    val colores = NubiTheme.colores
    Card(
        shape = RoundedCornerShape(NubiGeometria.radioChico),
        colors = CardDefaults.cardColors(containerColor = colores.superficie),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = if (bloqueado) 0.5f else 1f)
            .clickable(onClick = onClick)
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
            if (bloqueado) {
                BadgeIcono(IconoNubi.CANDADO, colorFondo = colores.lienzoAlto, colorIcono = colores.tintaSuave)
            } else {
                BadgeIcono(icono, colorFondo = colorBadge, colorIcono = colores.tinta)
            }
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

/**
 * Misma forma que [TarjetaPrediccion] pero sin revelar la hora: solo dice
 * que existe y que hace falta Premium, para no regalar la predicción en
 * texto plano con un candado encima (eso seguiría siendo "ver la app
 * entera", solo que con un dibujo tapando el número).
 */
@Composable
private fun TarjetaPrediccionBloqueada(onClick: () -> Unit) {
    val colores = NubiTheme.colores
    Card(
        shape = RoundedCornerShape(NubiGeometria.radioChico),
        colors = CardDefaults.cardColors(containerColor = colores.superficie),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BadgeIcono(IconoNubi.CANDADO, colorFondo = colores.lienzoAlto, colorIcono = colores.tintaSuave)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(L10n.t("Próxima ventana de sueño"), style = MaterialTheme.typography.bodyLarge)
                Text(
                    L10n.t("Nubi ya sabe cuándo toca — desbloquéalo con Premium"),
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.tintaTenue
                )
            }
            Icono(IconoNubi.CHEVRON, tint = colores.tintaTenue, tamano = 16.dp)
        }
    }
}

private fun textoPrediccion(p: PrediccionDia, yaEnVentana: Boolean, pasada: Boolean): String = when (p.tipo) {
    TipoPrediccion.SIESTA -> when {
        yaEnVentana -> "${L10n.t("Buena ventana ahora")} · ${L10n.t("hasta")} ${formatoHora.format(p.hasta)}"
        pasada -> L10n.t("Se ha pasado la ventana ideal")
        else -> "${L10n.t("Próxima siesta")} · ${formatoHora.format(p.desde)} – ${formatoHora.format(p.hasta)}"
    }
    TipoPrediccion.NOCHE ->
        if (yaEnVentana || pasada) L10n.t("Es hora de dormir 🌙")
        else "${L10n.t("Sueño nocturno")} · ${formatoHora.format(p.desde)}"
}

private fun subtituloPrediccion(p: PrediccionDia, ahora: Instant, yaEnVentana: Boolean, pasada: Boolean): String =
    when (p.tipo) {
        TipoPrediccion.SIESTA ->
            L10n.t(if (p.ajustadaPorHistorial) L10n.t("Ajustado a su ritmo real") else L10n.t("Basado en su edad"))
        TipoPrediccion.NOCHE -> {
            if (yaEnVentana || pasada) {
                L10n.t("Última ventana del día")
            } else {
                val restanteMin = Duration.between(ahora, p.desde).toMinutes()
                if (restanteMin > 0) "${L10n.t("En")} ${textoDuracion(restanteMin)} ${L10n.t("· última siesta hecha")}"
                else L10n.t("Última ventana del día")
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
    bloqueado: Boolean = false,
    oscuro: Boolean = false,
    onReloj: () -> Unit = {},
    onBiberon: () -> Unit,
    onPecho: () -> Unit
) {
    val colores = NubiTheme.colores
    val ultimo = listOfNotNull(ultimoBiberon, ultimoPecho).maxByOrNull { it.inicio }

    Card(
        shape = RoundedCornerShape(NubiGeometria.radioChico),
        colors = CardDefaults.cardColors(containerColor = colores.superficie),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = if (bloqueado) 0.5f else 1f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (bloqueado) L10n.t("Alimentación") else (ultimo?.let { "${L10n.t("Última toma")} · ${hace(it.inicio)}" } ?: L10n.t("Alimentación")),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    if (bloqueado) L10n.t("Parte de Nubi Premium") else (ultimo?.let { subtituloRegistro(it) } ?: L10n.t("Sin registrar todavía")),
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.tintaTenue
                )
            }
            Spacer(Modifier.width(12.dp))
            if (bloqueado) {
                BadgeIcono(
                    IconoNubi.CANDADO,
                    colorFondo = colores.lienzoAlto,
                    colorIcono = colores.tintaSuave,
                    modifier = Modifier.clickable(onClick = onBiberon)
                )
            } else {
                // Círculo del mismo tamaño que biberón/pecho (40dp, el que ya
                // trae BadgeIcono por defecto), con el icono del reloj más
                // grande dentro (22dp), y en blanco/negro invertido según el
                // tema — igual que iOS: en tema día, fondo negro con el
                // reloj en blanco; en tema noche, al revés.
                BadgeIcono(
                    IconoNubi.RELOJ,
                    colorFondo = if (oscuro) Color.White else Color.Black,
                    colorIcono = if (oscuro) Color.Black else Color.White,
                    tamanoIcono = 22.dp,
                    modifier = Modifier.clickable(onClick = onReloj)
                )
                Spacer(Modifier.width(8.dp))
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
    return L10n.t(
        when {
            hora < 6 -> L10n.t("Buenas noches")
            hora < 12 -> L10n.t("Buenos días")
            hora < 20 -> L10n.t("Buenas tardes")
            else -> L10n.t("Buenas noches")
        }
    )
}

private fun hace(instante: Instant): String {
    val minutos = Duration.between(instante, Instant.now()).toMinutes().coerceAtLeast(0)
    return when {
        minutos < 1 -> L10n.ahoraMismo
        minutos < 60 -> L10n.haceMin(minutos.toInt())
        else -> {
            val horas = (minutos / 60).toInt()
            val resto = (minutos % 60).toInt()
            L10n.haceHoras(horas, resto)
        }
    }
}

private fun textoEdadDetallada(bebe: Bebe): String = L10n.textoEdadDetallada(bebe.edadEnDias())

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

private fun textoDuracionCompacta(minutos: Long): String {
    val h = minutos / 60
    val m = minutos % 60
    return "${h}h%02d".format(m)
}

private fun textoFechaDia(dia: LocalDate): String {
    val hoy = LocalDate.now(zonaLocal)
    return when (dia) {
        hoy -> L10n.t("Hoy")
        hoy.minusDays(1) -> L10n.t("Ayer")
        else -> {
            val nombreDia = dia.dayOfWeek.getDisplayName(TextStyle.SHORT, L10n.localeJava)
            val nombreMes = dia.month.getDisplayName(TextStyle.SHORT, L10n.localeJava)
            "$nombreDia ${dia.dayOfMonth} $nombreMes".lowercase()
        }
    }
}

private fun textoEdadCorta(bebe: Bebe, hasta: Instant): String = L10n.textoEdadCorta(bebe.edadEnDias(hasta))

@Composable
fun PantallaDia(bebe: Bebe, registros: List<Registro>, premium: Boolean) {
    val colores = NubiTheme.colores
    var diaSeleccionado by remember { mutableStateOf(LocalDate.now(zonaLocal)) }
    val hoy = LocalDate.now(zonaLocal)
    // Al tocar una fila de "Todo lo del día" se reabre su misma hoja de
    // registro, en modo edición (ver HojaEditarRegistro más abajo).
    var registroEditando by remember { mutableStateOf<Registro?>(null) }
    // Mismo sheet que usa BotoneraDia al tocar un botón bloqueado — se
    // comparte aquí para la flecha de "día anterior" y para editar un
    // registro desde la lista, que sin premium tampoco se puede.
    var mostrarBloqueo by remember { mutableStateOf(false) }

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
        Text(L10n.t("Su día"), style = MaterialTheme.typography.displaySmall)
        ChipsResumenDelDia(eventosDelDia)

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BotonFlechaDia(mirar = true) {
                // Sin premium, el historial de días anteriores es
                // justo lo que se vende: se abre el paywall en vez de
                // cambiar de fecha, en lugar de deshabilitar la flecha
                // sin más (que no explicaría por qué no hace nada).
                if (premium) diaSeleccionado = diaSeleccionado.minusDays(1) else mostrarBloqueo = true
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
        RelojDelDia(
            bebe = bebe,
            registros = registros,
            dia = diaSeleccionado,
            mostrarPrediccion = premium
        )

        Spacer(Modifier.height(20.dp))
        BarraSuenoDia(guiaEdad, minutosSueno, suenoSaludable)

        Spacer(Modifier.height(16.dp))
        BotoneraDia(bebe.id, diaSeleccionado, premium)

        Spacer(Modifier.height(20.dp))
        Text(
            L10n.t("TODO LO DEL DÍA"),
            style = MaterialTheme.typography.labelMedium,
            color = colores.tintaTenue
        )
        Text(
            // Sin premium editar está bloqueado, así que la ayuda no puede
            // seguir diciendo "toca para editarlo". Borrar sí se deja: son
            // sus datos, y es la única forma de arreglar un toque mal dado
            // en Hoy sin tener que pagar para ello.
            if (premium) L10n.t("Toca un registro para editarlo · X para borrarlo")
            else L10n.t("X para borrarlo · editar es parte de Nubi Premium"),
            style = MaterialTheme.typography.bodySmall,
            color = colores.tintaTenue
        )
        Spacer(Modifier.height(10.dp))

        if (listaCompleta.isEmpty()) {
            Text(
                L10n.t("Todavía no hay nada apuntado."),
                style = MaterialTheme.typography.bodyMedium,
                color = colores.tintaTenue
            )
        } else {
            listaCompleta.forEachIndexed { indice, registro ->
                FilaTimeline(
                    registro = registro,
                    diaMostrado = diaSeleccionado,
                    esUltimo = indice == listaCompleta.lastIndex,
                    onEditar = {
                        // Sin premium la lista se ve entera, pero es de solo
                        // lectura: editar cualquier registro abre el paywall.
                        if (premium) registroEditando = registro else mostrarBloqueo = true
                    },
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
                            L10n.despiertoDurante(textoDuracion(despierto.toMinutes())),
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

    // La hoja de edición: la misma hoja de siempre para ese tipo, pero
    // precargada con los datos del registro tocado y en modo edición —
    // al guardar se actualiza el mismo registro, nunca se crea uno nuevo.
    registroEditando?.let { registro ->
        val cerrar = { registroEditando = null }
        when (registro.tipo) {
            TipoRegistro.SIESTA -> HojaSiestaDia(bebe.id, diaSeleccionado, registro = registro, onCerrar = cerrar)
            TipoRegistro.NOCHE -> HojaSuenoNocturnoDia(bebe.id, diaSeleccionado, registro = registro, onCerrar = cerrar)
            TipoRegistro.DESPERTAR -> HojaDespertarNocturno(bebe.id, diaSeleccionado, registro = registro, onCerrar = cerrar)
            TipoRegistro.BANO -> HojaBanoDia(bebe.id, diaSeleccionado, registro = registro, onCerrar = cerrar)
            TipoRegistro.BIBERON -> HojaBiberon(bebe.id, registro = registro, onCerrar = cerrar)
            TipoRegistro.PANAL -> HojaPanal(bebe.id, registro = registro, onCerrar = cerrar)
            TipoRegistro.MEDICINA -> HojaMedicinaDia(bebe.id, diaSeleccionado, registro = registro, onCerrar = cerrar)
            TipoRegistro.PECHO -> HojaPechoHora(
                bebe.id,
                lado = registro.lado ?: LadoPecho.IZQUIERDA,
                dia = diaSeleccionado,
                registro = registro,
                onCerrar = cerrar
            )
        }
    }

    if (mostrarBloqueo) {
        HojaPremiumBloqueado { mostrarBloqueo = false }
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
 * El acumulado del día bajo "Su día": biberón, tomas de pecho y pañales,
 * cada uno con su icono y su propia cápsula de color (igual que
 * chipsResumenDelDia / resumenChips en VistaLinea.swift de iOS).
 *
 * El total de sueño NO va aquí a propósito: ya se ve entero en la barra
 * de "SUEÑO DE HOY" que hay justo debajo del reloj, y repetirlo arriba
 * era lo que dejaba la pantalla sin el dato que sí falta (los pañales).
 */
@Composable
private fun ChipsResumenDelDia(eventosDelDia: List<Registro>) {
    val colores = NubiTheme.colores

    val biberones = eventosDelDia.filter { it.tipo == TipoRegistro.BIBERON }
    val totalBiberonMl: Double = biberones.fold(0.0) { total, r ->
        val cantidad = r.cantidadBiberon
        val unidad = r.unidadBiberon
        if (cantidad == null || unidad == null) total else total + cantidad * unidad.factor
    }
    // iOS tiene una preferencia guardada de unidad (unidadBiberonPreferida);
    // aquí todavía no, así que se enseña en la unidad en la que él mismo
    // apuntó los biberones de hoy — y solo se cae a ml si ese día mezcló
    // las dos unidades.
    val unidadesUsadas = biberones.mapNotNull { it.unidadBiberon }.distinct()
    val unidadResumen = unidadesUsadas.singleOrNull() ?: UnidadBiberon.ML

    val tomasPecho = eventosDelDia.count { it.tipo == TipoRegistro.PECHO }
    val panales = eventosDelDia.filter { it.tipo == TipoRegistro.PANAL }

    val hayAlgo = totalBiberonMl > 0.0 || tomasPecho > 0 || panales.isNotEmpty()
    if (!hayAlgo) {
        Text(
            L10n.t("Sin registros"),
            style = MaterialTheme.typography.bodySmall,
            color = colores.tintaTenue
        )
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (totalBiberonMl > 0.0) {
            ChipResumen(
                icono = IconoNubi.BIBERON,
                color = colores.mantequilla,
                texto = textoResumenBiberon(totalBiberonMl, unidadResumen)
            )
        }
        if (tomasPecho > 0) {
            ChipResumen(
                icono = IconoNubi.CORAZON,
                color = colores.melocoton,
                texto = L10n.tomasPechoResumen(tomasPecho)
            )
        }
        if (panales.isNotEmpty()) {
            ChipResumen(
                icono = IconoNubi.PANAL,
                color = colores.cielo,
                texto = L10n.pañalesResumenDesglosado(
                    total = panales.size,
                    pis = panales.count { it.panalPis == true },
                    caca = panales.count { it.panalCaca == true }
                )
            )
        }
    }
}

@Composable
private fun ChipResumen(icono: IconoNubi, color: Color, texto: String) {
    val colores = NubiTheme.colores
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icono(icono, tint = color, tamano = 13.dp)
        Text(
            texto,
            style = MaterialTheme.typography.labelMedium,
            color = colores.tintaSuave,
            maxLines = 1
        )
    }
}

/** El total de biberón del día, en la unidad en la que él lo apuntó
 *  (ver formatearCantidadResumenBiberon en VistaLinea.swift de iOS). */
private fun textoResumenBiberon(totalMl: Double, unidad: UnidadBiberon): String {
    val valor = totalMl / unidad.factor
    if (unidad == UnidadBiberon.ML) return "${valor.roundToInt()} ml"
    val redondeado = (valor * 10).roundToInt() / 10.0
    return if (redondeado % 1.0 == 0.0) "${redondeado.toInt()} oz"
    else String.format(Locale.US, "%.1f oz", redondeado)
}

/**
 * La barra de "SUEÑO DE HOY": el eje es una escala de HORAS DORMIDAS (0h a
 * 24h de sueño total), no un reloj del día (eso ya lo dibuja RelojDelDia
 * arriba). Es solo la píldora la que lleva el marco de color — la tarjeta
 * en sí queda igual que el resto, sin borde — y dentro de la píldora se
 * ve siempre el tramo saludable para su edad (una banda fija, tenue, en
 * menta) como referencia; la barra en sí NO se rellena con lo dormido,
 * solo la insignia del total se desliza hasta su sitio. Marco de la
 * píldora y borde de la insignia se ponen en menta cuando lo acumulado
 * hoy ya cae dentro de esa banda sana para su edad; si no, en coral.
 */
@Composable
private fun BarraSuenoDia(
    guiaEdad: GuiaEdadAjustada,
    minutosTotales: Long,
    saludable: Boolean
) {
    val colores = NubiTheme.colores
    val colorMarco = if (saludable) colores.menta else colores.coral

    val fraccionTotal = (minutosTotales / (24f * 60f)).coerceIn(0f, 1f)
    val bias = (fraccionTotal * 2 - 1).coerceIn(-1f, 1f)
    val bandaDesde = (guiaEdad.suenoTotalHoras.start / 24.0).coerceIn(0.0, 1.0)
    val bandaHasta = (guiaEdad.suenoTotalHoras.endInclusive / 24.0).coerceIn(0.0, 1.0)

    Card(
        shape = RoundedCornerShape(NubiGeometria.radio),
        colors = CardDefaults.cardColors(containerColor = colores.superficie)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(L10n.t("SUEÑO DE HOY"), style = MaterialTheme.typography.labelMedium, color = colores.tintaTenue)
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Canvas(Modifier.matchParentSize()) {
                    val radio = size.height / 2f
                    // Fondo de la píldora.
                    drawRoundRect(
                        color = colores.lienzoAlto,
                        size = size,
                        cornerRadius = CornerRadius(radio)
                    )
                    // Banda fija y tenue con el rango de horas de sueño
                    // sano para su edad — no se mueve con lo dormido hoy,
                    // es solo la referencia contra la que se compara.
                    val xBandaDesde = (bandaDesde * size.width).toFloat()
                    val xBandaHasta = (bandaHasta * size.width).toFloat()
                    if (xBandaHasta > xBandaDesde) {
                        drawRect(
                            color = colores.menta.copy(alpha = 0.35f),
                            topLeft = Offset(xBandaDesde, 0f),
                            size = Size(xBandaHasta - xBandaDesde, size.height)
                        )
                    }
                    // El marco de la píldora: menta si lo dormido hoy cae
                    // dentro de la banda sana, coral si no. Se mete medio
                    // grosor hacia dentro para que el trazo no quede
                    // recortado por el borde del lienzo.
                    val grosorMarco = 1.5.dp.toPx()
                    drawRoundRect(
                        color = colorMarco,
                        topLeft = Offset(grosorMarco / 2f, grosorMarco / 2f),
                        size = Size(size.width - grosorMarco, size.height - grosorMarco),
                        cornerRadius = CornerRadius(radio - grosorMarco / 2f),
                        style = Stroke(width = grosorMarco)
                    )
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
private fun BotoneraDia(bebeId: String, dia: LocalDate, premium: Boolean) {
    val colores = NubiTheme.colores

    // Sin premium se bloquean los ocho tipos, sin excepción. La diferencia
    // con la pestaña Hoy es a propósito: en Hoy se puede apuntar una siesta
    // o un sueño EN EL MOMENTO (es lo que crea el hábito y lo que alimenta
    // a MotorSueno.kt durante la prueba), pero apuntar o corregir algo "de
    // hace un rato", que es para lo que sirve esta botonera, ya es premium.
    val bloqueado = !premium

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
                // Mismo texto que iOS (botoneraRegistro en VistaLinea.swift).
                if (premium) L10n.t("¿Se te olvidó? Toca y elige la hora")
                else L10n.t("Apuntar a otra hora es parte de Nubi Premium."),
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
    // El círculo mide 38 dp con un icono de 17,5 dp — exactamente la misma
    // proporción que Insignia en iOS (diametro 38, icono diametro * 0.46).
    // Antes el círculo era de 44 dp con icono de 18 dp y el dibujo salía
    // visiblemente más pequeño dentro del círculo que en iOS. La caja que
    // recibe el toque se deja en 44 dp para no perder zona táctil.
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(colorDeTipo(tipo, colores))
                .graphicsLayer(alpha = if (bloqueado) 0.4f else 1f),
            contentAlignment = Alignment.Center
        ) {
            Icono(iconoDeTipo(tipo), tint = tinteIconoDeTipo(tipo, porDefecto = colores.tinta), tamano = 17.5.dp)
        }
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
 *  con la siguiente, título con sus chips, subtítulo, y un lápiz + una X
 *  para editar/borrar. Tocar la fila en cualquier punto también edita. */
@Composable
private fun FilaTimeline(
    registro: Registro,
    diaMostrado: LocalDate,
    esUltimo: Boolean,
    onEditar: () -> Unit,
    onBorrar: () -> Unit
) {
    val colores = NubiTheme.colores
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onEditar)) {
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
                colorIcono = tinteIconoDeTipo(registro.tipo, porDefecto = colores.tinta),
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
        // El lápiz deja claro, sin tener que explicarlo en un texto aparte,
        // que el registro se puede tocar para editarlo (igual que en iOS).
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onEditar),
            contentAlignment = Alignment.Center
        ) {
            Icono(IconoNubi.LAPIZ, tint = colores.tintaSuave, tamano = 16.dp)
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onBorrar),
            contentAlignment = Alignment.Center
        ) {
            Icono(IconoNubi.EQUIS, tint = colores.tintaSuave, tamano = 16.dp)
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
            "${L10n.t("Desde las")} ${formatoHoraCorta.format(registro.inicio)}"
        }

    TipoRegistro.BIBERON -> {
        val partes = mutableListOf(formatoHoraCorta.format(registro.inicio))
        registro.cantidadBiberon?.let { cantidad ->
            val unidad = registro.unidadBiberon?.etiqueta ?: "ml"
            partes.add("${cantidad.toInt()} $unidad")
        }
        registro.tipoLeche?.let { partes.add(if (it.name == "MATERNA") L10n.t("Materna") else L10n.t("Fórmula")) }
        partes.joinToString(" · ")
    }

    TipoRegistro.PECHO -> {
        val partes = mutableListOf(formatoHoraCorta.format(registro.inicio))
        registro.lado?.let { partes.add(if (it.name == "IZQUIERDA") L10n.t("Izquierdo") else L10n.t("Derecho")) }
        partes.joinToString(" · ")
    }

    TipoRegistro.PANAL -> {
        val detalle = when {
            registro.panalPis == true && registro.panalCaca == true -> L10n.t("Pis y caca")
            registro.panalCaca == true -> L10n.t("Caca")
            registro.panalPis == true -> L10n.t("Pis")
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
    TipoRegistro.SIESTA -> L10n.t("Siesta")
    TipoRegistro.NOCHE -> L10n.t("Sueño nocturno")
    TipoRegistro.BIBERON -> L10n.t("Biberón")
    TipoRegistro.PECHO -> L10n.t("Pecho")
    TipoRegistro.PANAL -> L10n.t("Pañal")
    TipoRegistro.DESPERTAR -> L10n.t("Despertar")
    TipoRegistro.MEDICINA -> L10n.t("Medicina")
    TipoRegistro.BANO -> L10n.t("Baño")
}

private fun textoEdad(bebe: Bebe): String = L10n.textoEdad(bebe.edadEnDias())

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
                L10n.t("Pendiente de portar desde iOS."),
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
fun PantallaAjustes(bebe: Bebe?, perfil: Perfil?, alCerrarSesion: () -> Unit) {
    var confirmarEliminarCuenta by remember { mutableStateOf(false) }
    var errorEliminarCuenta by remember { mutableStateOf(false) }
    var eliminandoCuenta by remember { mutableStateOf(false) }
    var mostrarHojaBebe by remember { mutableStateOf(false) }
    var mostrarHojaSuenoSeguro by remember { mutableStateOf(false) }
    val ambito = rememberCoroutineScope()
    val colores = NubiTheme.colores
    val contexto = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NubiGeometria.margen)
    ) {
        Text(L10n.t("Ajustes"), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(18.dp))

        // El modo día/noche ya no se elige aquí: se alterna directamente
        // con el botón de sol/luna de la cabecera de Hoy.

        TarjetaEstadoSuscripcion(perfil)
        Spacer(Modifier.height(14.dp))

        TarjetaIdioma()
        Spacer(Modifier.height(14.dp))

        bebe?.let {
            Card(
                shape = RoundedCornerShape(NubiGeometria.radioChico),
                colors = CardDefaults.cardColors(containerColor = colores.superficie),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(L10n.t("Bebé"), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    FilaDato(L10n.t("Nombre"), it.nombre)
                    HorizontalDivider(color = colores.separador)
                    FilaDato(L10n.t("Nacimiento"), formatoFecha.format(it.fechaNacimiento))
                    HorizontalDivider(color = colores.separador)
                    FilaDato(L10n.t("Edad"), textoEdad(it))
                    if (it.semanasPrematuro > 0) {
                        HorizontalDivider(color = colores.separador)
                        FilaDato(L10n.t("Prematuridad"), "${it.semanasPrematuro} ${L10n.t("semanas")}")
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { mostrarHojaBebe = true },
                        shape = RoundedCornerShape(NubiGeometria.radioChico)
                    ) {
                        Icono(IconoNubi.LAPIZ, tint = colores.tinta, tamano = 14.dp)
                        Spacer(Modifier.width(7.dp))
                        Text(L10n.t("Editar datos"))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        Card(
            shape = RoundedCornerShape(NubiGeometria.radioChico),
            colors = CardDefaults.cardColors(containerColor = colores.superficie),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Nubi", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                FilaEnlace(IconoNubi.NUBE, colores.menta, L10n.t("Sueño seguro")) {
                    mostrarHojaSuenoSeguro = true
                }
                HorizontalDivider(color = colores.separador)
                FilaEnlace(IconoNubi.CANDADO, colores.lila, L10n.t("Política de privacidad")) {
                    abrirUrl(contexto, "https://appnubi.netlify.app/privacidad")
                }
                HorizontalDivider(color = colores.separador)
                FilaEnlace(IconoNubi.CALENDARIO, colores.cielo, L10n.t("Términos de uso")) {
                    abrirUrl(contexto, "https://appnubi.netlify.app/terminos")
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // Cuenta — mismo sitio y mismo texto que en iOS: borrado real en el
        // servidor, no solo un email a soporte (lo exige Apple 5.1.1(v), y es
        // lo correcto igualmente en Android).
        Card(
            shape = RoundedCornerShape(NubiGeometria.radioChico),
            colors = CardDefaults.cardColors(containerColor = colores.superficie),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(L10n.t("Cuenta"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = alCerrarSesion,
                    shape = RoundedCornerShape(NubiGeometria.radioChico),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(L10n.t("Cerrar sesión"))
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { confirmarEliminarCuenta = true },
                        enabled = !eliminandoCuenta
                    ) {
                        Text(L10n.t("Eliminar cuenta"), color = colores.coral)
                    }
                    if (eliminandoCuenta) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
                Text(
                    L10n.t("Borra tu cuenta de Nubi y todos sus datos en la nube. Es permanente."),
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.tintaTenue
                )
            }
        }
        Spacer(Modifier.height(18.dp))

        // Mismo pie que VistaAjustes.swift, al final de toda la pantalla.
        Text(
            L10n.t("Nubi no sustituye el criterio de un profesional sanitario."),
            style = MaterialTheme.typography.bodySmall,
            color = colores.tintaTenue,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
    }

    if (mostrarHojaBebe && bebe != null) {
        HojaBebe(bebe = bebe) { mostrarHojaBebe = false }
    }

    if (mostrarHojaSuenoSeguro) {
        HojaSuenoSeguro { mostrarHojaSuenoSeguro = false }
    }

    if (confirmarEliminarCuenta) {
        AlertDialog(
            onDismissRequest = { confirmarEliminarCuenta = false },
            title = { Text(L10n.t("¿Eliminar tu cuenta?")) },
            text = {
                Text(
                    L10n.t("Se elimina tu cuenta de Nubi y todos tus datos guardados en la nube: bebé, registros, medidas, citas y diario. No se puede deshacer.\n\nSi tienes una suscripción activa, cancélala en Google Play: eliminar la cuenta no la cancela.")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmarEliminarCuenta = false
                    ambito.launch {
                        eliminandoCuenta = true
                        try {
                            Supabase.client.postgrest.rpc("eliminar_mi_cuenta")
                            alCerrarSesion()
                        } catch (e: Exception) {
                            errorEliminarCuenta = true
                        }
                        eliminandoCuenta = false
                    }
                }) {
                    Text(L10n.t("Eliminar cuenta"), color = colores.coral)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmarEliminarCuenta = false }) {
                    Text(L10n.t("Cancelar"))
                }
            }
        )
    }

    if (errorEliminarCuenta) {
        AlertDialog(
            onDismissRequest = { errorEliminarCuenta = false },
            title = { Text(L10n.t("No se pudo eliminar la cuenta")) },
            text = { Text(L10n.t("Comprueba tu conexión e inténtalo de nuevo. Si el problema continúa, escribe a nubibabyapp@gmail.com.")) },
            confirmButton = {
                TextButton(onClick = { errorEliminarCuenta = false }) { Text(L10n.t("Listo")) }
            }
        )
    }
}

/** Abre un enlace en el navegador del sistema — mismo sitio en todos los
 *  enlaces de Ajustes (regalo, privacidad, términos). */
private fun abrirUrl(contexto: Context, url: String) {
    contexto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private val formatoFecha: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", L10n.localeJava).withZone(zonaLocal)

// Mismo patrón que fechaAMillisUtc/millisUtcAFecha de PantallaSalud.kt (son
// privadas a ese archivo, así que se repiten aquí): el DatePicker de
// Material3 trabaja en millis UTC de medianoche, y convertir con
// Instant.ofEpochMilli() directo desplaza el día según la zona del usuario.
private fun fechaAMillisUtc(fecha: Instant): Long =
    fecha.atZone(zonaLocal).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun millisUtcAFecha(millis: Long): Instant =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zonaLocal).toInstant()

/**
 * Tarjeta de "Regalar Nubi" en la pestaña Hoy — mismo sitio y mismo texto
 * que TarjetaRegalarNubi en VistaInicio.swift, al final del scroll.
 */
@Composable
private fun TarjetaRegalarNubi() {
    val colores = NubiTheme.colores
    val contexto = LocalContext.current
    Card(
        shape = RoundedCornerShape(NubiGeometria.radioChico),
        colors = CardDefaults.cardColors(containerColor = colores.superficie),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { abrirUrl(contexto, "https://appnubi.netlify.app/regalar") }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.nubi_logo_mark),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(32.dp).clip(CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    L10n.t("Regala Nubi"),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    L10n.t("Un regalo perfecto para otra familia con bebé"),
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.tintaSuave
                )
            }
            Icono(IconoNubi.CHEVRON, tint = colores.tintaTenue, tamano = 16.dp)
        }
    }
}

/**
 * Tarjeta de Instagram — mismo sitio que TarjetaInstagramNubi en
 * VistaInicio.swift: abre el perfil de Instagram de Nubi.
 */
@Composable
private fun TarjetaInstagramNubi() {
    val colores = NubiTheme.colores
    val contexto = LocalContext.current
    Card(
        shape = RoundedCornerShape(NubiGeometria.radioChico),
        colors = CardDefaults.cardColors(containerColor = colores.superficie),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                abrirUrl(contexto, "https://www.instagram.com/nubibaby.app?rpxt=MThpcjViYzY3Y3J3")
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.logo_instagram),
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    L10n.t("Aprende a usar Nubi"),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    L10n.t("Síguenos en Instagram"),
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.tintaSuave
                )
            }
            Icono(IconoNubi.CHEVRON, tint = colores.tintaTenue, tamano = 16.dp)
        }
    }
}

/**
 * Envuelve TarjetaRegalarNubi y TarjetaInstagramNubi en un carrusel que
 * cambia solo cada 5 segundos (y que también se puede deslizar a mano en
 * cualquier momento) — igual que TarjetaDinamicaRegalarInstagram en
 * VistaInicio.swift.
 */
@Composable
private fun TarjetaDinamicaRegalarInstagram() {
    val estadoPagina = rememberPagerState(pageCount = { 2 })

    LaunchedEffect(estadoPagina) {
        while (true) {
            delay(5000)
            estadoPagina.animateScrollToPage((estadoPagina.currentPage + 1) % 2)
        }
    }

    HorizontalPager(state = estadoPagina, modifier = Modifier.fillMaxWidth()) { pagina ->
        if (pagina == 0) TarjetaRegalarNubi() else TarjetaInstagramNubi()
    }
}

/**
 * Estado de la cuenta (premium / prueba / caducada) + acceso a regalar
 * Nubi — mismo agrupamiento que tarjetaSuscripcion en VistaAjustes.swift,
 * sin los botones de Gestionar/Suscribirse: Google Play Billing todavía no
 * está conectado en Android, así que no hay compra real que ofrecer aquí
 * todavía — solo el estado, que sí se puede leer de "perfiles".
 */
@Composable
private fun TarjetaEstadoSuscripcion(perfil: Perfil?) {
    val colores = NubiTheme.colores
    val contexto = LocalContext.current
    val ahora = Instant.now()
    val premiumHasta = perfil?.premiumHasta
    // Premium.activo(), no perfil.puedeUsarApp(): esta tarjeta mostraba
    // antes "Período de prueba"/"Prueba finalizada" según
    // fechaCortePrueba, el mecanismo de prueba SIN tarjeta que Android ya
    // no usa para decidir acceso (ver el comentario de
    // Perfil.puedeUsarApp() en Modelos.kt) — decía una cosa mientras el
    // resto de la app, correctamente, se regía por otra.
    val premiumActivo = Premium.activo(perfil)
    var mostrarPaywall by remember { mutableStateOf(false) }
    var mostrarHojaCanje by remember { mutableStateOf(false) }

    val titulo: String
    val subtitulo: String
    val colorBadge: Color

    when {
        perfil == null -> {
            titulo = L10n.t("Comprobando tu cuenta…")
            subtitulo = ""
            colorBadge = colores.tintaTenue
        }
        premiumActivo && premiumHasta != null && ahora.isBefore(premiumHasta) -> {
            titulo = L10n.t("Nubi Premium activo")
            subtitulo = "${L10n.t("Hasta el")} ${formatoFecha.format(premiumHasta)}"
            colorBadge = colores.mantequilla
        }
        premiumActivo -> {
            // Caso del interruptor de pruebas (ver más abajo): premium
            // simulado sin fecha real de Play Billing detrás.
            titulo = L10n.t("Nubi Premium activo")
            subtitulo = L10n.t("Simulado para pruebas")
            colorBadge = colores.mantequilla
        }
        else -> {
            titulo = L10n.t("Versión gratuita")
            subtitulo = L10n.t("Apuntar sueño y siestas desde Hoy es gratis. Hazte Premium para el resto.")
            colorBadge = colores.lila
        }
    }

    Card(
        shape = RoundedCornerShape(NubiGeometria.radioChico),
        colors = CardDefaults.cardColors(containerColor = colores.superficie),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BadgeIcono(
                    if (premiumActivo) IconoNubi.ESTRELLA else IconoNubi.CANDADO,
                    colorFondo = colorBadge,
                    colorIcono = colores.tinta,
                    tamano = 34.dp,
                    tamanoIcono = 16.dp
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (subtitulo.isNotEmpty()) {
                        Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = colores.tintaSuave)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = colores.separador)
            Spacer(Modifier.height(12.dp))

            // Entrada explícita a Premium desde Ajustes — no depende de
            // tocar antes algo bloqueado en Hoy/Día.
            if (!premiumActivo) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(NubiGeometria.radioChico))
                        .background(colores.lienzoAlto)
                        .clickable { mostrarPaywall = true }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BadgeIcono(
                        IconoNubi.ESTRELLA,
                        colorFondo = colores.lila,
                        colorIcono = colores.tinta,
                        tamano = 32.dp,
                        tamanoIcono = 15.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(L10n.t("Hazte Premium"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            L10n.t("7 días de prueba gratis"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colores.tintaSuave
                        )
                    }
                    Icono(IconoNubi.CHEVRON, tint = colores.tintaTenue, tamano = 14.dp)
                }
                Spacer(Modifier.height(10.dp))
            } else {
                // Mismo lugar y mismo propósito que el Link "Gestionar
                // suscripción en Apple" de iOS: con premium activo no hace
                // falta el CTA de arriba, pero sí un enlace directo a
                // gestionarla donde se cobra de verdad.
                Text(
                    L10n.t("Gestionar suscripción en Google Play"),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colores.indigo,
                    modifier = Modifier
                        .clickable {
                            abrirUrl(
                                contexto,
                                "https://play.google.com/store/account/subscriptions?package=com.app.nubi"
                            )
                        }
                        .padding(vertical = 4.dp)
                )
                Spacer(Modifier.height(10.dp))
            }

            // Interruptor SOLO en builds de depuración (Premium.kt):
            // desaparece del todo en release, no hace falta esconderlo
            // con más vueltas.
            if (Premium.disponibleEnPruebas) {
                val forzado by Premium.forzadoEnPruebas.collectAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(NubiGeometria.radioChico))
                        .background(colores.lienzoAlto)
                        .clickable { Premium.alternarPruebas() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            L10n.t("[Debug] Simular Premium"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            L10n.t("Solo en esta build — no usa Play Billing"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colores.tintaTenue
                        )
                    }
                    Switch(checked = forzado, onCheckedChange = { Premium.alternarPruebas() })
                }
                Spacer(Modifier.height(10.dp))

                // Mismo sitio y mismo propósito que el botón "[Debug] Probar
                // aviso de prueba" en VistaAjustes.swift: dispara el aviso de
                // "te quedan 3 días" ya mismo, con el copy real, para poder
                // verlo tal cual lo vería alguien de verdad.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(NubiGeometria.radioChico))
                        .background(colores.lienzoAlto)
                        .clickable { NotificadorPrueba.probarAhora(contexto) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            L10n.t("[Debug] Probar aviso de prueba"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            L10n.t("Lanza la notificación de \"te quedan 3 días\" en 5 segundos"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colores.tintaTenue
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(NubiGeometria.radioChico))
                    .background(colores.lienzoAlto)
                    .clickable { abrirUrl(contexto, "https://appnubi.netlify.app/regalar") }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BadgeIcono(
                    IconoNubi.CORAZON,
                    colorFondo = colores.rosa,
                    colorIcono = colores.tinta,
                    tamano = 32.dp,
                    tamanoIcono = 15.dp
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(L10n.t("Regalar Nubi"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        L10n.t("Regala 1, 3 o 12 meses de Nubi"),
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.tintaSuave
                    )
                }
                Icono(IconoNubi.CHEVRON, tint = colores.tintaTenue, tamano = 14.dp)
            }

            Spacer(Modifier.height(10.dp))

            // Canjear código: fila compacta dentro de esta misma tarjeta,
            // justo después de "Regalar Nubi" — igual que iOS, en vez de
            // una tarjeta aparte en la pantalla.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(NubiGeometria.radioChico))
                    .background(colores.lienzoAlto)
                    .clickable { mostrarHojaCanje = true }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BadgeIcono(
                    IconoNubi.REGALO,
                    colorFondo = colores.mantequilla,
                    colorIcono = colores.tinta,
                    tamano = 32.dp,
                    tamanoIcono = 15.dp
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        L10n.t("Canjear código de regalo"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        L10n.t("¿Tienes un código? Úsalo aquí"),
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.tintaSuave
                    )
                }
                Icono(IconoNubi.CHEVRON, tint = colores.tintaTenue, tamano = 14.dp)
            }
        }
    }

    if (mostrarPaywall) {
        HojaPremiumBloqueado { mostrarPaywall = false }
    }

    if (mostrarHojaCanje) {
        HojaCanjearCodigo { mostrarHojaCanje = false }
    }
}

/** Hoja para canjear un código de regalo — reusa BloqueCanjearCodigo, que
 *  ya se usaba suelto en PantallaBienvenida. Al canjear NO se cierra sola
 *  (igual que HojaRedimirCodigo.swift: el mensaje "¡Canjeado!" se queda
 *  visible y hay un botón "Cerrar" explícito para no taparlo). */
@Composable
private fun HojaCanjearCodigo(alCerrar: () -> Unit) {
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = alCerrar, sheetState = estadoHoja) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NubiGeometria.margen)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    L10n.t("Canjear código de regalo"),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = alCerrar) {
                    Text(L10n.t("Cerrar"))
                }
            }
            Spacer(Modifier.height(16.dp))
            BloqueCanjearCodigo()
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Selector de idioma — mismo sitio que tarjetaIdioma en VistaAjustes.swift
 * (justo debajo del estado de la cuenta). "Automático" sigue el idioma del
 * teléfono (L10n.idiomaEfectivo); los otros cuatro fuerzan ese idioma.
 */
@Composable
private fun TarjetaIdioma() {
    val colores = NubiTheme.colores
    var abierto by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(NubiGeometria.radioChico),
        colors = CardDefaults.cardColors(containerColor = colores.superficie),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(L10n.t("Idioma"), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))

            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(NubiGeometria.radioChico))
                        .clickable { abierto = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Globo sobre fondo translúcido, igual que iOS
                    // (Image(systemName: "globe") en indigo sobre
                    // Theme.cielo.opacity(0.3)) — antes era BOMBILLA sobre
                    // un círculo sólido, que no decía "idioma" a simple
                    // vista.
                    BadgeIcono(
                        IconoNubi.MUNDO,
                        colorFondo = colores.cielo.copy(alpha = 0.3f),
                        colorIcono = colores.indigo,
                        tamano = 34.dp,
                        tamanoIcono = 16.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        L10n.idioma.nombre,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Icono(IconoNubi.CHEVRON, tint = colores.tintaTenue, tamano = 14.dp)
                }

                DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                    IdiomaNubi.entries.forEach { opcion ->
                        DropdownMenuItem(
                            text = { Text(opcion.nombre) },
                            onClick = {
                                abierto = false
                                L10n.idioma = opcion
                                Repositorio.guardarIdioma(opcion.rawValue)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                L10n.t("Elige tu idioma"),
                style = MaterialTheme.typography.bodySmall,
                color = colores.tintaTenue
            )
        }
    }
}

/** Fila etiqueta/valor — la tarjeta "Bebé" de Ajustes. */
@Composable
private fun FilaDato(etiqueta: String, valor: String) {
    val colores = NubiTheme.colores
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = colores.tintaSuave)
        Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** Fila de enlace con badge + chevron — la tarjeta "Nubi" de Ajustes. */
@Composable
private fun FilaEnlace(icono: IconoNubi, colorBadge: Color, texto: String, onClick: () -> Unit) {
    val colores = NubiTheme.colores
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BadgeIcono(icono, colorFondo = colorBadge, colorIcono = colores.tinta, tamano = 36.dp, tamanoIcono = 16.dp)
        Spacer(Modifier.width(12.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Icono(IconoNubi.CHEVRON, tint = colores.tintaTenue, tamano = 14.dp)
    }
}

/**
 * Hoja para editar los datos del bebé — mismo contenido y mismos límites
 * que HojaBebe en VistaAjustes.swift (semanas de prematuridad 0..16),
 * guardando con el mismo Repositorio.guardarBebe que usa el resto de la app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaBebe(bebe: Bebe, alCerrar: () -> Unit) {
    val colores = NubiTheme.colores
    var nombre by remember { mutableStateOf(bebe.nombre) }
    var fechaNacimiento by remember { mutableStateOf(bebe.fechaNacimiento) }
    var semanasPrematuro by remember { mutableIntStateOf(bebe.semanasPrematuro) }
    var mostrarSelectorFecha by remember { mutableStateOf(false) }
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = alCerrar, sheetState = estadoHoja) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NubiGeometria.margen)
        ) {
            Text(L10n.t("Datos del bebé"), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(20.dp))

            Text(L10n.t("Nombre"), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text(L10n.t("Su nombre")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(18.dp))
            Text(L10n.t("Fecha de nacimiento"), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { mostrarSelectorFecha = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(formatoFecha.format(fechaNacimiento))
            }

            Spacer(Modifier.height(18.dp))
            Text(L10n.t("Semanas de prematuridad"), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { if (semanasPrematuro > 0) semanasPrematuro-- },
                    enabled = semanasPrematuro > 0
                ) { Text("−") }
                Text(
                    L10n.nSemanas(semanasPrematuro),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
                OutlinedButton(
                    onClick = { if (semanasPrematuro < 16) semanasPrematuro++ },
                    enabled = semanasPrematuro < 16
                ) { Text("+") }
            }

            Spacer(Modifier.height(26.dp))
            Button(
                onClick = {
                    Repositorio.guardarBebe(
                        bebe.copy(
                            nombre = nombre.trim().ifBlank { bebe.nombre },
                            fechaNacimiento = fechaNacimiento,
                            semanasPrematuro = semanasPrematuro
                        )
                    )
                    alCerrar()
                },
                enabled = nombre.isNotBlank(),
                shape = RoundedCornerShape(NubiGeometria.radioChico),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(L10n.t("Guardar"))
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (mostrarSelectorFecha) {
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = fechaAMillisUtc(fechaNacimiento),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = { mostrarSelectorFecha = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { millis -> fechaNacimiento = millisUtcAFecha(millis) }
                    mostrarSelectorFecha = false
                }) { Text(L10n.t("Aceptar")) }
            },
            dismissButton = { TextButton(onClick = { mostrarSelectorFecha = false }) { Text(L10n.t("Cancelar")) } }
        ) { DatePicker(state = estado) }
    }
}

/**
 * Info estática de sueño seguro — mismo texto que VistaSueñoSeguro en
 * VistaAjustes.swift, palabra por palabra.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaSuenoSeguro(alCerrar: () -> Unit) {
    val colores = NubiTheme.colores
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = alCerrar, sheetState = estadoHoja) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(NubiGeometria.margen)
        ) {
            BadgeIcono(
                IconoNubi.NUBE,
                colorFondo = colores.menta,
                colorIcono = colores.tinta,
                tamano = 60.dp,
                tamanoIcono = 28.dp
            )
            Spacer(Modifier.height(16.dp))
            Text(L10n.t("Sueño seguro"), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(14.dp))
            Text(
                L10n.t("Las recomendaciones generales de sueño seguro para lactantes incluyen acostar al bebé boca arriba, sobre una superficie firme y plana, sin almohadas, mantas sueltas ni peluches en la cuna, y compartir habitación pero no cama durante los primeros meses.\n\n") +
                    L10n.t("Evita el sobrecalentamiento y el humo del tabaco en el entorno del bebé.\n\n") +
                    L10n.t("Consulta siempre las guías de tu pediatra o de la sociedad de pediatría de tu país, que son la referencia válida para tu caso."),
                style = MaterialTheme.typography.bodyMedium,
                color = colores.tintaSuave
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
