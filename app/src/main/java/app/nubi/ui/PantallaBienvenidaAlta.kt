package app.nubi.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nubi.R
import app.nubi.data.Bebe
import app.nubi.data.MotorSueno
import app.nubi.data.PrediccionDia
import app.nubi.data.Registro
import app.nubi.data.TipoPrediccion
import app.nubi.data.TipoRegistro
import app.nubi.i18n.L10n
import app.nubi.ui.theme.NubiGeometria
import app.nubi.ui.theme.NubiTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val formatoHoraBienvenida: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

/**
 * Pantalla que sale justo después del alta del bebé (en lugar del antiguo
 * aviso de bienvenida). Igual que VistaBienvenidaAlta en iOS. Dos versiones:
 *
 *  · Con hora de despertar: enseña ya su próxima siesta, sin candado — el
 *    "momento mágico" de Nubi. En Hoy, sin premium, esa tarjeta sale luego
 *    difuminada (TarjetaPrediccionBloqueada).
 *  · Sin hora de despertar: da por hecho que duerme y pregunta a qué hora se
 *    durmió, para empezar ya su sueño nocturno; Nubi calculará su primera
 *    siesta cuando despierte.
 *
 * Un solo botón, "Continuar": da de alta al bebé (PantallaNuevoBebe) y entra
 * en Hoy. Recibe la hora en que se durmió (solo en la versión sin despertar).
 */
@Composable
fun PantallaBienvenidaAlta(
    bebe: Bebe,
    despertar: Instant?,
    alContinuar: (horaDormido: Instant?) -> Unit
) {
    val colores = NubiTheme.colores
    val ahora = remember { Instant.now() }
    val zona = ZoneId.systemDefault()
    val horaInicial = remember { LocalTime.now(zona) }
    var hora by remember { mutableIntStateOf(horaInicial.hour) }
    var minuto by remember { mutableIntStateOf(horaInicial.minute) }

    // La hora elegida, hoy; si cae más tarde que ahora (p. ej. son las 00:30
    // y elige las 23:00), es de ayer.
    fun fechaDormido(): Instant {
        var fecha = LocalDate.now(zona).atTime(hora, minuto).atZone(zona).toInstant()
        if (fecha.isAfter(Instant.now())) fecha = fecha.minus(Duration.ofDays(1))
        return fecha
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colores.lienzo)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = NubiGeometria.margen)
            .padding(bottom = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Más apretada cuando hay que elegir la hora de dormir, para que
            // quepa entera sin scroll.
            verticalArrangement = Arrangement.spacedBy(if (despertar == null) 12.dp else 16.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.nubi_logo_mark),
                contentDescription = null,
                modifier = Modifier.size(if (despertar == null) 84.dp else 120.dp)
            )
            Text(
                L10n.nubiConociendo(bebe.nombre),
                style = MaterialTheme.typography.bodyLarge,
                color = colores.tintaSuave,
                textAlign = TextAlign.Center
            )

            if (despertar != null) {
                ContenidoPrediccion(bebe, despertar, ahora)
            } else {
                ContenidoDormido(bebe, hora, minuto, { hora = it }, { minuto = it })
            }

            TarjetaAprendiendo(bebe.nombre, conDespertar = despertar != null)
        }

        Button(
            onClick = { alContinuar(if (despertar == null) fechaDormido() else null) },
            shape = RoundedCornerShape(NubiGeometria.radioChico),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text(L10n.t("Continuar"))
        }
    }
}

/** Mismo cálculo que hará Hoy en cuanto se guarde el bebé: la noche de 00:00
 *  a la hora en que despertó y esa hora como despertar del día. */
private fun prediccionInicial(bebe: Bebe, despertar: Instant, ahora: Instant): PrediccionDia? {
    val zona = ZoneId.systemDefault()
    val medianoche = despertar.atZone(zona).toLocalDate().atStartOfDay(zona).toInstant()
    val registros = if (!despertar.isAfter(ahora) && Duration.between(medianoche, despertar).seconds >= 60) {
        listOf(Registro(bebeId = bebe.id, tipo = TipoRegistro.NOCHE, inicio = medianoche, fin = despertar))
    } else emptyList()
    return MotorSueno.prediccionDelDia(bebe = bebe, registros = registros, ahora = ahora, despertarManual = despertar)
}

private fun duracionCorta(minutos: Long): String = when {
    minutos < 60 -> "$minutos min"
    minutos % 60 == 0L -> "${minutos / 60} h"
    else -> "${minutos / 60} h ${minutos % 60} min"
}

@Composable
private fun ContenidoPrediccion(bebe: Bebe, despertar: Instant, ahora: Instant) {
    val colores = NubiTheme.colores
    val p = remember(bebe, despertar) { prediccionInicial(bebe, despertar, ahora) }
    val esNoche = p?.tipo == TipoPrediccion.NOCHE

    Text(
        if (esNoche) L10n.t("Su próximo sueño nocturno") else L10n.t("Su próxima siesta"),
        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
        color = colores.tinta,
        textAlign = TextAlign.Center
    )

    TarjetaBienvenida {
        BadgeIcono(IconoNubi.LUNA, colorFondo = colores.lila.copy(alpha = 0.25f), colorIcono = colores.lila, tamano = 64.dp, tamanoIcono = 30.dp)
        if (p != null) {
            Text(
                if (esNoche) formatoHoraBienvenida.format(p.desde)
                else "${formatoHoraBienvenida.format(p.desde)} – ${formatoHoraBienvenida.format(p.hasta)}",
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                color = colores.tinta,
                maxLines = 1
            )
            val cuando = when {
                p.desde.isAfter(ahora) -> L10n.enDuracion(duracionCorta(Duration.between(ahora, p.desde).toMinutes()))
                !p.hasta.isBefore(ahora) -> L10n.t("Buena ventana ahora")
                else -> L10n.t("Se ha pasado la ventana ideal")
            }
            Text(
                cuando,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = colores.menta,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colores.menta.copy(alpha = 0.18f))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        } else {
            Text(
                L10n.t("Nubi la calculará en cuanto tenga un poco más de datos."),
                style = MaterialTheme.typography.bodyLarge,
                color = colores.tintaSuave,
                textAlign = TextAlign.Center
            )
        }
    }

    Text(
        L10n.calculadaConEdad(L10n.textoEdadCorta(bebe.edadEnDias()), formatoHoraBienvenida.format(despertar)),
        style = MaterialTheme.typography.bodyMedium,
        color = colores.tintaSuave,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}

@Composable
private fun ContenidoDormido(
    bebe: Bebe,
    hora: Int,
    minuto: Int,
    onHora: (Int) -> Unit,
    onMinuto: (Int) -> Unit
) {
    val colores = NubiTheme.colores
    Text(
        L10n.yaDurmiendo(bebe.nombre),
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = colores.tinta,
        textAlign = TextAlign.Center
    )

    TarjetaBienvenida(compacta = true) {
        BadgeIcono(IconoNubi.LUNA, colorFondo = colores.lila.copy(alpha = 0.25f), colorIcono = colores.lila, tamano = 40.dp, tamanoIcono = 20.dp)
        Text(
            L10n.t("Marca su sueño nocturno"),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colores.tinta
        )
        Text(
            L10n.t("y mañana, al despertar, Nubi calculará su primera siesta."),
            style = MaterialTheme.typography.bodyMedium,
            color = colores.tintaSuave,
            textAlign = TextAlign.Center
        )
    }

    // Una sola fila: la pregunta y, a la derecha, la hora en una cápsula que
    // al tocarla abre el selector — la rueda en línea ocupaba media pantalla
    // y obligaba a hacer scroll.
    var mostrarSelector by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NubiGeometria.radio))
            .background(colores.superficie)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            L10n.t("¿A qué hora se durmió tu bebé?"),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colores.tinta,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "%02d:%02d".format(hora, minuto),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = colores.tinta,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colores.lila.copy(alpha = 0.25f))
                .clickable { mostrarSelector = true }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
    if (mostrarSelector) {
        SelectorHoraDormido(hora, minuto, onCerrar = { mostrarSelector = false }) { h, m ->
            onHora(h)
            onMinuto(m)
        }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(colores.lila.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text("i", style = MaterialTheme.typography.labelLarge, color = colores.tinta)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            L10n.t("Si se despierta por la noche, podrás registrar cada despertar en un toque."),
            style = MaterialTheme.typography.bodySmall,
            color = colores.tintaSuave
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorHoraDormido(hora: Int, minuto: Int, onCerrar: () -> Unit, onElegir: (Int, Int) -> Unit) {
    val estado = rememberTimePickerState(initialHour = hora, initialMinute = minuto, is24Hour = true)
    AlertDialog(
        onDismissRequest = onCerrar,
        confirmButton = {
            TextButton(onClick = {
                onElegir(estado.hour, estado.minute)
                onCerrar()
            }) { Text(L10n.t("Aceptar")) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text(L10n.t("Cancelar")) } },
        text = { TimePicker(state = estado) }
    )
}

@Composable
private fun TarjetaAprendiendo(nombre: String, conDespertar: Boolean) {
    val colores = NubiTheme.colores
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NubiGeometria.radio))
            .background(colores.superficie)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                L10n.aprendiendoRitmo(nombre),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colores.tinta,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text("${L10n.t("Día")} 1/7", style = MaterialTheme.typography.bodySmall, color = colores.tintaSuave)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(7) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i == 0) colores.lila else colores.lila.copy(alpha = 0.2f))
                )
            }
        }
        Text(
            if (conDespertar) L10n.t("Cuanto más registres, más se ajustará.")
            else L10n.t("Esta noche empieza a conocer sus sueños."),
            style = MaterialTheme.typography.bodySmall,
            color = colores.tintaTenue
        )
    }
}

@Composable
private fun TarjetaBienvenida(compacta: Boolean = false, contenido: @Composable () -> Unit) {
    val colores = NubiTheme.colores
    val forma = RoundedCornerShape(NubiGeometria.radio + 6.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(colores.superficie)
            .border(1.dp, colores.lila.copy(alpha = 0.25f), forma)
            .padding(vertical = if (compacta) 14.dp else 22.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compacta) 6.dp else 12.dp)
    ) {
        contenido()
    }
}
