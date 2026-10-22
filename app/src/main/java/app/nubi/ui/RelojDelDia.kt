package app.nubi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
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
import app.nubi.data.Bebe
import app.nubi.data.MotorSueno
import app.nubi.data.Registro
import app.nubi.data.TipoRegistro
import app.nubi.data.VentanaIdeal
import app.nubi.ui.theme.NubiColores
import app.nubi.ui.theme.NubiTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * El día como un reloj de 24 horas: medianoche arriba, mediodía abajo.
 * Traducido en fidelidad de RelojDelDia.swift:
 *
 * - Anillo de fondo con un degradado angular de 8 paradas (noche, amanecer,
 *   día, atardecer) más estrellas y nubes decorativas dentro del propio
 *   trazo, y un borde fino a cada lado.
 * - 24 marcas horarias (una por hora, las de 0/6/12/18 más marcadas).
 * - Los sueños de verdad (siesta/noche), resaltados sobre el degradado con
 *   un brillo blanco fino; los despertares nocturnos (restaSueño) se pintan
 *   igual pero sin el brillo, como un tramo de otro color dentro de la noche.
 * - Fuera del anillo, la PREDICCIÓN de sueño (MotorSueño): una luna para la
 *   ventana de noche (fija en el centro de esa ventana, sin moverse aunque
 *   la noche real ya haya pasado) y una nube por cada siesta prevista — esa
 *   nube si se muestra en el centro de la ventana cuando aún no se ha hecho,
 *   o en el centro del tramo REAL una vez hecha, y cambia de relleno según
 *   esté hecha, pendiente o ya perdida. El despertar real del día (si lo
 *   hay) tiene su propia insignia de sol, aparte de la predicción.
 * - Dentro del anillo, un punto pequeño por cada evento suelto (biberón,
 *   pecho, pañal, medicina, baño...), con separación anti-solape.
 */
@Composable
fun RelojDelDia(
    bebe: Bebe,
    registros: List<Registro>,
    dia: LocalDate = LocalDate.now(),
    ahora: Instant = Instant.now(),
    nombreBebe: String? = null,
    alTocarEvento: (Registro) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colores = NubiTheme.colores
    val zona = ZoneId.systemDefault()
    val densidad = LocalDensity.current
    val diaInstante = dia.atTime(12, 0).atZone(zona).toInstant()
    val esHoy = dia == LocalDate.now(zona)

    fun horaDecimal(instante: Instant): Double {
        val t = instante.atZone(zona)
        val h = (t.hour + t.minute / 60.0 + t.second / 3600.0)
        return h.coerceIn(0.0, 24.0)
    }

    // Los intervalos (siesta/noche/despertar) que tocan este día, aunque
    // hayan empezado ayer o sigan en curso (sin "fin" todavía).
    val intervalos = registros.filter { it.tipo.esIntervalo && tocaDia(it, dia, ahora, zona) }
    // El resto de eventos sueltos: solo los que empezaron hoy mismo.
    val eventos = registros.filter {
        !it.tipo.esIntervalo && it.inicio.atZone(zona).toLocalDate() == dia
    }

    val ventanasIdeales: List<VentanaIdeal> =
        MotorSueno.ventanasIdealesDelDia(bebe = bebe, registros = registros, dia = diaInstante, ahora = ahora)
    val despertar: Instant? = MotorSueno.despertarDelDia(registros = registros, dia = diaInstante)

    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1f)) {
        val ladoPx = constraints.maxWidth.toFloat()
        val centro = Offset(ladoPx / 2f, ladoPx / 2f)

        fun dp(valor: Float): Float = with(densidad) { valor.dp.toPx() }

        val grosor = dp(30f)
        val rExterior = ladoPx / 2f - dp(27f)
        val rAnillo = rExterior - grosor / 2f
        val rEventos = rAnillo - grosor / 2f - dp(18f)
        val rIdeales = rExterior + dp(29f)

        fun punto(hora: Double, radio: Float): Offset {
            val angulo = hora / 24.0 * 2 * PI - PI / 2
            return Offset(
                centro.x + (radio * cos(angulo)).toFloat(),
                centro.y + (radio * sin(angulo)).toFloat()
            )
        }

        // -----------------------------------------------------------------
        // Anillo cielo: degradado angular + estrellas/nubes decorativas +
        // doble borde fino.
        // -----------------------------------------------------------------
        Canvas(Modifier.matchParentSize()) {
            val esquina = Offset(centro.x - rAnillo, centro.y - rAnillo)
            val tamanoCaja = Size(rAnillo * 2f, rAnillo * 2f)
            val navy = Color(0xFF14122B)

            rotate(degrees = -90f, pivot = centro) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colorStops = arrayOf(
                            0.00f to navy,
                            0.21f to navy,
                            0.28f to colores.melocoton,
                            0.35f to colores.cielo,
                            0.65f to colores.cielo,
                            0.72f to colores.melocoton,
                            0.80f to navy,
                            1.00f to navy
                        ),
                        center = centro
                    ),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = esquina,
                    size = tamanoCaja,
                    style = Stroke(width = grosor)
                )
            }

            // Estrellas (noche, 21:12 a 04:24) y nubes (día, 08:36 a 16:48),
            // decorativas, dibujadas dentro del propio trazo del anillo.
            dibujarEstrellas(this, ::punto)
            dibujarNubesDecorativas(this, ::punto)

            val colorBorde = colores.lila.copy(alpha = 0.8f)
            drawCircle(color = colorBorde, radius = rAnillo + grosor / 2f, center = centro, style = Stroke(width = 1.5f.dpPx(densidad)))
            drawCircle(color = colorBorde, radius = rAnillo - grosor / 2f, center = centro, style = Stroke(width = 1.5f.dpPx(densidad)))

            // 24 marcas horarias, centradas en el radio del anillo (cruzan
            // el trazo), las de cada 6 horas más largas y marcadas.
            for (h in 0 until 24) {
                val angulo = h / 24.0 * 2 * PI - PI / 2
                val dirX = cos(angulo).toFloat()
                val dirY = sin(angulo).toFloat()
                val marcada = h % 6 == 0
                val largo = if (marcada) dp(11f) else dp(5f)
                val desde = Offset(centro.x + dirX * (rAnillo - largo / 2f), centro.y + dirY * (rAnillo - largo / 2f))
                val hasta = Offset(centro.x + dirX * (rAnillo + largo / 2f), centro.y + dirY * (rAnillo + largo / 2f))
                drawLine(
                    color = Color.White.copy(alpha = if (marcada) 0.95f else 0.35f),
                    start = desde,
                    end = hasta,
                    strokeWidth = if (marcada) dp(2f) else dp(1.2f)
                )
            }

            // Sueños de verdad: el propio sueño (siesta/noche) resaltado con
            // un brillo blanco fino encima; el despertar nocturno (resta
            // sueño) en su color, sin brillo — un tramo distinto dentro de
            // la noche.
            intervalos.filter { it.tipo.esSueno }.forEach { reg ->
                val (desde, hasta) = horasDe(reg, dia, ahora, zona)
                val anguloInicio = (desde / 24.0 * 360.0 - 90.0).toFloat()
                val anguloBarrido = ((hasta - desde) / 24.0 * 360.0).toFloat()
                val color = colorDeTipo(reg.tipo, colores)
                drawArc(
                    color = color, startAngle = anguloInicio, sweepAngle = anguloBarrido,
                    useCenter = false, topLeft = esquina, size = tamanoCaja,
                    style = Stroke(width = grosor - dp(7f), cap = StrokeCap.Round)
                )
                drawArc(
                    color = Color.White.copy(alpha = 0.7f), startAngle = anguloInicio, sweepAngle = anguloBarrido,
                    useCenter = false, topLeft = esquina, size = tamanoCaja,
                    style = Stroke(width = 1.8f.dpPx(densidad), cap = StrokeCap.Round)
                )
            }
            intervalos.filter { it.tipo.restaSueno }.forEach { reg ->
                val (desde, hasta) = horasDe(reg, dia, ahora, zona)
                val anguloInicio = (desde / 24.0 * 360.0 - 90.0).toFloat()
                val anguloBarrido = ((hasta - desde) / 24.0 * 360.0).toFloat()
                drawArc(
                    color = colorDeTipo(reg.tipo, colores), startAngle = anguloInicio, sweepAngle = anguloBarrido,
                    useCenter = false, topLeft = esquina, size = tamanoCaja,
                    style = Stroke(width = grosor - dp(7f), cap = StrokeCap.Round)
                )
            }

            // Aguja de la hora actual, solo en el día de hoy: del centro
            // hasta el borde exterior del anillo.
            if (esHoy) {
                val angulo = horaDecimal(ahora) / 24.0 * 2 * PI - PI / 2
                val punta = Offset(
                    centro.x + (rExterior * cos(angulo)).toFloat(),
                    centro.y + (rExterior * sin(angulo)).toFloat()
                )
                drawLine(color = colores.tinta, start = centro, end = punta, strokeWidth = dp(2.5f), cap = StrokeCap.Round)
                drawCircle(color = colores.tinta, radius = dp(4.5f), center = centro)
            }
        }

        // Números de las horas principales, sobre el borde exterior.
        listOf(0, 3, 6, 9, 12, 15, 18, 21).forEach { hora ->
            val marcada = hora % 6 == 0
            val pos = punto(hora.toDouble(), rExterior + dp(13f))
            Text(
                "%02d".format(hora),
                style = MaterialTheme.typography.labelSmall,
                color = if (marcada) colores.tintaSuave else colores.tintaTenue,
                modifier = Modifier.offsetPx(pos.x - dp(12f), pos.y - dp(8f))
            )
        }

        // -----------------------------------------------------------------
        // Predicción (MotorSueño): luna para la ventana de noche (fija en
        // el centro de la ventana prevista), nube por cada siesta prevista
        // (en el centro del tramo real si ya se hizo).
        // -----------------------------------------------------------------
        ventanasIdeales.forEach { ideal ->
            if (ideal.esNocturna) {
                val desde = horaDecimal(ideal.rangoInicioDesde)
                val hasta = horaDecimal(ideal.rangoInicioHasta)
                val centroH = (desde + hasta) / 2.0
                val pos = punto(centroH, rIdeales)
                val realizada = ideal.realizada
                MarcaLunaIdeal(
                    realizada = realizada,
                    colores = colores,
                    modifier = Modifier.offsetPx(pos.x - dp(15f), pos.y - dp(15f))
                )
            } else {
                val desde = horaDecimal(ideal.arcoDesde)
                val hasta = max(horaDecimal(ideal.arcoHasta), desde + 0.15)
                val centroH = (desde + hasta) / 2.0
                val pos = punto(centroH, rIdeales)
                MarcaSiestaIdeal(
                    realizada = ideal.realizada,
                    perdida = ideal.perdida(ahora),
                    colores = colores,
                    modifier = Modifier.offsetPx(pos.x - dp(12f), pos.y - dp(12f))
                )
            }
        }

        // El despertar real del día (si lo hay), aparte de la predicción.
        despertar?.let { fecha ->
            val pos = punto(horaDecimal(fecha), rIdeales)
            MarcaSol(colores = colores, modifier = Modifier.offsetPx(pos.x - dp(15f), pos.y - dp(15f)))
        }

        // -----------------------------------------------------------------
        // Eventos sueltos dentro del anillo, con separación anti-solape:
        // si dos caen casi encima, primero se reduce el radio, y si ya no
        // hay margen, se desplaza el ángulo.
        // -----------------------------------------------------------------
        val posiciones = posicionarEventos(
            eventos, rEventos, ::horaDecimal, ::punto,
            distanciaMinima = dp(20f), radioFloor = dp(50f), radioPaso = dp(12f)
        )
        posiciones.forEach { (registro, pos) ->
            val claro = registro.tipo != TipoRegistro.MEDICINA
            BadgeIcono(
                tipo = iconoDeTipo(registro.tipo),
                colorFondo = colorDeTipo(registro.tipo, colores),
                colorIcono = if (claro) Color.White else Color.Black,
                tamano = 26.dp,
                tamanoIcono = 13.dp,
                modifier = Modifier
                    .offsetPx(pos.x - dp(13f), pos.y - dp(13f))
                    .shadow(2.dp, CircleShape)
                    .border(2.dp, colores.superficie, CircleShape)
                    .clickable { alTocarEvento(registro) }
            )
        }

        if (intervalos.isEmpty() && eventos.isEmpty()) {
            Text(
                "Sin datos aún",
                style = MaterialTheme.typography.bodySmall,
                color = colores.tintaTenue,
                modifier = Modifier.offsetPx(centro.x - dp(40f), centro.y)
            )
        }
    }
}

@Composable
private fun MarcaLunaIdeal(realizada: Boolean, colores: NubiColores, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier
            .size(30.dp)
            .shadow(3.dp, CircleShape)
            .background(if (realizada) colores.lila else colores.lila.copy(alpha = 0.6f), CircleShape),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Icono(IconoNubi.LUNA, tint = if (realizada) colores.tinta else colores.superficie, tamano = 15.dp)
    }
}

@Composable
private fun MarcaSiestaIdeal(realizada: Boolean, perdida: Boolean, colores: NubiColores, modifier: Modifier = Modifier) {
    val alphaTotal = if (perdida) 0.5f else 1.0f
    androidx.compose.foundation.layout.Box(
        modifier
            .size(24.dp)
            .shadow(2.dp, CircleShape)
            .background(
                color = (if (realizada) colores.menta else colores.superficie).copy(alpha = alphaTotal),
                shape = CircleShape
            )
            .border(2.dp, (if (perdida) colores.melocoton else colores.menta).copy(alpha = alphaTotal), CircleShape),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Icono(
            IconoNubi.NUBE,
            tint = (if (realizada) Color.White else colores.menta).copy(alpha = if (perdida) 0.6f else 1.0f),
            tamano = 12.dp
        )
    }
}

@Composable
private fun MarcaSol(colores: NubiColores, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier
            .size(30.dp)
            .shadow(3.dp, CircleShape)
            .background(colores.mantequilla, CircleShape),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Icono(IconoNubi.SOL, tint = colores.tinta, tamano = 16.dp)
    }
}

/** Estrellas decorativas fijas, de las 21:12 a las 04:24 (parte nocturna del anillo). */
private fun dibujarEstrellas(scope: androidx.compose.ui.graphics.drawscope.DrawScope, punto: (Double, Float) -> Offset) {
    val datos = listOf(
        Triple(21.2, -7f, 2.0f) to 0.9f, Triple(21.9, 5f, 1.4f) to 0.6f, Triple(22.6, -2f, 2.4f) to 1.0f,
        Triple(23.3, 7f, 1.4f) to 0.6f, Triple(23.9, -6f, 1.8f) to 0.8f, Triple(0.6, 4f, 2.2f) to 0.9f,
        Triple(1.4, -5f, 1.4f) to 0.6f, Triple(2.1, 6f, 2.0f) to 0.9f, Triple(2.9, -3f, 1.4f) to 0.7f,
        Triple(3.7, 5f, 2.2f) to 0.9f, Triple(4.4, -6f, 1.4f) to 0.6f
    )
    with(scope) {
        datos.forEach { (datoTriple, opacidad) ->
            val (hora, offsetRadio, tamano) = datoTriple
            val pos = punto(hora, offsetRadio)
            drawCircle(color = Color.White.copy(alpha = opacidad), radius = tamano, center = pos)
        }
    }
}

/** Nubes decorativas fijas, de las 08:36 a las 16:48 (parte diurna del anillo). */
private fun dibujarNubesDecorativas(scope: androidx.compose.ui.graphics.drawscope.DrawScope, punto: (Double, Float) -> Offset) {
    val datos = listOf(
        Triple(8.6, -6f, 1.0f) to 0.75f, Triple(10.3, 5f, 0.75f) to 0.55f, Triple(12.0, -4f, 1.1f) to 0.8f,
        Triple(13.7, 6f, 0.75f) to 0.55f, Triple(15.4, -5f, 1.0f) to 0.7f, Triple(16.8, 4f, 0.7f) to 0.5f
    )
    with(scope) {
        datos.forEach { (datoTriple, opacidad) ->
            val (hora, offsetRadio, escala) = datoTriple
            val pos = punto(hora, offsetRadio)
            val tam = 12f * escala
            val colorNube = Color.White.copy(alpha = opacidad)
            // Capsule (cuerpo) + dos círculos (bultos), como nubeDeco en iOS.
            drawRoundRect(
                color = colorNube,
                topLeft = Offset(pos.x - tam / 2f, pos.y - (tam * 0.45f) / 2f),
                size = Size(tam, tam * 0.45f),
                cornerRadius = CornerRadius(tam * 0.45f / 2f)
            )
            drawCircle(
                color = colorNube, radius = tam * 0.25f,
                center = Offset(pos.x - tam * 0.18f, pos.y - tam * 0.22f)
            )
            drawCircle(
                color = colorNube, radius = tam * 0.2f,
                center = Offset(pos.x + tam * 0.2f, pos.y - tam * 0.15f)
            )
        }
    }
}

/**
 * Posiciona los eventos sueltos evitando que se solapen: primero se intenta
 * el radio base a la hora exacta; si colisiona con uno ya colocado, se
 * reduce el radio, y cuando ya no queda margen de radio, se desplaza el
 * ángulo (en horas) hacia un lado y otro.
 */
private fun posicionarEventos(
    eventos: List<Registro>,
    radioBase: Float,
    horaDecimal: (Instant) -> Double,
    punto: (Double, Float) -> Offset,
    distanciaMinima: Float,
    radioFloor: Float,
    radioPaso: Float
): List<Pair<Registro, Offset>> {
    fun ordenDeTipo(tipo: TipoRegistro): Int = when (tipo) {
        TipoRegistro.BIBERON -> 0
        TipoRegistro.PECHO -> 1
        TipoRegistro.PANAL -> 2
        else -> 3
    }
    fun horaEnvuelta(h: Double): Double {
        val r = h % 24.0
        return if (r < 0) r + 24.0 else r
    }
    fun colisiona(p: Offset, puntos: List<Offset>): Boolean = puntos.any {
        val dx = it.x - p.x; val dy = it.y - p.y
        dx * dx + dy * dy < distanciaMinima * distanciaMinima
    }

    val ordenados = eventos.sortedWith(
        compareBy({ it.inicio }, { ordenDeTipo(it.tipo) })
    )
    val desplazamientos = listOf(0.14, -0.14, 0.28, -0.28, 0.42, -0.42, 0.56, -0.56, 0.70, -0.70)
    val resultado = mutableListOf<Pair<Registro, Offset>>()
    for (registro in ordenados) {
        val h = horaDecimal(registro.inicio)
        var radio = radioBase
        var p = punto(h, radio)
        var intentos = 0
        while (colisiona(p, resultado.map { it.second }) && intentos < 14) {
            if (radio > radioFloor) {
                radio -= radioPaso
                p = punto(h, radio)
            } else {
                val desplazamiento = desplazamientos[intentos % desplazamientos.size]
                p = punto(horaEnvuelta(h + desplazamiento), radio)
            }
            intentos++
        }
        resultado.add(registro to p)
    }
    return resultado
}

/**
 * ¿El intervalo (siesta/noche/despertar) toca este día? Es decir, su rango
 * [inicio, fin ?: ahora] se solapa con el día, aunque haya empezado ayer,
 * siga en curso o termine mañana.
 */
private fun tocaDia(registro: Registro, dia: LocalDate, ahora: Instant, zona: ZoneId): Boolean {
    val inicioDia = dia.atStartOfDay(zona).toInstant()
    val finDia = dia.plusDays(1).atStartOfDay(zona).toInstant()
    val fin = registro.fin ?: ahora
    return registro.inicio.isBefore(finDia) && fin.isAfter(inicioDia)
}

/**
 * El tramo (desde, hasta), en horas decimales de 0 a 24, que le corresponde
 * a "dia" de un intervalo que puede haber empezado ayer, seguir en curso
 * (sin fin todavía, se usa "ahora" como fin provisional) o terminar mañana.
 */
private fun horasDe(registro: Registro, dia: LocalDate, ahora: Instant, zona: ZoneId): Pair<Double, Double> {
    val inicioDia = dia.atStartOfDay(zona).toInstant()
    val finDia = dia.plusDays(1).atStartOfDay(zona).toInstant()
    val inicioClamp = maxOf(registro.inicio, inicioDia)
    val finClamp = minOf(registro.fin ?: ahora, finDia)
    if (!inicioClamp.isBefore(finClamp)) return 0.0 to 0.2

    fun horaDecimal(instante: Instant): Double {
        val t = instante.atZone(zona)
        return t.hour + t.minute / 60.0 + t.second / 3600.0
    }
    val desde = horaDecimal(inicioClamp)
    val hasta = horaDecimal(finClamp)
    return desde to max(hasta, min(desde + 0.2, 24.0))
}

/**
 * El tramo (desde, hasta), en horas decimales, que le corresponde a "dia"
 * de un registro TERMINADO que puede haber empezado el día anterior o
 * terminar el siguiente — por ejemplo, un sueño nocturno de 20:24 a 8:00.
 *
 * Devuelve null cuando el registro no toca ese día en absoluto, así que
 * también sirve para filtrar ("solo los que sí pertenecen a este día").
 * Se mantiene (la usa PantallaDia para la barra de sueño de 24h) junto a
 * `horasDe`, que es la versión para el propio reloj: admite intervalos en
 * curso (sin fin) y nunca devuelve null.
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

private fun Float.dpPx(densidad: androidx.compose.ui.unit.Density): Float = with(densidad) { this@dpPx.dp.toPx() }
