package app.nubi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.nubi.data.Registro
import app.nubi.data.TipoRegistro
import app.nubi.ui.theme.NubiTheme
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * El día como un reloj de 24 horas: medianoche arriba, mediodía abajo.
 * Portado de RelojDelDia.swift.
 *
 * Los sueños se dibujan como arcos sobre el anillo y el resto de eventos
 * como puntos, de forma que de un vistazo se ve cómo ha ido el día.
 *
 * Versión inicial: faltan las ventanas ideales de sueño, la sugerencia de
 * la próxima siesta y poder tocar un evento para abrirlo.
 */
@Composable
fun RelojDelDia(
    registros: List<Registro>,
    modifier: Modifier = Modifier
) {
    val colores = NubiTheme.colores
    val zona = ZoneId.systemDefault()
    val hoy = LocalDate.now(zona)

    // Hora decimal del día (13:30 → 13.5) para situar algo en el anillo.
    fun horaDecimal(instante: java.time.Instant): Double {
        val t = instante.atZone(zona)
        return t.hour + t.minute / 60.0
    }

    val deHoy = registros.filter { it.inicio.atZone(zona).toLocalDate() == hoy }
    val suenos = deHoy.filter { it.tipo.esSueno && it.fin != null }
    val eventos = deHoy.filter { !it.tipo.esSueno }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            val grosor = 30.dp.toPx()
            val lado = minOf(size.width, size.height)
            val radioExterior = lado / 2f - 27.dp.toPx()
            val radioAnillo = radioExterior - grosor / 2f
            val centro = Offset(size.width / 2f, size.height / 2f)

            val esquina = Offset(centro.x - radioAnillo, centro.y - radioAnillo)
            val tamano = Size(radioAnillo * 2f, radioAnillo * 2f)

            // Anillo de fondo: las 24 horas.
            drawArc(
                color = colores.lienzoAlto,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = esquina,
                size = tamano,
                style = Stroke(width = grosor)
            )

            // Cada sueño, un arco. -90º para que medianoche quede arriba.
            suenos.forEach { sueno ->
                val desde = horaDecimal(sueno.inicio)
                val hasta = horaDecimal(sueno.fin!!)
                // Un sueño que cruza la medianoche se recorta a fin de día:
                // la parte de la madrugada ya pertenece al día siguiente.
                val duracion = if (hasta >= desde) hasta - desde else 24.0 - desde

                drawArc(
                    color = if (sueno.tipo == TipoRegistro.NOCHE) colores.lila else colores.menta,
                    startAngle = (desde / 24.0 * 360.0 - 90.0).toFloat(),
                    sweepAngle = (duracion / 24.0 * 360.0).toFloat(),
                    useCenter = false,
                    topLeft = esquina,
                    size = tamano,
                    style = Stroke(width = grosor)
                )
            }

            // El resto de eventos, un punto sobre el anillo.
            eventos.forEach { evento ->
                val angulo = (horaDecimal(evento.inicio) / 24.0 * 2 * PI) - PI / 2
                val punto = Offset(
                    centro.x + (radioAnillo * cos(angulo)).toFloat(),
                    centro.y + (radioAnillo * sin(angulo)).toFloat()
                )
                drawCircle(
                    color = colorDeEvento(evento.tipo, colores.melocoton, colores.cielo, colores.rosa),
                    radius = 5.dp.toPx(),
                    center = punto
                )
            }

            // Marcas de las cuatro horas principales (0, 6, 12, 18).
            listOf(0, 6, 12, 18).forEach { hora ->
                val angulo = (hora / 24.0 * 2 * PI) - PI / 2
                val desde = Offset(
                    centro.x + ((radioAnillo + grosor / 2 + 6.dp.toPx()) * cos(angulo)).toFloat(),
                    centro.y + ((radioAnillo + grosor / 2 + 6.dp.toPx()) * sin(angulo)).toFloat()
                )
                val hasta = Offset(
                    centro.x + ((radioAnillo + grosor / 2 + 14.dp.toPx()) * cos(angulo)).toFloat(),
                    centro.y + ((radioAnillo + grosor / 2 + 14.dp.toPx()) * sin(angulo)).toFloat()
                )
                drawLine(color = colores.separador, start = desde, end = hasta, strokeWidth = 2.dp.toPx())
            }
        }

        if (deHoy.isEmpty()) {
            Text(
                "Sin datos aún",
                style = MaterialTheme.typography.bodySmall,
                color = colores.tintaTenue
            )
        }
    }
}

private fun colorDeEvento(
    tipo: TipoRegistro,
    melocoton: Color,
    cielo: Color,
    rosa: Color
): Color = when (tipo) {
    TipoRegistro.BIBERON, TipoRegistro.PECHO -> melocoton
    TipoRegistro.PANAL -> cielo
    else -> rosa
}
