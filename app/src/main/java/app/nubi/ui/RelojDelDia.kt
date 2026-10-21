package app.nubi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.nubi.data.Registro
import app.nubi.data.TipoRegistro
import app.nubi.ui.theme.NubiColores
import app.nubi.ui.theme.NubiTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * El día como un reloj de 24 horas: medianoche arriba, mediodía abajo.
 * Portado de RelojDelDia.swift, y ampliado sobre la captura de VistaLinea:
 *
 * - Anillo de fondo con un degradado que recorre los colores de Nubi según
 *   la hora (noche → amanecer → día → tarde → noche), en vez de un solo
 *   color plano.
 * - Los sueños de verdad (siesta/noche) se resaltan encima, más gruesos,
 *   con un brillo fino en el centro del trazo.
 * - Una aguja desde el centro marca la hora actual.
 * - Fuera del anillo solo va el sueño: luna a la hora de acostarse, sol a
 *   la hora de despertar, nube a la hora de la siesta — cada uno en su
 *   sitio, no una insignia a mitad del arco.
 * - Dentro del anillo, un círculo pequeño por cada evento suelto (biberón,
 *   pecho, pañal, medicina, baño...), más discreto que el sueño.
 *
 * Sigue siendo una versión inicial: faltan las ventanas ideales de sueño,
 * la sugerencia de la próxima siesta y poder tocar un evento para abrirlo.
 */
@Composable
fun RelojDelDia(
    registros: List<Registro>,
    dia: LocalDate = LocalDate.now(),
    modifier: Modifier = Modifier
) {
    val colores = NubiTheme.colores
    val zona = ZoneId.systemDefault()
    val densidad = LocalDensity.current

    fun horaDecimal(instante: Instant): Double {
        val t = instante.atZone(zona)
        return t.hour + t.minute / 60.0 + t.second / 3600.0
    }

    // Un sueño nocturno puede haber empezado ayer y terminar hoy: para este
    // día en concreto solo nos interesa el tramo (desde, hasta) que cae
    // dentro de él, no el sueño entero.
    val suenosConTramo = registros
        .filter { it.tipo.esSueno && it.fin != null }
        .mapNotNull { r -> tramoEnDia(r, dia, zona)?.let { r to it } }
    // El despertar nocturno se trata como el sueño (fuera del anillo, con
    // su propia insignia): solo biberón/pecho/pañal/medicina/baño van
    // dentro, como círculos pequeños.
    val despertares = registros.filter {
        it.tipo == TipoRegistro.DESPERTAR && it.inicio.atZone(zona).toLocalDate() == dia
    }
    val eventos = registros.filter {
        !it.tipo.esSueno && it.tipo != TipoRegistro.DESPERTAR &&
            it.inicio.atZone(zona).toLocalDate() == dia
    }

    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1f)) {
        val ladoPx = constraints.maxWidth.toFloat()
        val centro = Offset(ladoPx / 2f, ladoPx / 2f)

        fun dp(valor: Int): Float = with(densidad) { valor.dp.toPx() }

        val margenBorde = dp(32)
        val grosor = dp(24)
        val radioExterior = ladoPx / 2f - margenBorde
        val radioAnillo = radioExterior - grosor / 2f
        val radioEtiqueta = radioAnillo + grosor / 2f + dp(22)
        val radioInsignia = radioAnillo + grosor / 2f + dp(22)
        // Claramente dentro del aro de color, no encima ni fuera de él.
        val radioInterior = radioAnillo - grosor / 2f - dp(26)
        val tamanoInsigniaPx = dp(30)
        val tamanoEventoPx = dp(20)

        Canvas(Modifier.matchParentSize()) {
            val esquina = Offset(centro.x - radioAnillo, centro.y - radioAnillo)
            val tamanoCaja = Size(radioAnillo * 2f, radioAnillo * 2f)

            // Anillo de fondo: un degradado que recorre la paleta según la
            // hora, para que de un vistazo se note noche/amanecer/día/tarde.
            rotate(degrees = -90f, pivot = centro) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colorStops = arrayOf(
                            0.0f to colores.lila,
                            0.25f to colores.melocoton,
                            0.5f to colores.cielo,
                            0.75f to colores.menta,
                            1.0f to colores.lila
                        ),
                        center = centro
                    ),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = esquina,
                    size = tamanoCaja,
                    style = Stroke(width = grosor),
                    alpha = 0.55f
                )
            }

            // Cada sueño de verdad, resaltado encima del degradado.
            suenosConTramo.forEach { (sueno, tramo) ->
                val (desde, hasta) = tramo
                val duracion = hasta - desde
                val colorSueno = if (sueno.tipo == TipoRegistro.NOCHE) colores.lila else colores.menta
                val anguloInicio = (desde / 24.0 * 360.0 - 90.0).toFloat()
                val anguloBarrido = (duracion / 24.0 * 360.0).toFloat()

                drawArc(
                    color = colorSueno,
                    startAngle = anguloInicio,
                    sweepAngle = anguloBarrido,
                    useCenter = false,
                    topLeft = esquina,
                    size = tamanoCaja,
                    style = Stroke(width = grosor, cap = StrokeCap.Round)
                )
                // Brillo fino en el centro del trazo.
                drawArc(
                    color = colores.superficie.copy(alpha = 0.5f),
                    startAngle = anguloInicio,
                    sweepAngle = anguloBarrido,
                    useCenter = false,
                    topLeft = esquina,
                    size = tamanoCaja,
                    style = Stroke(width = grosor * 0.22f, cap = StrokeCap.Round)
                )
            }

            // Marcas de las ocho horas principales.
            listOf(0, 3, 6, 9, 12, 15, 18, 21).forEach { hora ->
                val angulo = (hora / 24.0 * 2 * PI) - PI / 2
                val desde = Offset(
                    centro.x + ((radioAnillo + grosor / 2 + dp(4)) * cos(angulo)).toFloat(),
                    centro.y + ((radioAnillo + grosor / 2 + dp(4)) * sin(angulo)).toFloat()
                )
                val hasta = Offset(
                    centro.x + ((radioAnillo + grosor / 2 + dp(10)) * cos(angulo)).toFloat(),
                    centro.y + ((radioAnillo + grosor / 2 + dp(10)) * sin(angulo)).toFloat()
                )
                drawLine(color = colores.separador, start = desde, end = hasta, strokeWidth = dp(2))
            }

            // Aguja con la hora actual, solo si se está mirando el día de hoy.
            if (dia == LocalDate.now(zona)) {
                val angulo = (horaDecimal(Instant.now()) / 24.0 * 2 * PI) - PI / 2
                val punta = Offset(
                    centro.x + (radioAnillo * 0.78f * cos(angulo)).toFloat(),
                    centro.y + (radioAnillo * 0.78f * sin(angulo)).toFloat()
                )
                drawLine(
                    color = colores.tinta,
                    start = centro,
                    end = punta,
                    strokeWidth = dp(3),
                    cap = StrokeCap.Round
                )
                drawCircle(color = colores.tinta, radius = dp(5), center = centro)
            }
        }

        // Números de las horas, como texto normal colocado sobre el anillo.
        listOf(0, 3, 6, 9, 12, 15, 18, 21).forEach { hora ->
            val angulo = (hora / 24.0 * 2 * PI) - PI / 2
            val x = centro.x + (radioEtiqueta * cos(angulo)).toFloat()
            val y = centro.y + (radioEtiqueta * sin(angulo)).toFloat()
            Text(
                "%02d".format(hora),
                style = MaterialTheme.typography.labelMedium,
                color = colores.tintaSuave,
                modifier = Modifier.offsetPx(x - dp(14), y - dp(10))
            )
        }

        // Fuera del anillo solo va el sueño, y cada extremo por separado:
        // la luna a la hora de acostarse, el sol a la hora de despertar
        // (no una insignia a mitad del arco). Si el tramo viene truncado
        // porque el sueño sigue desde ayer o continúa mañana, ese extremo
        // no es una hora real de hoy y no lleva insignia.
        suenosConTramo.forEach { (sueno, tramo) ->
            val fin = sueno.fin ?: return@forEach
            val (desde, hasta) = tramo
            val diaInicioReal = sueno.inicio.atZone(zona).toLocalDate()
            val diaFinReal = fin.atZone(zona).toLocalDate()

            @Composable
            fun insigniaSueno(hora: Double, tipo: IconoNubi, colorFondo: Color) {
                val angulo = (hora / 24.0 * 2 * PI) - PI / 2
                val x = centro.x + (radioInsignia * cos(angulo)).toFloat()
                val y = centro.y + (radioInsignia * sin(angulo)).toFloat()
                BadgeIcono(
                    tipo = tipo,
                    colorFondo = colorFondo,
                    colorIcono = colores.tinta,
                    tamano = 30.dp,
                    tamanoIcono = 14.dp,
                    modifier = Modifier.offsetPx(x - tamanoInsigniaPx / 2f, y - tamanoInsigniaPx / 2f)
                )
            }

            if (sueno.tipo == TipoRegistro.NOCHE) {
                if (diaInicioReal == dia) insigniaSueno(desde, IconoNubi.LUNA, colores.lila)
                if (diaFinReal == dia) insigniaSueno(hasta, IconoNubi.SOL, colores.mantequilla)
            } else if (diaInicioReal == dia) {
                insigniaSueno(desde, IconoNubi.NUBE, colores.menta)
            }
        }

        // El despertar nocturno se trata igual que el sueño: fuera del
        // anillo, a la hora en la que ocurrió.
        despertares.forEach { despertar ->
            val angulo = (horaDecimal(despertar.inicio) / 24.0 * 2 * PI) - PI / 2
            val x = centro.x + (radioInsignia * cos(angulo)).toFloat()
            val y = centro.y + (radioInsignia * sin(angulo)).toFloat()
            BadgeIcono(
                tipo = iconoDeTipo(despertar.tipo),
                colorFondo = colorDeTipo(despertar.tipo, colores),
                colorIcono = colores.tinta,
                tamano = 30.dp,
                tamanoIcono = 14.dp,
                modifier = Modifier.offsetPx(x - tamanoInsigniaPx / 2f, y - tamanoInsigniaPx / 2f)
            )
        }

        // Dentro del anillo, un círculo pequeño por cada evento suelto
        // (biberón, pecho, pañal, medicina, baño...) — claramente dentro,
        // ni encima del aro de color ni fuera de él.
        //
        // Dos eventos casi a la misma hora (p. ej. biberón y pañal juntos)
        // caerían exactamente en el mismo punto y uno taparía al otro, así
        // que los agrupamos por cercanía y los separamos un poco en radio.
        val gruposEventos = mutableListOf<MutableList<Registro>>()
        eventos.sortedBy { it.inicio }.forEach { evento ->
            val horaEvento = horaDecimal(evento.inicio)
            val grupoActual = gruposEventos.lastOrNull()?.takeIf { grupo ->
                kotlin.math.abs(horaEvento - horaDecimal(grupo.last().inicio)) < 0.4
            }
            if (grupoActual != null) grupoActual.add(evento) else gruposEventos.add(mutableListOf(evento))
        }
        gruposEventos.forEach { grupo ->
            val anguloBase = horaDecimal(grupo.first().inicio)
            val angulo = (anguloBase / 24.0 * 2 * PI) - PI / 2
            grupo.forEachIndexed { indice, evento ->
                val desplazamiento = (indice - (grupo.size - 1) / 2f) * dp(18)
                val radio = radioInterior + desplazamiento
                val x = centro.x + (radio * cos(angulo)).toFloat()
                val y = centro.y + (radio * sin(angulo)).toFloat()
                BadgeIcono(
                    tipo = iconoDeTipo(evento.tipo),
                    colorFondo = colorDeTipo(evento.tipo, colores),
                    colorIcono = colores.tinta,
                    tamano = 20.dp,
                    tamanoIcono = 10.dp,
                    modifier = Modifier.offsetPx(x - tamanoEventoPx / 2f, y - tamanoEventoPx / 2f)
                )
            }
        }

        if (suenosConTramo.isEmpty() && despertares.isEmpty() && eventos.isEmpty()) {
            Text(
                "Sin datos aún",
                style = MaterialTheme.typography.bodySmall,
                color = colores.tintaTenue,
                modifier = Modifier.offsetPx(centro.x - dp(40), centro.y)
            )
        }
    }
}

/**
 * El tramo (desde, hasta), en horas decimales, que le corresponde a "dia"
 * de un registro que puede haber empezado el día anterior o terminar el
 * siguiente — por ejemplo, un sueño nocturno de 20:24 a 8:00.
 *
 * Devuelve null cuando el registro no toca ese día en absoluto, así que
 * también sirve para filtrar ("solo los que sí pertenecen a este día").
 */
fun tramoEnDia(registro: Registro, dia: LocalDate, zona: ZoneId): Pair<Double, Double>? {
    val fin = registro.fin ?: return null
    val diaInicio = registro.inicio.atZone(zona).toLocalDate()
    val diaFin = fin.atZone(zona).toLocalDate()

    fun horaDecimal(instante: Instant): Double {
        val t = instante.atZone(zona)
        return t.hour + t.minute / 60.0 + t.second / 3600.0
    }

    return when {
        diaInicio == dia && diaFin == dia -> horaDecimal(registro.inicio) to horaDecimal(fin)
        diaInicio == dia && diaFin != dia -> horaDecimal(registro.inicio) to 24.0
        diaInicio != dia && diaFin == dia -> 0.0 to horaDecimal(fin)
        else -> null
    }
}

/** Qué dibujo le toca a cada tipo de registro — se usa en el reloj, la
 *  botonera y la lista del día, para que los tres coincidan. */
fun iconoDeTipo(tipo: TipoRegistro): IconoNubi = when (tipo) {
    TipoRegistro.SIESTA -> IconoNubi.NUBE
    TipoRegistro.NOCHE -> IconoNubi.LUNA
    TipoRegistro.BIBERON -> IconoNubi.BIBERON
    TipoRegistro.PECHO -> IconoNubi.CORAZON
    TipoRegistro.PANAL -> IconoNubi.GOTA
    TipoRegistro.DESPERTAR -> IconoNubi.LUNA
    TipoRegistro.MEDICINA -> IconoNubi.MEDICINA
    TipoRegistro.BANO -> IconoNubi.BANO
}

/** Qué color le toca a cada tipo de registro. */
fun colorDeTipo(tipo: TipoRegistro, c: NubiColores): Color = when (tipo) {
    TipoRegistro.SIESTA -> c.menta
    TipoRegistro.NOCHE -> c.lila
    TipoRegistro.BIBERON -> c.mantequilla
    TipoRegistro.PECHO -> c.melocoton
    TipoRegistro.PANAL -> c.cielo
    TipoRegistro.DESPERTAR -> c.coral
    TipoRegistro.MEDICINA -> c.indigo
    TipoRegistro.BANO -> c.rosa
}

/** Atajo para "offset en píxeles ya calculados", sin tener que convertir
 *  a Dp y volver — todo el reloj trabaja en píxeles porque Canvas y las
 *  insignias comparten el mismo tamaño medido (ladoPx). */
private fun Modifier.offsetPx(x: Float, y: Float): Modifier = this.offset {
    IntOffset(x.roundToInt(), y.roundToInt())
}
