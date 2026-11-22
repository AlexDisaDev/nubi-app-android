package app.nubi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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

/** Qué estaba haciendo el bebé al darlo de alta (pregunta obligatoria
 *  "¿Qué hace ahora?" de PantallaNuevoBebe). Igual que EstadoAlta en iOS. */
sealed class EstadoAlta {
    /** Ya se despertó, a esta hora (hoy, o ayer si era más tarde que ahora). */
    data class Despierto(val hora: Instant) : EstadoAlta()
    /** Está durmiendo una siesta desde esta hora. */
    data class Siesta(val inicio: Instant) : EstadoAlta()
    /** Está durmiendo su sueño nocturno desde esta hora. */
    data class Noche(val inicio: Instant) : EstadoAlta()

    companion object {
        /** "Está durmiendo desde las X": ¿siesta o noche? Noche si empezó de
         *  madrugada (antes de las 6:00), si fue ayer, o si empezó a menos de
         *  una hora de la hora de dormir más temprana para su edad
         *  (MotorSueno.guiaAjustada); si no, siesta. */
        fun durmiendo(inicio: Instant, meses: Double, ahora: Instant = Instant.now()): EstadoAlta {
            val zona = ZoneId.systemDefault()
            val local = inicio.atZone(zona)
            if (local.toLocalDate() != ahora.atZone(zona).toLocalDate()) return Noche(inicio)
            val minutos = local.hour * 60 + local.minute
            val g = MotorSueno.guiaAjustada(meses)
            return if (minutos < 6 * 60 || minutos >= g.bedtimeMin - 60) Noche(inicio) else Siesta(inicio)
        }
    }
}

/**
 * Pantalla que sale justo después del alta del bebé (en lugar del antiguo
 * aviso de bienvenida), según lo que contestaron en "¿Qué hace ahora?".
 * Igual que VistaBienvenidaAlta en iOS:
 *
 *  · Ya se despertó: su próxima siesta, o que le toca una ahora, o su sueño
 *    nocturno, con la tabla de siestas por edad (mismo motor que Hoy). Si ya
 *    pasó su hora de dormir (o lleva despierto desde ayer), se ofrece
 *    empezar el sueño nocturno.
 *  · Durmiendo una siesta: "Que descanse"; Nubi calculará la siguiente.
 *  · Durmiendo la noche: "Buenas noches"; Nubi calculará su primera siesta.
 *
 * Nunca un mensaje negativo. Los botones van fuera del scroll, dentro de la
 * zona segura. alContinuar recibe true si hay que empezar ya el sueño
 * nocturno.
 */
@Composable
fun PantallaBienvenidaAlta(
    bebe: Bebe,
    estado: EstadoAlta,
    alContinuar: (empezarNocheAhora: Boolean) -> Unit,
    /** Flecha de arriba a la izquierda (y el botón atrás del sistema):
     *  vuelve al alta con todo lo escrito, p. ej. para corregir la hora. */
    alVolver: () -> Unit
) {
    BackHandler(onBack = alVolver)
    val colores = NubiTheme.colores
    val ahora = remember { Instant.now() }
    val zona = ZoneId.systemDefault()
    val prediccion = remember(bebe, estado) {
        (estado as? EstadoAlta.Despierto)?.hora
            ?.takeIf { it.atZone(zona).toLocalDate() == ahora.atZone(zona).toLocalDate() }
            ?.let { prediccionInicial(bebe, it, ahora) }
    }
    val esHoraDeDormir = when (estado) {
        is EstadoAlta.Despierto ->
            estado.hora.atZone(zona).toLocalDate() != ahora.atZone(zona).toLocalDate() ||
                (prediccion != null && prediccion.tipo == TipoPrediccion.NOCHE &&
                    (prediccion.yaEnVentana(ahora) || prediccion.pasada(ahora)))
        else -> false
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
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .offset(x = (-4).dp)
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = L10n.t("Volver"), onClick = alVolver),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colores.superficie),
                contentAlignment = Alignment.Center
            ) {
                Icono(
                    IconoNubi.CHEVRON,
                    tint = colores.indigo,
                    tamano = 16.dp,
                    modifier = Modifier.graphicsLayer(scaleX = -1f)
                )
            }
        }

        // El contenido ocupa como mínimo toda la altura disponible y reparte
        // el hueco que sobra entre los bloques: en pantallas grandes respira,
        // en las pequeñas se junta, y solo si de verdad no cabe hay scroll.
        BoxWithConstraints(Modifier.weight(1f)) {
            val altoDisponible = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = altoDisponible)
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(R.drawable.nubi_logo_mark),
                        contentDescription = null,
                        modifier = Modifier.size(110.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        L10n.nubiConociendo(bebe.nombre),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colores.tintaSuave,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.weight(1f).heightIn(min = 18.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (estado) {
                            is EstadoAlta.Despierto -> ContenidoDespierto(bebe, estado.hora, prediccion, esHoraDeDormir, ahora)
                            is EstadoAlta.Siesta -> {
                                TituloBienvenida(L10n.t("Que descanse 💤"))
                                TarjetaDormido(estado.inicio, L10n.t("Cuando se despierte, Nubi calculará la siguiente"), esNoche = false)
                            }
                            is EstadoAlta.Noche -> {
                                TituloBienvenida(L10n.t("Buenas noches 🌙"))
                                TarjetaDormido(estado.inicio, L10n.t("Mañana, al despertar, Nubi calculará su primera siesta"), esNoche = true)
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f).heightIn(min = 14.dp))

                    TarjetaAprendiendo(
                        bebe.nombre,
                        when (estado) {
                            is EstadoAlta.Despierto -> L10n.t("Cuanto más registres, más se ajustará")
                            is EstadoAlta.Siesta -> L10n.t("Cuando se despierte, lo anotas en un toque")
                            is EstadoAlta.Noche -> L10n.t("Si se despierta por la noche, lo anotas en un toque")
                        }
                    )
                }
            }
        }

        if (esHoraDeDormir) {
            Button(
                onClick = { alContinuar(true) },
                shape = RoundedCornerShape(NubiGeometria.radioChico),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text(L10n.t("Empezar sueño nocturno"))
            }
            TextButton(
                onClick = { alContinuar(false) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(L10n.t("Ahora no"), color = colores.tintaSuave)
            }
        } else {
            Button(
                onClick = { alContinuar(false) },
                shape = RoundedCornerShape(NubiGeometria.radioChico),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text(L10n.t("Continuar"))
            }
        }
    }
}

@Composable
private fun TituloBienvenida(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
        color = NubiTheme.colores.tinta,
        textAlign = TextAlign.Center
    )
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
private fun ContenidoDespierto(
    bebe: Bebe,
    despertar: Instant,
    p: PrediccionDia?,
    esHoraDeDormir: Boolean,
    ahora: Instant
) {
    val colores = NubiTheme.colores

    if (esHoraDeDormir) {
        TituloBienvenida(L10n.t("Es buena hora para dormir"))
        TarjetaBienvenida {
            BadgeIcono(IconoNubi.LUNA, colorFondo = colores.lila.copy(alpha = 0.25f), colorIcono = colores.lila, tamano = 64.dp, tamanoIcono = 30.dp)
            Text(
                L10n.t("Cuando llegue el momento, empieza su sueño nocturno y Nubi lo irá midiendo"),
                style = MaterialTheme.typography.bodyLarge,
                color = colores.tintaSuave,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val esNoche = p?.tipo == TipoPrediccion.NOCHE
    TituloBienvenida(
        when {
            p == null -> L10n.t("Su próxima siesta")
            esNoche -> L10n.t("Su próximo sueño nocturno")
            p.yaEnVentana(ahora) || p.pasada(ahora) -> L10n.t("Le toca una siesta ahora")
            else -> L10n.t("Su próxima siesta")
        }
    )

    TarjetaBienvenida {
        // Siesta: nube en menta (su color en Nubi). Noche: luna en lila.
        IconoSueno(esNoche = esNoche, tamano = 64.dp, tamanoIcono = 30.dp)
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
                else -> L10n.t("Buen momento para una siesta")
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
                L10n.t("Nubi la calculará en cuanto tenga un poco más de datos"),
                style = MaterialTheme.typography.bodyLarge,
                color = colores.tintaSuave,
                textAlign = TextAlign.Center
            )
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = 12.dp)
    ) {
        Text(
            L10n.calculadaConEdad(L10n.textoEdadCorta(bebe.edadEnDias()), formatoHoraBienvenida.format(despertar)),
            style = MaterialTheme.typography.bodyMedium,
            color = colores.tintaSuave,
            textAlign = TextAlign.Center
        )
        if (bebe.edadEnMeses() < 3) {
            Text(
                L10n.t("A esta edad su ritmo aún es irregular, Nubi irá aprendiendo"),
                style = MaterialTheme.typography.bodyMedium,
                color = colores.tintaSuave,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Icono de siesta (nube, menta) o de sueño nocturno (luna, lila). */
@Composable
private fun IconoSueno(esNoche: Boolean, tamano: Dp, tamanoIcono: Dp) {
    val colores = NubiTheme.colores
    val color = if (esNoche) colores.lila else colores.menta
    BadgeIcono(
        if (esNoche) IconoNubi.LUNA else IconoNubi.NUBE,
        colorFondo = color.copy(alpha = 0.25f),
        colorIcono = color,
        tamano = tamano,
        tamanoIcono = tamanoIcono
    )
}

/** Una sola tarjeta protagonista: la hora en que se durmió en grande y qué
 *  hará Nubi cuando despierte. */
@Composable
private fun TarjetaDormido(inicio: Instant, texto: String, esNoche: Boolean) {
    val colores = NubiTheme.colores
    TarjetaBienvenida {
        IconoSueno(esNoche = esNoche, tamano = 52.dp, tamanoIcono = 24.dp)
        Text(
            L10n.t("Se durmió a las"),
            style = MaterialTheme.typography.bodyLarge,
            color = colores.tintaSuave
        )
        Text(
            formatoHoraBienvenida.format(inicio),
            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
            color = colores.tinta
        )
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = colores.tintaSuave,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TarjetaAprendiendo(nombre: String, texto: String) {
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
            texto,
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
