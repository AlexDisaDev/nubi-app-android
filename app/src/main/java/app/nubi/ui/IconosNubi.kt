package app.nubi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Los dibujos propios de Nubi (sustituyen a los puntos de color de antes).
 *
 * Son deliberadamente simples — trazo fino, formas redondeadas — para que
 * se acerquen al estilo de Ilustraciones.swift mientras no se porta esa
 * librería de verdad. Cada uno se dibuja a mano con Canvas: no hay ningún
 * paquete de iconos de por medio.
 */
enum class IconoNubi {
    NUBE, RELOJ, CRECE, MALETIN, LIBRETA,
    BIBERON, CRONOMETRO, ONDAS,
    SOL, LUNA, OJO_ABIERTO, CORAZON, GOTA, BOMBILLA, CONTROLES, CHEVRON,
    MEDICINA, BANO, EQUIS,
    MAS, BASCULA, REGLA, PAPELERA,
    JERINGA, CALENDARIO, BOTIQUIN, CHECK,
    LAPIZ, ESTRELLA, CACA,
    BRAZOS, CUNA, CARRITO, COCHE,
    CONTENTO, MOLESTO,
    CANDADO,
    CAMARA, GALERIA, PULGAR_ARRIBA, PULGAR_ABAJO, PLAY, PAUSA
}

@Composable
fun Icono(
    tipo: IconoNubi,
    tint: Color,
    modifier: Modifier = Modifier,
    tamano: Dp = 22.dp
) {
    Canvas(modifier.size(tamano)) {
        when (tipo) {
            IconoNubi.NUBE -> dibujarNube(tint)
            IconoNubi.RELOJ -> dibujarReloj(tint)
            IconoNubi.CRECE -> dibujarCrece(tint)
            IconoNubi.MALETIN -> dibujarMaletin(tint)
            IconoNubi.LIBRETA -> dibujarLibreta(tint)
            IconoNubi.BIBERON -> dibujarBiberon(tint)
            IconoNubi.CRONOMETRO -> dibujarCronometro(tint)
            IconoNubi.ONDAS -> dibujarOndas(tint)
            IconoNubi.SOL -> dibujarSol(tint)
            IconoNubi.LUNA -> dibujarLuna(tint)
            IconoNubi.OJO_ABIERTO -> dibujarOjoAbierto(tint)
            IconoNubi.CORAZON -> dibujarCorazon(tint)
            IconoNubi.GOTA -> dibujarGota(tint)
            IconoNubi.BOMBILLA -> dibujarBombilla(tint)
            IconoNubi.CONTROLES -> dibujarControles(tint)
            IconoNubi.CHEVRON -> dibujarChevron(tint)
            IconoNubi.MEDICINA -> dibujarMedicina(tint)
            IconoNubi.BANO -> dibujarBano(tint)
            IconoNubi.EQUIS -> dibujarEquis(tint)
            IconoNubi.MAS -> dibujarMas(tint)
            IconoNubi.BASCULA -> dibujarBascula(tint)
            IconoNubi.REGLA -> dibujarRegla(tint)
            IconoNubi.PAPELERA -> dibujarPapelera(tint)
            IconoNubi.JERINGA -> dibujarJeringa(tint)
            IconoNubi.CALENDARIO -> dibujarCalendario(tint)
            IconoNubi.BOTIQUIN -> dibujarBotiquin(tint)
            IconoNubi.CHECK -> dibujarCheck(tint)
            IconoNubi.LAPIZ -> dibujarLapiz(tint)
            IconoNubi.ESTRELLA -> dibujarEstrella(tint)
            IconoNubi.CACA -> dibujarCaca(tint)
            IconoNubi.BRAZOS -> dibujarBrazos(tint)
            IconoNubi.CUNA -> dibujarCuna(tint)
            IconoNubi.CARRITO -> dibujarCarrito(tint)
            IconoNubi.COCHE -> dibujarCoche(tint)
            IconoNubi.CONTENTO -> dibujarContento(tint)
            IconoNubi.MOLESTO -> dibujarMolesto(tint)
            IconoNubi.CANDADO -> dibujarCandado(tint)
            IconoNubi.CAMARA -> dibujarCamara(tint)
            IconoNubi.GALERIA -> dibujarGaleria(tint)
            IconoNubi.PULGAR_ARRIBA -> dibujarPulgarArriba(tint)
            IconoNubi.PULGAR_ABAJO -> dibujarPulgarAbajo(tint)
            IconoNubi.PLAY -> dibujarPlay(tint)
            IconoNubi.PAUSA -> dibujarPausa(tint)
        }
    }
}

/** Icono dentro de un círculo de color — el "badge" que acompaña cada fila. */
@Composable
fun BadgeIcono(
    tipo: IconoNubi,
    colorFondo: Color,
    colorIcono: Color,
    tamano: Dp = 40.dp,
    tamanoIcono: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(tamano)
            .clip(CircleShape)
            .background(colorFondo),
        contentAlignment = Alignment.Center
    ) {
        Icono(tipo, tint = colorIcono, tamano = tamanoIcono)
    }
}

// ---------------------------------------------------------------------
// Dibujos. Todos trabajan sobre "size" (el tamaño del Canvas, en píxeles)
// y asumen un lienzo cuadrado.
// ---------------------------------------------------------------------

private fun DrawScope.dibujarNube(color: Color) {
    val w = size.width
    val h = size.height
    // Un rectángulo redondeado como base y tres círculos encima: al ser
    // del mismo color sólido, las uniones no se notan y queda la nube.
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.06f, h * 0.46f),
        size = Size(w * 0.88f, h * 0.38f),
        cornerRadius = CornerRadius(h * 0.19f)
    )
    drawCircle(color, radius = h * 0.26f, center = Offset(w * 0.32f, h * 0.44f))
    drawCircle(color, radius = h * 0.33f, center = Offset(w * 0.57f, h * 0.34f))
    drawCircle(color, radius = h * 0.22f, center = Offset(w * 0.78f, h * 0.48f))
}

private fun DrawScope.dibujarReloj(color: Color) {
    val r = size.minDimension * 0.4f
    val c = Offset(size.width / 2f, size.height / 2f)
    val grosor = size.minDimension * 0.09f
    drawCircle(color, radius = r, center = c, style = Stroke(width = grosor))
    drawLine(
        color, start = c, end = Offset(c.x, c.y - r * 0.55f),
        strokeWidth = grosor, cap = StrokeCap.Round
    )
    drawLine(
        color, start = c, end = Offset(c.x + r * 0.42f, c.y + r * 0.12f),
        strokeWidth = grosor, cap = StrokeCap.Round
    )
}

private fun DrawScope.dibujarCrece(color: Color) {
    val w = size.width
    val h = size.height
    val base = h * 0.86f
    val anchoBarra = w * 0.18f
    val hueco = w * 0.12f
    val alturas = listOf(0.34f, 0.6f, 0.86f)
    var x = w * 0.12f
    alturas.forEach { f ->
        val altura = h * f
        drawRoundRect(
            color = color,
            topLeft = Offset(x, base - altura),
            size = Size(anchoBarra, altura),
            cornerRadius = CornerRadius(anchoBarra * 0.4f)
        )
        x += anchoBarra + hueco
    }
}

private fun DrawScope.dibujarMaletin(color: Color) {
    val w = size.width
    val h = size.height
    val grosor = w * 0.09f
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.12f, h * 0.34f),
        size = Size(w * 0.76f, h * 0.5f),
        cornerRadius = CornerRadius(w * 0.1f),
        style = Stroke(width = grosor)
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.36f, h * 0.16f),
        size = Size(w * 0.28f, h * 0.2f),
        cornerRadius = CornerRadius(w * 0.07f),
        style = Stroke(width = grosor * 0.85f)
    )
    drawLine(
        color, Offset(w * 0.5f, h * 0.48f), Offset(w * 0.5f, h * 0.7f),
        strokeWidth = grosor, cap = StrokeCap.Round
    )
    drawLine(
        color, Offset(w * 0.39f, h * 0.59f), Offset(w * 0.61f, h * 0.59f),
        strokeWidth = grosor, cap = StrokeCap.Round
    )
}

private fun DrawScope.dibujarLibreta(color: Color) {
    val w = size.width
    val h = size.height
    val grosor = w * 0.08f
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.18f, h * 0.1f),
        size = Size(w * 0.64f, h * 0.8f),
        cornerRadius = CornerRadius(w * 0.08f),
        style = Stroke(width = grosor)
    )
    listOf(0.36f, 0.54f, 0.72f).forEach { fy ->
        drawLine(
            color, Offset(w * 0.3f, h * fy), Offset(w * 0.7f, h * fy),
            strokeWidth = grosor * 0.75f, cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.dibujarBiberon(color: Color) {
    val w = size.width
    val h = size.height
    // Tetina
    val tetina = Path().apply {
        moveTo(w * 0.41f, h * 0.06f)
        lineTo(w * 0.59f, h * 0.06f)
        lineTo(w * 0.55f, h * 0.2f)
        lineTo(w * 0.45f, h * 0.2f)
        close()
    }
    drawPath(tetina, color)
    // Cuello
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.4f, h * 0.18f),
        size = Size(w * 0.2f, h * 0.14f),
        cornerRadius = CornerRadius(w * 0.03f)
    )
    // Cuerpo
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.24f, h * 0.3f),
        size = Size(w * 0.52f, h * 0.62f),
        cornerRadius = CornerRadius(w * 0.15f)
    )
    // Rayas de medida, más claras, sobre el cuerpo
    listOf(0.5f, 0.64f, 0.78f).forEach { fy ->
        drawLine(
            color.copy(alpha = 0.4f),
            Offset(w * 0.32f, h * fy), Offset(w * 0.68f, h * fy),
            strokeWidth = w * 0.05f, cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.dibujarCronometro(color: Color) {
    val w = size.width
    val h = size.height
    val r = size.minDimension * 0.34f
    val c = Offset(w / 2f, h * 0.58f)
    val grosor = size.minDimension * 0.09f
    drawCircle(color, radius = r, center = c, style = Stroke(width = grosor))
    drawLine(
        color, Offset(c.x, c.y - r - h * 0.1f), Offset(c.x, c.y - r),
        strokeWidth = grosor, cap = StrokeCap.Round
    )
    drawLine(
        color, Offset(c.x - w * 0.1f, h * 0.06f), Offset(c.x + w * 0.1f, h * 0.06f),
        strokeWidth = grosor, cap = StrokeCap.Round
    )
    drawLine(
        color, c, Offset(c.x + r * 0.45f, c.y - r * 0.45f),
        strokeWidth = grosor * 0.85f, cap = StrokeCap.Round
    )
}

private fun DrawScope.dibujarOndas(color: Color) {
    val w = size.width
    val h = size.height
    val alturas = listOf(0.38f, 0.72f, 1f, 0.52f)
    val anchoBarra = w * 0.14f
    val hueco = w * 0.08f
    val totalAncho = alturas.size * anchoBarra + (alturas.size - 1) * hueco
    var x = (w - totalAncho) / 2f
    alturas.forEach { f ->
        val altura = h * 0.78f * f
        drawRoundRect(
            color = color,
            topLeft = Offset(x, h * 0.92f - altura),
            size = Size(anchoBarra, altura),
            cornerRadius = CornerRadius(anchoBarra * 0.4f)
        )
        x += anchoBarra + hueco
    }
}

private fun DrawScope.dibujarSol(color: Color) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.minDimension * 0.22f
    drawCircle(color, radius = r, center = c)
    val desde = r * 1.35f
    val hasta = r * 1.95f
    val grosor = size.minDimension * 0.08f
    for (i in 0 until 8) {
        val angulo = i * (2.0 * PI / 8.0)
        val p1 = Offset(
            c.x + (desde * cos(angulo)).toFloat(),
            c.y + (desde * sin(angulo)).toFloat()
        )
        val p2 = Offset(
            c.x + (hasta * cos(angulo)).toFloat(),
            c.y + (hasta * sin(angulo)).toFloat()
        )
        drawLine(color, p1, p2, strokeWidth = grosor, cap = StrokeCap.Round)
    }
}

private fun DrawScope.dibujarLuna(color: Color) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.minDimension * 0.36f
    val p1 = Path().apply { addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r)) }
    val desplazado = Offset(c.x + r * 0.55f, c.y - r * 0.3f)
    val r2 = r * 0.95f
    val p2 = Path().apply {
        addOval(Rect(desplazado.x - r2, desplazado.y - r2, desplazado.x + r2, desplazado.y + r2))
    }
    val resultado = Path()
    resultado.op(p1, p2, PathOperation.Difference)
    drawPath(resultado, color)
}

private fun DrawScope.dibujarCorazon(color: Color) {
    val w = size.width
    val h = size.height
    val camino = Path().apply {
        moveTo(w * 0.5f, h * 0.84f)
        cubicTo(w * 0.06f, h * 0.56f, w * 0.1f, h * 0.16f, w * 0.5f, h * 0.34f)
        cubicTo(w * 0.9f, h * 0.16f, w * 0.94f, h * 0.56f, w * 0.5f, h * 0.84f)
        close()
    }
    drawPath(camino, color)
}

private fun DrawScope.dibujarGota(color: Color) {
    val w = size.width
    val h = size.height
    val camino = Path().apply {
        moveTo(w * 0.5f, h * 0.08f)
        cubicTo(w * 0.86f, h * 0.5f, w * 0.78f, h * 0.92f, w * 0.5f, h * 0.92f)
        cubicTo(w * 0.22f, h * 0.92f, w * 0.14f, h * 0.5f, w * 0.5f, h * 0.08f)
        close()
    }
    drawPath(camino, color)
}

private fun DrawScope.dibujarBombilla(color: Color) {
    val w = size.width
    val h = size.height
    drawCircle(color, radius = w * 0.28f, center = Offset(w * 0.5f, h * 0.4f))
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.38f, h * 0.62f),
        size = Size(w * 0.24f, h * 0.14f),
        cornerRadius = CornerRadius(w * 0.04f)
    )
    listOf(0.82f, 0.9f).forEach { fy ->
        drawLine(
            color, Offset(w * 0.4f, h * fy), Offset(w * 0.6f, h * fy),
            strokeWidth = w * 0.05f, cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.dibujarControles(color: Color) {
    val w = size.width
    val h = size.height
    val grosor = w * 0.1f
    val ejes = listOf(0.28f, 0.52f, 0.76f)
    val nudos = listOf(0.38f, 0.66f, 0.28f)
    ejes.forEachIndexed { i, fx ->
        drawLine(
            color, Offset(w * fx, h * 0.12f), Offset(w * fx, h * 0.88f),
            strokeWidth = grosor * 0.6f, cap = StrokeCap.Round
        )
        drawCircle(color, radius = w * 0.09f, center = Offset(w * fx, h * nudos[i]))
    }
}

/** Bote de medicina: tapón sólido arriba, cuerpo con una cruz — se lee
 *  como "medicina" de un vistazo, a diferencia del dibujo anterior
 *  (una caja con rayas sueltas que a tamaño pequeño parecía una nota
 *  musical). */
private fun DrawScope.dibujarMedicina(color: Color) {
    val w = size.width
    val h = size.height
    val grosor = w * 0.09f
    // Tapón, sólido
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.38f, h * 0.06f),
        size = Size(w * 0.24f, h * 0.16f),
        cornerRadius = CornerRadius(w * 0.04f)
    )
    // Cuerpo del bote
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.22f, h * 0.22f),
        size = Size(w * 0.56f, h * 0.7f),
        cornerRadius = CornerRadius(w * 0.14f),
        style = Stroke(width = grosor)
    )
    // Cruz en la etiqueta
    drawLine(
        color, Offset(w * 0.5f, h * 0.46f), Offset(w * 0.5f, h * 0.68f),
        strokeWidth = grosor * 0.8f, cap = StrokeCap.Round
    )
    drawLine(
        color, Offset(w * 0.39f, h * 0.57f), Offset(w * 0.61f, h * 0.57f),
        strokeWidth = grosor * 0.8f, cap = StrokeCap.Round
    )
}

/** Bañera sólida con burbujas encima — más legible a tamaño pequeño que
 *  el contorno fino de antes, que a 18dp se confundía con un globo de
 *  chat. El relleno sólido es el mismo lenguaje que NUBE, GOTA o
 *  CORAZON, los iconos que mejor se leen de todo el set. */
private fun DrawScope.dibujarBano(color: Color) {
    val w = size.width
    val h = size.height
    // Base de la bañera
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.1f, h * 0.5f),
        size = Size(w * 0.8f, h * 0.36f),
        cornerRadius = CornerRadius(w * 0.18f)
    )
    // Patas
    drawLine(
        color, Offset(w * 0.22f, h * 0.84f), Offset(w * 0.22f, h * 0.94f),
        strokeWidth = w * 0.08f, cap = StrokeCap.Round
    )
    drawLine(
        color, Offset(w * 0.78f, h * 0.84f), Offset(w * 0.78f, h * 0.94f),
        strokeWidth = w * 0.08f, cap = StrokeCap.Round
    )
    // Burbujas de jabón sobre el agua
    drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.32f, h * 0.3f))
    drawCircle(color, radius = w * 0.11f, center = Offset(w * 0.54f, h * 0.18f))
    drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.7f, h * 0.32f))
}

/** Ojo abierto — "se despertó": distinto de LUNA para que "Sueño
 *  nocturno" y "Despertar nocturno" no se vean como el mismo icono en
 *  la botonera. */
private fun DrawScope.dibujarOjoAbierto(color: Color) {
    val w = size.width
    val h = size.height
    val grosor = w * 0.09f
    val c = Offset(w * 0.5f, h * 0.5f)
    val ojo = Path().apply {
        moveTo(w * 0.08f, h * 0.5f)
        cubicTo(w * 0.24f, h * 0.22f, w * 0.76f, h * 0.22f, w * 0.92f, h * 0.5f)
        cubicTo(w * 0.76f, h * 0.78f, w * 0.24f, h * 0.78f, w * 0.08f, h * 0.5f)
        close()
    }
    drawPath(ojo, color, style = Stroke(width = grosor, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(color, radius = w * 0.14f, center = c)
}

private fun DrawScope.dibujarEquis(color: Color) {
    val w = size.width
    val h = size.height
    val grosor = w * 0.12f
    drawLine(
        color, Offset(w * 0.26f, h * 0.26f), Offset(w * 0.74f, h * 0.74f),
        strokeWidth = grosor, cap = StrokeCap.Round
    )
    drawLine(
        color, Offset(w * 0.74f, h * 0.26f), Offset(w * 0.26f, h * 0.74f),
        strokeWidth = grosor, cap = StrokeCap.Round
    )
}

private fun DrawScope.dibujarMas(color: Color) {
    val w = size.width
    val h = size.height
    val grosor = w * 0.12f
    drawLine(
        color, Offset(w * 0.5f, h * 0.2f), Offset(w * 0.5f, h * 0.8f),
        strokeWidth = grosor, cap = StrokeCap.Round
    )
    drawLine(
        color, Offset(w * 0.2f, h * 0.5f), Offset(w * 0.8f, h * 0.5f),
        strokeWidth = grosor, cap = StrokeCap.Round
    )
}

/** Báscula: una base redondeada y una esfera con una aguja, para "peso". */
private fun DrawScope.dibujarBascula(color: Color) {
    val w = size.width
    val h = size.height
    val grosor = w * 0.08f
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.1f, h * 0.56f),
        size = Size(w * 0.8f, h * 0.34f),
        cornerRadius = CornerRadius(w * 0.1f),
        style = Stroke(width = grosor)
    )
    val c = Offset(w * 0.5f, h * 0.36f)
    val r = w * 0.26f
    drawCircle(color, radius = r, center = c, style = Stroke(width = grosor * 0.85f))
    drawLine(
        color, c, Offset(c.x + r * 0.5f, c.y - r * 0.4f),
        strokeWidth = grosor * 0.7f, cap = StrokeCap.Round
    )
}

/** Regla inclinada con marcas, para "estatura". */
private fun DrawScope.dibujarRegla(color: Color) {
    val w = size.width
    val h = size.height
    val grosor = w * 0.09f
    rotate(degrees = -45f) {
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.12f, h * 0.4f),
            size = Size(w * 0.76f, h * 0.2f),
            cornerRadius = CornerRadius(w * 0.04f),
            style = Stroke(width = grosor)
        )
        listOf(0.28f, 0.44f, 0.6f, 0.76f).forEach { fx ->
            drawLine(
                color,
                Offset(w * fx, h * 0.4f),
                Offset(w * fx, h * 0.4f + h * 0.1f),
                strokeWidth = grosor * 0.7f
            )
        }
    }
}

/** Papelera: cubo con tapa y tres rayas dentro, para "borrar". */
private fun DrawScope.dibujarPapelera(color: Color) {
    val w = size.width
    val h = size.height
    val grosor = w * 0.08f
    drawLine(
        color, Offset(w * 0.22f, h * 0.26f), Offset(w * 0.78f, h * 0.26f),
        strokeWidth = grosor, cap = StrokeCap.Round
    )
    drawLine(
        color, Offset(w * 0.4f, h * 0.18f), Offset(w * 0.6f, h * 0.18f),
        strokeWidth = grosor, cap = StrokeCap.Round
    )
    drawPath(
        Path().apply {
            moveTo(w * 0.28f, h * 0.3f)
            lineTo(w * 0.34f, h * 0.88f)
            lineTo(w * 0.66f, h * 0.88f)
            lineTo(w * 0.72f, h * 0.3f)
        },
        color,
        style = Stroke(width = grosor * 0.85f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    listOf(0.42f, 0.5f, 0.58f).forEach { fx ->
        drawLine(
            color, Offset(w * fx, h * 0.4f), Offset(w * fx, h * 0.76f),
            strokeWidth = grosor * 0.55f, cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.dibujarChevron(color: Color) {
    val w = size.width
    val h = size.height
    val camino = Path().apply {
        moveTo(w * 0.34f, h * 0.18f)
        lineTo(w * 0.7f, h * 0.5f)
        lineTo(w * 0.34f, h * 0.82f)
    }
    drawPath(
        camino, color,
        style = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

/** Jeringa: cuerpo, émbolo y una gota en la aguja — para las vacunas. */
private fun DrawScope.dibujarJeringa(color: Color) {
    val w = size.width
    val h = size.height
    val grosor = w * 0.08f
    rotate(degrees = 45f) {
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.3f, h * 0.22f),
            size = Size(w * 0.4f, h * 0.4f),
            cornerRadius = CornerRadius(w * 0.05f),
            style = Stroke(width = grosor)
        )
        drawLine(
            color, Offset(w * 0.5f, h * 0.14f), Offset(w * 0.5f, h * 0.22f),
            strokeWidth = grosor, cap = StrokeCap.Round
        )
        drawLine(
            color, Offset(w * 0.5f, h * 0.62f), Offset(w * 0.5f, h * 0.9f),
            strokeWidth = grosor * 0.7f, cap = StrokeCap.Round
        )
        drawLine(
            color, Offset(w * 0.38f, h * 0.14f), Offset(w * 0.62f, h * 0.14f),
            strokeWidth = grosor, cap = StrokeCap.Round
        )
        listOf(0.34f, 0.44f).forEach { fy ->
            drawLine(
                color, Offset(w * 0.3f, h * fy), Offset(w * 0.38f, h * fy),
                strokeWidth = grosor * 0.6f, cap = StrokeCap.Round
            )
        }
    }
}

/** Calendario: marco con dos anillas arriba y una línea de cabecera. */
private fun DrawScope.dibujarCalendario(color: Color) {
    val w = size.width
    val h = size.height
    val grosor = w * 0.08f
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.14f, h * 0.2f),
        size = Size(w * 0.72f, h * 0.66f),
        cornerRadius = CornerRadius(w * 0.1f),
        style = Stroke(width = grosor)
    )
    drawLine(
        color, Offset(w * 0.14f, h * 0.4f), Offset(w * 0.86f, h * 0.4f),
        strokeWidth = grosor * 0.8f
    )
    listOf(0.32f, 0.68f).forEach { fx ->
        drawLine(
            color, Offset(w * fx, h * 0.12f), Offset(w * fx, h * 0.28f),
            strokeWidth = grosor, cap = StrokeCap.Round
        )
    }
    drawCircle(color, radius = w * 0.045f, center = Offset(w * 0.32f, h * 0.58f))
    drawCircle(color, radius = w * 0.045f, center = Offset(w * 0.5f, h * 0.58f))
    drawCircle(color, radius = w * 0.045f, center = Offset(w * 0.68f, h * 0.58f))
}

/** Botiquín: caja con una cruz — para enfermedades / medicación. */
private fun DrawScope.dibujarBotiquin(color: Color) {
    val w = size.width
    val h = size.height
    val grosor = w * 0.09f
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.14f, h * 0.3f),
        size = Size(w * 0.72f, h * 0.56f),
        cornerRadius = CornerRadius(w * 0.1f),
        style = Stroke(width = grosor)
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.36f, h * 0.18f),
        size = Size(w * 0.28f, h * 0.16f),
        cornerRadius = CornerRadius(w * 0.04f),
        style = Stroke(width = grosor * 0.85f)
    )
    drawLine(
        color, Offset(w * 0.5f, h * 0.46f), Offset(w * 0.5f, h * 0.7f),
        strokeWidth = grosor * 0.85f, cap = StrokeCap.Round
    )
    drawLine(
        color, Offset(w * 0.38f, h * 0.58f), Offset(w * 0.62f, h * 0.58f),
        strokeWidth = grosor * 0.85f, cap = StrokeCap.Round
    )
}

/** Marca de verificación — para casillas marcadas. */
private fun DrawScope.dibujarCheck(color: Color) {
    val w = size.width
    val h = size.height
    val camino = Path().apply {
        moveTo(w * 0.2f, h * 0.52f)
        lineTo(w * 0.42f, h * 0.74f)
        lineTo(w * 0.8f, h * 0.3f)
    }
    drawPath(
        camino, color,
        style = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

/** Lápiz: cuerpo en diagonal y punta — para escribir/editar. */
private fun DrawScope.dibujarLapiz(color: Color) {
    val w = size.width
    val h = size.height
    rotate(degrees = 45f) {
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.42f, h * 0.1f),
            size = Size(w * 0.16f, h * 0.58f),
            cornerRadius = CornerRadius(w * 0.04f),
            style = Stroke(width = w * 0.08f)
        )
        val punta = Path().apply {
            moveTo(w * 0.41f, h * 0.68f)
            lineTo(w * 0.59f, h * 0.68f)
            lineTo(w * 0.5f, h * 0.88f)
            close()
        }
        drawPath(punta, color)
    }
}

/** Caca: tres vueltas apiladas, cada una más pequeña hacia arriba — el
 *  remolino típico, sin más detalle que eso. */
private fun DrawScope.dibujarCaca(color: Color) {
    val w = size.width
    val h = size.height
    listOf(
        Triple(0.5f, 0.78f, 0.3f),
        Triple(0.5f, 0.56f, 0.24f),
        Triple(0.5f, 0.38f, 0.17f)
    ).forEach { (fx, fy, fr) ->
        drawCircle(color, radius = w * fr, center = Offset(w * fx, h * fy))
    }
}

/** Estrella de cinco puntas — para marcar un hito. */
private fun DrawScope.dibujarEstrella(color: Color) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.5f
    val rExterior = w * 0.46f
    val rInterior = w * 0.19f
    val camino = Path()
    for (i in 0 until 10) {
        val angulo = (PI / 5 * i - PI / 2).toFloat()
        val r = if (i % 2 == 0) rExterior else rInterior
        val x = cx + r * cos(angulo)
        val y = cy + r * sin(angulo)
        if (i == 0) camino.moveTo(x, y) else camino.lineTo(x, y)
    }
    camino.close()
    drawPath(camino, color)
}

// =====================================================================
// "¿Dónde se durmió?" (HojaSiestaDia, ver LugarSueno en Modelos.kt) y
// "¿Cómo lo llevó?" del Baño — iconos nuevos de esta tanda.
// =====================================================================

/** Dos brazos en forma de cuna, sosteniendo al bebé. */
private fun DrawScope.dibujarBrazos(color: Color) {
    val w = size.width
    val h = size.height
    val grosor = w * 0.1f
    val camino = Path().apply {
        moveTo(w * 0.14f, h * 0.28f)
        cubicTo(w * 0.1f, h * 0.68f, w * 0.28f, h * 0.84f, w * 0.5f, h * 0.84f)
        cubicTo(w * 0.72f, h * 0.84f, w * 0.9f, h * 0.68f, w * 0.86f, h * 0.28f)
    }
    drawPath(camino, color, style = Stroke(width = grosor, cap = StrokeCap.Round))
}

/** Una cuna sencilla: colchón, barrotes y dos patas. */
private fun DrawScope.dibujarCuna(color: Color) {
    val w = size.width
    val h = size.height
    val grosor = w * 0.07f
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.16f, h * 0.36f),
        size = Size(w * 0.68f, h * 0.14f),
        cornerRadius = CornerRadius(w * 0.03f)
    )
    listOf(0.22f, 0.34f, 0.46f, 0.58f, 0.7f, 0.78f).forEach { fx ->
        drawLine(
            color, Offset(w * fx, h * 0.14f), Offset(w * fx, h * 0.36f),
            strokeWidth = grosor * 0.55f, cap = StrokeCap.Round
        )
    }
    drawLine(color, Offset(w * 0.22f, h * 0.5f), Offset(w * 0.16f, h * 0.86f), strokeWidth = grosor, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.78f, h * 0.5f), Offset(w * 0.84f, h * 0.86f), strokeWidth = grosor, cap = StrokeCap.Round)
}

/** Un carrito de paseo: capota, cuerpo, manillar y dos ruedas. */
private fun DrawScope.dibujarCarrito(color: Color) {
    val w = size.width
    val h = size.height
    val grosor = w * 0.07f
    val capota = Path().apply {
        moveTo(w * 0.26f, h * 0.42f)
        cubicTo(w * 0.28f, h * 0.16f, w * 0.64f, h * 0.14f, w * 0.68f, h * 0.4f)
        close()
    }
    drawPath(capota, color)
    drawLine(color, Offset(w * 0.28f, h * 0.42f), Offset(w * 0.7f, h * 0.56f), strokeWidth = grosor, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.7f, h * 0.56f), Offset(w * 0.3f, h * 0.56f), strokeWidth = grosor, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.7f, h * 0.56f), Offset(w * 0.88f, h * 0.28f), strokeWidth = grosor * 0.8f, cap = StrokeCap.Round)
    drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.36f, h * 0.84f))
    drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.64f, h * 0.84f))
}

/** Un coche visto de lado: techo, carrocería y dos ruedas. */
private fun DrawScope.dibujarCoche(color: Color) {
    val w = size.width
    val h = size.height
    val techo = Path().apply {
        moveTo(w * 0.28f, h * 0.46f)
        cubicTo(w * 0.32f, h * 0.24f, w * 0.68f, h * 0.24f, w * 0.72f, h * 0.46f)
        close()
    }
    drawPath(techo, color)
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.1f, h * 0.44f),
        size = Size(w * 0.8f, h * 0.24f),
        cornerRadius = CornerRadius(w * 0.08f)
    )
    drawCircle(color, radius = w * 0.1f, center = Offset(w * 0.3f, h * 0.78f))
    drawCircle(color, radius = w * 0.1f, center = Offset(w * 0.7f, h * 0.78f))
}

/** Carita sonriente — "Contento" en la hoja de Baño. */
private fun DrawScope.dibujarContento(color: Color) {
    val w = size.width
    val h = size.height
    val grosor = w * 0.08f
    drawCircle(color, radius = w * 0.4f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(width = grosor))
    drawCircle(color, radius = w * 0.045f, center = Offset(w * 0.36f, h * 0.42f))
    drawCircle(color, radius = w * 0.045f, center = Offset(w * 0.64f, h * 0.42f))
    val sonrisa = Path().apply {
        moveTo(w * 0.32f, h * 0.56f)
        cubicTo(w * 0.4f, h * 0.72f, w * 0.6f, h * 0.72f, w * 0.68f, h * 0.56f)
    }
    drawPath(sonrisa, color, style = Stroke(width = grosor * 0.7f, cap = StrokeCap.Round))
}

/** Carita disgustada — "Molesto" en la hoja de Baño. */
private fun DrawScope.dibujarMolesto(color: Color) {
    val w = size.width
    val h = size.height
    val grosor = w * 0.08f
    drawCircle(color, radius = w * 0.4f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(width = grosor))
    drawCircle(color, radius = w * 0.045f, center = Offset(w * 0.36f, h * 0.44f))
    drawCircle(color, radius = w * 0.045f, center = Offset(w * 0.64f, h * 0.44f))
    val mueca = Path().apply {
        moveTo(w * 0.32f, h * 0.7f)
        cubicTo(w * 0.4f, h * 0.58f, w * 0.6f, h * 0.58f, w * 0.68f, h * 0.7f)
    }
    drawPath(mueca, color, style = Stroke(width = grosor * 0.7f, cap = StrokeCap.Round))
}

/** Candado — insignia de "esto es premium" sobre un botón bloqueado. */
private fun DrawScope.dibujarCandado(color: Color) {
    val w = size.width
    val h = size.height
    val grosor = w * 0.1f
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.2f, h * 0.46f),
        size = Size(w * 0.6f, h * 0.42f),
        cornerRadius = CornerRadius(w * 0.08f),
        style = Stroke(width = grosor)
    )
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(w * 0.3f, h * 0.14f),
        size = Size(w * 0.4f, h * 0.4f),
        style = Stroke(width = grosor * 0.85f, cap = StrokeCap.Round)
    )
    drawCircle(color, radius = w * 0.05f, center = Offset(w * 0.5f, h * 0.62f))
    drawLine(
        color, Offset(w * 0.5f, h * 0.64f), Offset(w * 0.5f, h * 0.78f),
        strokeWidth = grosor * 0.6f, cap = StrokeCap.Round
    )
}

/** Cámara: cuerpo redondeado, visor y un pequeño flash arriba — "Hacer foto". */
private fun DrawScope.dibujarCamara(color: Color) {
    val w = size.width
    val h = size.height
    val grosor = w * 0.08f
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.12f, h * 0.32f),
        size = Size(w * 0.76f, h * 0.54f),
        cornerRadius = CornerRadius(w * 0.12f),
        style = Stroke(width = grosor)
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.38f, h * 0.18f),
        size = Size(w * 0.24f, h * 0.14f),
        cornerRadius = CornerRadius(w * 0.04f)
    )
    drawCircle(color, radius = w * 0.17f, center = Offset(w * 0.5f, h * 0.59f), style = Stroke(width = grosor * 0.85f))
}

/** Galería: marco de foto con una montaña y un sol — "Elegir foto". */
private fun DrawScope.dibujarGaleria(color: Color) {
    val w = size.width
    val h = size.height
    val grosor = w * 0.08f
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.12f, h * 0.16f),
        size = Size(w * 0.76f, h * 0.68f),
        cornerRadius = CornerRadius(w * 0.1f),
        style = Stroke(width = grosor)
    )
    drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.32f, h * 0.38f))
    val monte = Path().apply {
        moveTo(w * 0.18f, h * 0.76f)
        lineTo(w * 0.4f, h * 0.5f)
        lineTo(w * 0.56f, h * 0.68f)
        lineTo(w * 0.68f, h * 0.54f)
        lineTo(w * 0.82f, h * 0.76f)
        close()
    }
    drawPath(monte, color)
}

/** Pulgar hacia arriba — "Le gustó". */
private fun DrawScope.dibujarPulgarArriba(color: Color) {
    val w = size.width
    val h = size.height
    val puño = Path().apply {
        moveTo(w * 0.22f, h * 0.46f)
        lineTo(w * 0.78f, h * 0.46f)
        cubicTo(w * 0.84f, h * 0.46f, w * 0.86f, h * 0.52f, w * 0.82f, h * 0.58f)
        lineTo(w * 0.74f, h * 0.84f)
        cubicTo(w * 0.72f, h * 0.9f, w * 0.66f, h * 0.9f, w * 0.3f, h * 0.9f)
        close()
    }
    drawPath(puño, color)
    val pulgar = Path().apply {
        moveTo(w * 0.22f, h * 0.46f)
        cubicTo(w * 0.22f, h * 0.3f, w * 0.34f, h * 0.1f, w * 0.44f, h * 0.1f)
        cubicTo(w * 0.5f, h * 0.1f, w * 0.52f, h * 0.16f, w * 0.48f, h * 0.24f)
        lineTo(w * 0.4f, h * 0.42f)
        close()
    }
    drawPath(pulgar, color)
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.1f, h * 0.46f),
        size = Size(w * 0.14f, h * 0.44f),
        cornerRadius = CornerRadius(w * 0.06f)
    )
}

/** Pulgar hacia abajo — "No le gustó": el mismo dibujo que PULGAR_ARRIBA, volteado verticalmente. */
private fun DrawScope.dibujarPulgarAbajo(color: Color) {
    scale(1f, -1f, pivot = Offset(size.width / 2f, size.height / 2f)) {
        dibujarPulgarArriba(color)
    }
}

/** Triángulo apuntando a la derecha — "reproducir". */
private fun DrawScope.dibujarPlay(color: Color) {
    val w = size.width
    val h = size.height
    val camino = Path().apply {
        moveTo(w * 0.32f, h * 0.18f)
        lineTo(w * 0.32f, h * 0.82f)
        lineTo(w * 0.82f, h * 0.5f)
        close()
    }
    drawPath(camino, color)
}

/** Dos barras verticales — "pausar". */
private fun DrawScope.dibujarPausa(color: Color) {
    val w = size.width
    val h = size.height
    listOf(0.28f, 0.6f).forEach { fx ->
        drawRoundRect(
            color = color,
            topLeft = Offset(w * fx, h * 0.18f),
            size = Size(w * 0.14f, h * 0.64f),
            cornerRadius = CornerRadius(w * 0.04f)
        )
    }
}
