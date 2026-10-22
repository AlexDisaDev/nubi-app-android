package app.nubi.data

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * MotorSueño — v3, traducido en fidelidad de VentanasDespierto.swift (proyecto
 * iOS). Es el motor que calcula la ventana ideal de la próxima siesta o del
 * sueño nocturno para la tarjeta de predicción de Hoy.
 *
 * No decide la hora de dormir por un umbral fijo: la DERIVA de cuánto sueño
 * nocturno necesita el bebé a su edad, mezclado con el historial real de la
 * familia (hasta 30 días) cuando hay datos suficientes, y busca —entre miles
 * de combinaciones de número de siestas, duración y hora de acostar— el plan
 * de menor "coste" (penaliza ventanas demasiado largas o cortas, sueño total
 * fuera de rango sano, desvíos del objetivo de noche, etc.). Se memoiza por
 * minuto porque el cálculo, aunque rápido, se pide en cada recomposición.
 *
 * Todas las fechas se manejan en minutos de reloj de pared (hora local del
 * dispositivo) y se reconstruyen con ZonedDateTime, nunca con duraciones
 * absolutas, para que los cambios de hora del año no desplacen el plan.
 */

// ---------------------------------------------------------------------
// Utilidades de fecha/hora (equivalentes a Calendar.current en iOS)
// ---------------------------------------------------------------------

private val zonaMotor: ZoneId = ZoneId.systemDefault()

private fun Instant.zdt(): ZonedDateTime = this.atZone(zonaMotor)

private fun inicioDelDia(instante: Instant): Instant =
    instante.zdt().toLocalDate().atStartOfDay(zonaMotor).toInstant()

private fun mismoDia(a: Instant, b: Instant): Boolean =
    a.zdt().toLocalDate() == b.zdt().toLocalDate()

private fun sumarDias(instante: Instant, dias: Long): Instant =
    instante.zdt().plusDays(dias).toInstant()

private fun componenteHora(instante: Instant): Int = instante.zdt().hour

/** Minuto del día (0..1439) de un instante, en hora local. */
private fun minutoDelDiaDe(instante: Instant): Int {
    val z = instante.zdt()
    return z.hour * 60 + z.minute
}

/** Reconstruye un instante en el mismo día que `diaBase`, a un minuto del día dado. */
private fun fechaConMinuto(diaBase: Instant, minuto: Int): Instant {
    val mm = minuto.coerceIn(0, 23 * 60 + 59)
    return diaBase.zdt().toLocalDate().atStartOfDay(zonaMotor)
        .plusMinutes(mm.toLong()).toInstant()
}

/** Redondeo "mitad se aleja de cero", igual que `Double.rounded()` en Swift. */
private fun redondear(x: Double): Int =
    if (x >= 0) floor(x + 0.5).toInt() else ceil(x - 0.5).toInt()

private fun mediana(xs: List<Double>): Double {
    val s = xs.sorted()
    val n = s.size
    if (n == 0) return 0.0
    return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2
}

// ---------------------------------------------------------------------
// Guía por edad (tabla cruda) — duraciones en SEGUNDOS, como en iOS.
// ---------------------------------------------------------------------

data class GuiaEdad(
    val mesesHasta: Double,
    val ventanaMinSeg: Double,
    val ventanaMaxSeg: Double,
    val siestasTipicas: IntRange,
    val suenoTotalHoras: ClosedRange<Double>,
    val duracionSiestaTipicaSeg: Double,
    val siestasMaxTotalSeg: Double,
    val bedtimeIdeal: Int,
    val bedtimeMin: Int,
    val bedtimeMax: Int,
    val catnapDurSeg: Double,
    val nocheObjetivoSeg: Double
) {
    companion object {
        val tabla: List<GuiaEdad> = listOf(
            GuiaEdad(
                mesesHasta = 1.0, ventanaMinSeg = 30 * 60.0, ventanaMaxSeg = 60 * 60.0,
                siestasTipicas = 5..7, suenoTotalHoras = 14.0..17.0,
                duracionSiestaTipicaSeg = 45 * 60.0, siestasMaxTotalSeg = 480 * 60.0,
                bedtimeIdeal = 20 * 60, bedtimeMin = 19 * 60 + 30, bedtimeMax = 22 * 60,
                catnapDurSeg = 0.0, nocheObjetivoSeg = 9 * 60 * 60.0
            ),
            GuiaEdad(
                mesesHasta = 3.0, ventanaMinSeg = 60 * 60.0, ventanaMaxSeg = 90 * 60.0,
                siestasTipicas = 4..6, suenoTotalHoras = 14.0..17.0,
                duracionSiestaTipicaSeg = 60 * 60.0, siestasMaxTotalSeg = 300 * 60.0,
                bedtimeIdeal = 19 * 60 + 45, bedtimeMin = 19 * 60 + 30, bedtimeMax = 22 * 60,
                catnapDurSeg = 0.0, nocheObjetivoSeg = 10 * 60 * 60.0
            ),
            GuiaEdad(
                mesesHasta = 4.0, ventanaMinSeg = 75 * 60.0, ventanaMaxSeg = 120 * 60.0,
                siestasTipicas = 4..5, suenoTotalHoras = 13.0..16.0,
                duracionSiestaTipicaSeg = 60 * 60.0, siestasMaxTotalSeg = 255 * 60.0,
                bedtimeIdeal = 19 * 60 + 45, bedtimeMin = 19 * 60 + 30, bedtimeMax = 22 * 60,
                catnapDurSeg = 40 * 60.0, nocheObjetivoSeg = 630 * 60.0
            ),
            GuiaEdad(
                mesesHasta = 5.0, ventanaMinSeg = 75 * 60.0, ventanaMaxSeg = 120 * 60.0,
                siestasTipicas = 4..5, suenoTotalHoras = 12.0..16.0,
                duracionSiestaTipicaSeg = 65 * 60.0, siestasMaxTotalSeg = 225 * 60.0,
                bedtimeIdeal = 19 * 60 + 45, bedtimeMin = 19 * 60 + 30, bedtimeMax = 22 * 60,
                catnapDurSeg = 40 * 60.0, nocheObjetivoSeg = 645 * 60.0
            ),
            GuiaEdad(
                mesesHasta = 7.0, ventanaMinSeg = 120 * 60.0, ventanaMaxSeg = 180 * 60.0,
                siestasTipicas = 3..4, suenoTotalHoras = 12.0..16.0,
                duracionSiestaTipicaSeg = 80 * 60.0, siestasMaxTotalSeg = 210 * 60.0,
                bedtimeIdeal = 19 * 60 + 45, bedtimeMin = 19 * 60 + 30, bedtimeMax = 22 * 60,
                catnapDurSeg = 35 * 60.0, nocheObjetivoSeg = 11 * 60 * 60.0
            ),
            GuiaEdad(
                mesesHasta = 10.0, ventanaMinSeg = 150 * 60.0, ventanaMaxSeg = 210 * 60.0,
                siestasTipicas = 2..3, suenoTotalHoras = 12.0..15.0,
                duracionSiestaTipicaSeg = 85 * 60.0, siestasMaxTotalSeg = 195 * 60.0,
                bedtimeIdeal = 19 * 60 + 45, bedtimeMin = 19 * 60 + 30, bedtimeMax = 22 * 60,
                catnapDurSeg = 30 * 60.0, nocheObjetivoSeg = 675 * 60.0
            ),
            GuiaEdad(
                mesesHasta = 14.0, ventanaMinSeg = 180 * 60.0, ventanaMaxSeg = 240 * 60.0,
                siestasTipicas = 2..2, suenoTotalHoras = 11.0..15.0,
                duracionSiestaTipicaSeg = 95 * 60.0, siestasMaxTotalSeg = 180 * 60.0,
                bedtimeIdeal = 19 * 60 + 45, bedtimeMin = 19 * 60 + 30, bedtimeMax = 22 * 60,
                catnapDurSeg = 25 * 60.0, nocheObjetivoSeg = 675 * 60.0
            ),
            GuiaEdad(
                mesesHasta = 18.0, ventanaMinSeg = 210 * 60.0, ventanaMaxSeg = 285 * 60.0,
                siestasTipicas = 1..2, suenoTotalHoras = 11.0..14.0,
                duracionSiestaTipicaSeg = 110 * 60.0, siestasMaxTotalSeg = 165 * 60.0,
                bedtimeIdeal = 19 * 60 + 45, bedtimeMin = 19 * 60 + 30, bedtimeMax = 22 * 60,
                catnapDurSeg = 25 * 60.0, nocheObjetivoSeg = 675 * 60.0
            ),
            GuiaEdad(
                mesesHasta = 24.0, ventanaMinSeg = 240 * 60.0, ventanaMaxSeg = 330 * 60.0,
                siestasTipicas = 1..1, suenoTotalHoras = 11.0..14.0,
                duracionSiestaTipicaSeg = 115 * 60.0, siestasMaxTotalSeg = 150 * 60.0,
                bedtimeIdeal = 20 * 60, bedtimeMin = 19 * 60 + 30, bedtimeMax = 22 * 60,
                catnapDurSeg = 25 * 60.0, nocheObjetivoSeg = 670 * 60.0
            ),
            GuiaEdad(
                mesesHasta = 99.0, ventanaMinSeg = 300 * 60.0, ventanaMaxSeg = 390 * 60.0,
                siestasTipicas = 0..1, suenoTotalHoras = 10.0..13.0,
                duracionSiestaTipicaSeg = 90 * 60.0, siestasMaxTotalSeg = 120 * 60.0,
                bedtimeIdeal = 20 * 60 + 15, bedtimeMin = 19 * 60 + 30, bedtimeMax = 22 * 60,
                catnapDurSeg = 20 * 60.0, nocheObjetivoSeg = 11 * 60 * 60.0
            )
        )
    }
}

/** Guía con todos los campos interpolados por edad EXACTA, en minutos. */
data class GuiaEdadAjustada(
    val ventanaMin: Int,
    val ventanaMax: Int,
    val duracionSiesta: Int,
    val siestasMaxTotal: Int,
    val bedtimeIdeal: Int,
    val bedtimeMin: Int,
    val bedtimeMax: Int,
    val catnapDur: Int,
    /** Duración objetivo del sueño nocturno, en minutos. Ancla la hora de dormir. */
    val nocheObjetivo: Int,
    val totalMin: Double,
    val totalMax: Double,
    /** Fraccionario a propósito: 3...4 → 3.5. */
    val siestasEsperadas: Double,
    val siestasTipicas: IntRange
) {
    val suenoTotalHoras: ClosedRange<Double> get() = totalMin..totalMax
    val suenoIdealMin: Double get() = (totalMin + totalMax) / 2 * 60
    val permiteCatnap: Boolean get() = catnapDur >= 15
}

// ---------------------------------------------------------------------
// Tipos de salida
// ---------------------------------------------------------------------

enum class TipoPrediccion { SIESTA, NOCHE }

/** Por qué el plan de hoy no es el ideal (se le cuenta al padre, no se esconde). */
sealed class DiagnosticoDia {
    object Ok : DiagnosticoDia()
    data class DespertarEstimado(val fecha: Instant) : DiagnosticoDia()
    data class VentanaLarga(val minutos: Int) : DiagnosticoDia()
    data class DiaCorto(val minutos: Int) : DiagnosticoDia()
    data class PocoSueno(val horas: Double, val minimo: Double) : DiagnosticoDia()
    data class MuchoSueno(val horas: Double, val maximo: Double) : DiagnosticoDia()
    data class SiestasRecortadas(val minutos: Int) : DiagnosticoDia()
}

data class PrediccionDia(
    val tipo: TipoPrediccion,
    val desde: Instant,
    val hasta: Instant,
    val numero: Int,
    val ajustadaPorHistorial: Boolean,
    val vigiliaPrevista: Int = 0,
    val vigiliaMinima: Int = 0,
    val vigiliaMaxima: Int = 0,
    val despertarReferencia: Instant? = null,
    val diagnostico: DiagnosticoDia = DiagnosticoDia.Ok,
    /** Sueño total previsto para el día completo (siestas + noche), en segundos. */
    val suenoTotalPrevisto: Double = 0.0,
    val rangoSano: ClosedRange<Double> = 11.0..15.0,
    val nochesAprendidas: Int = 0
) {
    fun yaEnVentana(ahora: Instant): Boolean = !ahora.isBefore(desde) && !ahora.isAfter(hasta)
    fun pasada(ahora: Instant): Boolean = ahora.isAfter(hasta)
}

data class VentanaIdeal(
    val numero: Int,
    val rangoInicioDesde: Instant,
    val rangoInicioHasta: Instant,
    /** Segundos. */
    val duracionTipica: Double,
    val inicioReal: Instant?,
    val finReal: Instant?,
    val esNocturna: Boolean = false,
    val esCatnap: Boolean = false,
    val ajustadaPorHistorial: Boolean = false,
    val vigiliaPrevia: Int = 0,
    val vigiliaMaxima: Int = 0,
    val excesoVigilia: Int = 0
) {
    val realizada: Boolean get() = inicioReal != null
}

data class ResultadoDia(
    val ventanas: List<VentanaIdeal>,
    val diagnostico: DiagnosticoDia,
    /** Segundos. */
    val suenoTotal: Double,
    val despertar: Instant,
    val noches: Int
)

// ---------------------------------------------------------------------
// Motor
// ---------------------------------------------------------------------

object MotorSueno {

    // --- Aprendizaje
    private const val aprendDias = 30
    private const val aprendMinMuestras = 5
    private const val aprendNConfianza = 8.0
    private const val aprendDiasSiestas = 14
    private const val aprendNConfianzaSiestas = 5.0

    // --- Planificación
    private const val siestaMinima = 25 // minutos
    private const val bandaVentanaSeg = 15 * 60L
    private const val maxSuenos = 10

    // --- Pesos del coste (ajustados en iOS contra 793 escenarios)
    private const val wExceso = 6.0
    private const val wDeficit = 8.0
    private const val wRango = 30.0
    private const val wIdeal = 0.1
    private const val wObjetivo = 3.0
    private const val wNSiestas = 60.0
    private const val wCatnap = 25.0
    private const val wRecorte = 0.4
    private const val wEscala = 25.0
    private const val wForma = 3.5

    // --- Caché (memoizado por minuto; igual intención que el NSLock de iOS)
    private data class ClaveCache(
        val dia: Instant,
        val edadDias: Int,
        val firma: Long,
        val despertarManual: Instant?,
        val minuto: Long
    )

    private val candado = ReentrantLock()
    private val cache = mutableMapOf<ClaveCache, List<VentanaIdeal>>()
    private val cacheDiag = mutableMapOf<ClaveCache, DiagnosticoDia>()
    private val cacheExtra = mutableMapOf<ClaveCache, Triple<Double, Instant, Int>>()
    private val ordenCache = mutableListOf<ClaveCache>()
    private const val cacheMax = 8

    private fun firmaDe(registros: List<Registro>): Long {
        var h = registros.size.toLong() * 1_000_003L + 7L
        val ultimos = registros.filter { it.tipo.esSueno }.takeLast(12)
        for (r in ultimos) {
            h = h * 1_000_003L + r.inicio.epochSecond
            h = h * 1_000_003L + (r.fin?.epochSecond ?: -1L)
            h = h * 1_000_003L + r.tipo.ordinal
        }
        return h
    }

    private fun guardar(
        clave: ClaveCache, v: List<VentanaIdeal>, d: DiagnosticoDia,
        e: Triple<Double, Instant, Int>
    ) {
        cache[clave] = v
        cacheDiag[clave] = d
        cacheExtra[clave] = e
        ordenCache.add(clave)
        while (ordenCache.size > cacheMax) {
            val viejo = ordenCache.removeAt(0)
            cache.remove(viejo)
            cacheDiag.remove(viejo)
            cacheExtra.remove(viejo)
        }
    }

    // -------------------------------------------------------------------
    // API principal
    // -------------------------------------------------------------------

    fun ventanasIdealesDelDia(
        bebe: Bebe, registros: List<Registro>, dia: Instant,
        ahora: Instant = Instant.now(), despertarManual: Instant? = null
    ): List<VentanaIdeal> =
        resultadoDelDia(bebe, registros, dia, ahora, despertarManual).ventanas

    /** Lo que usa la tarjeta de Hoy: ventanas del día + diagnóstico + métricas. */
    fun resultadoDelDia(
        bebe: Bebe, registros: List<Registro>, dia: Instant,
        ahora: Instant = Instant.now(), despertarManual: Instant? = null
    ): ResultadoDia {
        val vacio = ResultadoDia(
            ventanas = emptyList(), diagnostico = DiagnosticoDia.Ok,
            suenoTotal = 0.0, despertar = dia, noches = 0
        )

        if (!mismoDia(dia, ahora)) return vacio
        // Noche en curso -> solo franja roja, sin iconos del día.
        if (registros.any { it.tipo == TipoRegistro.NOCHE && it.fin == null }) return vacio

        val hayDespertarManana = registros.any { r ->
            val fin = r.fin
            r.tipo == TipoRegistro.NOCHE && fin != null && mismoDia(fin, dia) && componenteHora(fin) >= 4
        }
        val haySiestasHoy = registros.any { it.tipo == TipoRegistro.SIESTA && mismoDia(it.inicio, dia) }
        if (!hayDespertarManana && !haySiestasHoy) {
            val ultimoSueno = registros.filter { it.tipo.esSueno }
                .maxByOrNull { it.fin ?: it.inicio }
            if (ultimoSueno != null && ultimoSueno.tipo == TipoRegistro.NOCHE) return vacio
        }

        val clave = ClaveCache(
            dia = inicioDelDia(dia),
            edadDias = bebe.edadEnDias(ahora),
            firma = firmaDe(registros),
            despertarManual = despertarManual,
            minuto = ahora.epochSecond / 60
        )

        candado.withLock {
            val v = cache[clave]; val d = cacheDiag[clave]; val e = cacheExtra[clave]
            if (v != null && d != null && e != null) {
                return ResultadoDia(v, d, e.first, e.second, e.third)
            }
        }

        val r = calcular(bebe, registros, dia, ahora, despertarManual)

        candado.withLock {
            guardar(clave, r.ventanas, r.diagnostico, Triple(r.suenoTotal, r.despertar, r.noches))
        }
        return r
    }

    /** Predicción escueta para la tarjeta de Hoy: solo la próxima ventana aún no realizada. */
    fun prediccionDelDia(
        bebe: Bebe, registros: List<Registro>, ahora: Instant = Instant.now(),
        despertarManual: Instant? = null
    ): PrediccionDia? {
        if (registros.any { it.tipo.esSueno && it.fin == null }) return null
        val r = resultadoDelDia(bebe, registros, ahora, ahora, despertarManual)
        val proxima = r.ventanas.firstOrNull { !it.realizada } ?: return null
        val g = guiaAjustada(bebe.edadEnMeses(ahora))
        return PrediccionDia(
            tipo = if (proxima.esNocturna) TipoPrediccion.NOCHE else TipoPrediccion.SIESTA,
            desde = proxima.rangoInicioDesde,
            hasta = proxima.rangoInicioHasta,
            numero = proxima.numero,
            ajustadaPorHistorial = proxima.ajustadaPorHistorial,
            vigiliaPrevista = proxima.vigiliaPrevia,
            vigiliaMinima = g.ventanaMin,
            vigiliaMaxima = proxima.vigiliaMaxima,
            despertarReferencia = r.despertar,
            diagnostico = r.diagnostico,
            suenoTotalPrevisto = r.suenoTotal,
            rangoSano = g.suenoTotalHoras,
            nochesAprendidas = r.noches
        )
    }

    // -------------------------------------------------------------------
    // Cálculo real
    // -------------------------------------------------------------------

    private data class RealSiesta(val ini: Int, val fin: Int)

    private enum class Clase { SIESTA, CATNAP, NOCHE }

    private data class PasoInterno(
        val clase: Clase,
        val inicio: Int,
        val fin: Int,
        val vigiliaPrevia: Int,
        val vigiliaMaxima: Int = 0,
        val exceso: Int = 0,
        val deficit: Int = 0,
        val real: Boolean = false
    )

    private data class PlanInterno(
        val pasos: List<PasoInterno>,
        val coste: Double,
        val noche: Int,
        val suenoTotal: Int,
        val excesoMax: Int,
        val deficitMax: Int,
        val recorte: Int
    )

    private data class Estructura(
        val durs: List<Int>,
        val siestasYa: Int,
        val recorte: Int,
        val durIdeal: (Int) -> Int
    )

    private data class Limites(
        val lo: List<Int>,
        val hi: List<Int>,
        val forma: List<Double>
    )

    private fun calcular(
        bebe: Bebe, registros: List<Registro>, dia: Instant,
        ahora: Instant, despertarManual: Instant?
    ): ResultadoDia {
        val g = guiaAjustada(bebe.edadEnMeses(ahora))

        fun minutoDelDia(d: Instant): Int = minutoDelDiaDe(d)
        fun fecha(m: Int): Instant = fechaConMinuto(dia, m)

        val (despertarFecha, estimado) = despertarDeReferencia(bebe, registros, dia, ahora, despertarManual)
        val despertar = minutoDelDia(despertarFecha)

        val reales = mutableListOf<RealSiesta>()
        val siestasHoy = registros
            .filter { it.tipo == TipoRegistro.SIESTA && mismoDia(it.inicio, dia) }
            .sortedBy { it.inicio }
        var tope = despertar
        for (r in siestasHoy) {
            val f = r.fin ?: continue
            val i = max(tope, minutoDelDia(r.inicio))
            val fn = max(i + siestaMinima / 2, minutoDelDia(f))
            if (fn <= i || i >= 24 * 60) continue
            reales.add(RealSiesta(i, fn))
            tope = fn
        }
        val realesFinal: List<RealSiesta> =
            if (reales.size > maxSuenos) reales.take(maxSuenos) else reales

        val durAprendidas = duracionesSiestaAprendidas(registros, dia, g)
        val ventAprendidas = ventanasAprendidas(registros, dia, g)
        val (btAprendido, nNoches) = bedtimeAprendidoConMuestras(registros, dia, g)
        val (nHabituales, nDias) = siestasHabituales(registros, dia)

        var despertarManana = despertar
        horaMediaDespertarManana(registros, dia)?.let { despertarManana = minutoDelDia(it) }
        despertarManana = max(5 * 60 + 30, min(9 * 60, despertarManana))

        val conf = if (btAprendido != null && nNoches > 0) min(1.0, nNoches / aprendNConfianza) else 0.0
        val siestasEsperadasRedondeadas = redondear(g.siestasEsperadas).toDouble()
        val nAncla: Double = if (nHabituales != null && nDias > 0) {
            val c = min(1.0, nDias / aprendNConfianzaSiestas)
            (1 - c) * siestasEsperadasRedondeadas + c * nHabituales
        } else siestasEsperadasRedondeadas

        // --- barrido de candidatos
        val k = realesFinal.size
        val nLo = max(k, if (g.siestasTipicas.last == 0) 0 else max(0, g.siestasEsperadas.toInt() - 1))
        val nHi = min(maxSuenos, max(nLo, max(k, (g.siestasEsperadas + 0.5).toInt() + 2)))

        var mejor: PlanInterno? = null
        for (nTotal in nLo..max(nLo, nHi)) {
            val opciones: List<Boolean> =
                if (nTotal == 0 || !g.permiteCatnap || nTotal <= k) listOf(false) else listOf(false, true)
            for (ultimaCorta in opciones) {
                for (escala in listOf(0.8, 1.0, 1.25)) {
                    val est = estructura(g, realesFinal, nTotal, ultimaCorta, durAprendidas, escala) ?: continue
                    val lim = limitesVentanas(g, realesFinal, nTotal, ultimaCorta, est)

                    val bPresupuesto = 1440.0 + despertarManana - g.nocheObjetivo
                    val base = 0.72 * bPresupuesto + 0.28 * g.bedtimeIdeal
                    var objetivo = (1 - conf) * base + conf * (btAprendido?.toDouble() ?: base)
                    objetivo = max(g.bedtimeMin.toDouble(), min(g.bedtimeMax.toDouble(), objetivo))
                    val obj = redondear(objetivo)

                    var local: PlanInterno? = null
                    for (paso in listOf(10, 2, 1)) {
                        val desde: Int
                        val hasta: Int
                        val actual = local
                        if (actual != null) {
                            desde = max(g.bedtimeMin, actual.noche - paso * 5)
                            hasta = min(g.bedtimeMax, actual.noche + paso * 5)
                        } else {
                            desde = g.bedtimeMin
                            hasta = g.bedtimeMax
                        }
                        var n = desde
                        while (n <= hasta) {
                            val p = resolver(
                                g = g, despertar = despertar, reales = realesFinal, nTotal = nTotal,
                                ultimaCorta = ultimaCorta, noche = n, objetivo = obj, est = est, lim = lim,
                                despertarManana = despertarManana, ventAprendidas = ventAprendidas,
                                conf = conf, escala = escala, nAncla = nAncla
                            )
                            if (p != null && (local == null || p.coste < local!!.coste)) local = p
                            n += paso
                        }
                        if (local == null) break
                    }
                    val l = local
                    if (l != null && (mejor == null || l.coste < mejor!!.coste)) mejor = l
                }
            }
        }

        val plan = mejor ?: return ResultadoDia(
            ventanas = emptyList(), diagnostico = DiagnosticoDia.Ok,
            suenoTotal = 0.0, despertar = despertarFecha, noches = nNoches
        )

        // --- diagnóstico honesto
        var diag: DiagnosticoDia = DiagnosticoDia.Ok
        when {
            estimado -> diag = DiagnosticoDia.DespertarEstimado(despertarFecha)
            plan.excesoMax >= 15 -> diag = DiagnosticoDia.VentanaLarga(plan.excesoMax)
            plan.deficitMax >= 15 -> diag = DiagnosticoDia.DiaCorto(plan.deficitMax)
            plan.suenoTotal.toDouble() < g.totalMin * 60 ->
                diag = DiagnosticoDia.PocoSueno(plan.suenoTotal / 60.0, g.totalMin)
            plan.suenoTotal.toDouble() > g.totalMax * 60 ->
                diag = DiagnosticoDia.MuchoSueno(plan.suenoTotal / 60.0, g.totalMax)
            plan.recorte >= 35 -> diag = DiagnosticoDia.SiestasRecortadas(plan.recorte)
        }

        // --- mapear a VentanaIdeal
        val ajustada = btAprendido != null || durAprendidas.isNotEmpty() || ventAprendidas.isNotEmpty()
        val salida = mutableListOf<VentanaIdeal>()
        var numero = 0
        val realesRestantes = siestasHoy.toMutableList()
        for (p in plan.pasos) {
            numero += 1
            val centro = fecha(p.inicio)
            var inicioReal: Instant? = null
            var finReal: Instant? = null
            if (p.clase == Clase.NOCHE) {
                val noche = registros.firstOrNull { it.tipo == TipoRegistro.NOCHE && mismoDia(it.inicio, dia) }
                if (noche != null) {
                    inicioReal = noche.inicio
                    finReal = noche.fin
                }
            } else if (p.real) {
                val idx = realesRestantes.indexOfFirst {
                    abs(Duration.between(it.inicio, centro).seconds) <= 90 * 60
                }
                if (idx >= 0) {
                    val r = realesRestantes.removeAt(idx)
                    inicioReal = r.inicio
                    finReal = r.fin
                }
            }
            salida.add(
                VentanaIdeal(
                    numero = numero,
                    rangoInicioDesde = centro.minusSeconds(bandaVentanaSeg),
                    rangoInicioHasta = centro.plusSeconds(bandaVentanaSeg),
                    duracionTipica = (max(0, p.fin - p.inicio) * 60).toDouble(),
                    inicioReal = inicioReal,
                    finReal = finReal,
                    esNocturna = p.clase == Clase.NOCHE,
                    esCatnap = p.clase == Clase.CATNAP,
                    ajustadaPorHistorial = ajustada,
                    vigiliaPrevia = p.vigiliaPrevia,
                    vigiliaMaxima = p.vigiliaMaxima,
                    excesoVigilia = p.exceso
                )
            )
        }
        return ResultadoDia(
            ventanas = salida, diagnostico = diag,
            suenoTotal = plan.suenoTotal.toDouble() * 60, despertar = despertarFecha, noches = nNoches
        )
    }

    // -------------------------------------------------------------------
    // Interpolación de la guía
    // -------------------------------------------------------------------

    private data class Centro(val centro: Double, val guia: GuiaEdad)

    fun guiaAjustada(meses: Double): GuiaEdadAjustada {
        val centros = mutableListOf<Centro>()
        var prev = 0.0
        for (g in GuiaEdad.tabla) {
            val tope = if (g.mesesHasta > 90) 36.0 else g.mesesHasta
            centros.add(Centro((prev + tope) / 2.0, g))
            prev = g.mesesHasta
        }

        fun construir(a: GuiaEdad, b: GuiaEdad?, f: Double): GuiaEdadAjustada {
            fun mez(x: Double, y: Double): Double = x + (y - x) * f
            val bb = b ?: a
            val base = if (b == null || f < 0.5) a else bb
            val espA = (a.siestasTipicas.first + a.siestasTipicas.last) / 2.0
            val espB = (bb.siestasTipicas.first + bb.siestasTipicas.last) / 2.0
            return GuiaEdadAjustada(
                ventanaMin = redondear(mez(a.ventanaMinSeg / 60, bb.ventanaMinSeg / 60)),
                ventanaMax = redondear(mez(a.ventanaMaxSeg / 60, bb.ventanaMaxSeg / 60)),
                duracionSiesta = redondear(mez(a.duracionSiestaTipicaSeg / 60, bb.duracionSiestaTipicaSeg / 60)),
                siestasMaxTotal = redondear(mez(a.siestasMaxTotalSeg / 60, bb.siestasMaxTotalSeg / 60)),
                bedtimeIdeal = redondear(mez(a.bedtimeIdeal.toDouble(), bb.bedtimeIdeal.toDouble())),
                bedtimeMin = redondear(mez(a.bedtimeMin.toDouble(), bb.bedtimeMin.toDouble())),
                bedtimeMax = redondear(mez(a.bedtimeMax.toDouble(), bb.bedtimeMax.toDouble())),
                catnapDur = redondear(mez(a.catnapDurSeg / 60, bb.catnapDurSeg / 60)),
                nocheObjetivo = redondear(mez(a.nocheObjetivoSeg / 60, bb.nocheObjetivoSeg / 60)),
                totalMin = mez(a.suenoTotalHoras.start, bb.suenoTotalHoras.start),
                totalMax = mez(a.suenoTotalHoras.endInclusive, bb.suenoTotalHoras.endInclusive),
                siestasEsperadas = mez(espA, espB),
                siestasTipicas = base.siestasTipicas
            )
        }

        val primero = centros.firstOrNull() ?: return construir(GuiaEdad.tabla[4], null, 0.0)
        val ultimo = centros.last()
        if (meses <= primero.centro) return construir(primero.guia, null, 0.0)
        if (meses >= ultimo.centro) return construir(ultimo.guia, null, 0.0)
        for (i in 0 until centros.size - 1) {
            val (c0, g0) = centros[i]
            val (c1, g1) = centros[i + 1]
            if (meses >= c0 && meses <= c1) {
                return construir(g0, g1, (meses - c0) / (c1 - c0))
            }
        }
        return construir(ultimo.guia, null, 0.0)
    }

    // -------------------------------------------------------------------
    // Núcleo del planificador
    // -------------------------------------------------------------------

    /** Duraciones de las siestas pendientes, con el tope de sueño diurno aplicado. */
    private fun estructura(
        g: GuiaEdadAjustada, reales: List<RealSiesta>, nTotal: Int,
        ultimaCorta: Boolean, durAprendidas: Map<Int, Int>, escala: Double
    ): Estructura? {
        val k = reales.size
        val rem = nTotal - k
        if (rem < 0) return null

        val dt = g.duracionSiesta
        val esp = nTotal
        val durIdeal: (Int) -> Int = { pos ->
            when {
                esp <= 1 -> dt
                pos >= esp - 1 -> (dt * 0.7).toInt()
                else -> dt
            }
        }

        val durs = mutableListOf<Int>()
        for (j in 0 until rem) {
            val pos = k + j
            var d = (durAprendidas[pos] ?: durIdeal(pos)).toDouble()
            d = if (ultimaCorta && pos == nTotal - 1) {
                if (g.catnapDur > 0) g.catnapDur.toDouble() else dt * 0.45
            } else {
                d * escala
            }
            durs.add(max(siestaMinima, redondear(d)))
        }

        val siestasYa = reales.sumOf { it.fin - it.ini }
        var recorte = 0
        if (durs.isNotEmpty()) {
            val exceso = (siestasYa + durs.sum()) - g.siestasMaxTotal
            if (exceso > 0) {
                val minimos = durs.map { max(siestaMinima, (it * 0.6).toInt()) }
                val margen = durs.sum() - minimos.sum()
                if (margen > 0) {
                    val quitar = min(exceso, margen)
                    for (j in durs.indices) {
                        val parte = quitar.toDouble() * (durs[j] - minimos[j]) / margen
                        durs[j] = max(minimos[j], redondear(durs[j] - parte))
                    }
                    recorte = quitar
                }
            }
        }
        return Estructura(durs = durs.toList(), siestasYa = siestasYa, recorte = recorte, durIdeal = durIdeal)
    }

    private fun limitesVentanas(
        g: GuiaEdadAjustada, reales: List<RealSiesta>, nTotal: Int,
        ultimaCorta: Boolean, est: Estructura
    ): Limites {
        val k = reales.size
        val m = est.durs.size + 1
        val lo = mutableListOf<Int>(); val hi = mutableListOf<Int>(); val forma = mutableListOf<Double>()
        for (i in 0 until m) {
            var f = 1.0
            if (i > 0) {
                val dPrev = est.durs[i - 1]
                val espPrev = est.durIdeal(k + i - 1)
                f = when {
                    ultimaCorta && (k + i - 1) == nTotal - 1 -> 0.75
                    dPrev < 0.75 * espPrev -> 0.85
                    else -> 1.0
                }
            } else if (k > 0) {
                val ult = reales.last()
                if ((ult.fin - ult.ini) < 0.75 * est.durIdeal(k - 1)) f = 0.85
            }
            val li = redondear(g.ventanaMin * f)
            var hs = redondear(g.ventanaMax * f)
            if (i == m - 1) {
                hs += min(30, redondear(0.12 * g.ventanaMax))
            }
            lo.add(li)
            hi.add(hs)
            forma.add(li + (hs - li) * (i.toDouble() / max(1, m - 1)))
        }
        return Limites(lo = lo, hi = hi, forma = forma)
    }

    /** Reparto por saturación: continuo y monótono en `disponible`. */
    private fun repartir(disponible: Double, lo: List<Int>, hi: List<Int>, forma: List<Double>): DoubleArray {
        val m = lo.size
        if (m == 0) return DoubleArray(0)
        val sf = max(1e-9, forma.sum())
        val x = DoubleArray(m) {
            max(lo[it].toDouble(), min(hi[it].toDouble(), disponible * forma[it] / sf))
        }
        for (iter in 0 until 40) {
            val r = disponible - x.sum()
            if (abs(r) < 0.5) break
            val holg = DoubleArray(m) { if (r > 0) hi[it] - x[it] else x[it] - lo[it] }
            val tot = holg.sum()
            if (tot < 1e-9) break
            for (i in 0 until m) {
                if (holg[i] > 0) {
                    x[i] = max(lo[i].toDouble(), min(hi[i].toDouble(), x[i] + r * holg[i] / tot))
                }
            }
        }
        return x
    }

    private fun resolver(
        g: GuiaEdadAjustada, despertar: Int, reales: List<RealSiesta>, nTotal: Int,
        ultimaCorta: Boolean, noche: Int, objetivo: Int, est: Estructura, lim: Limites,
        despertarManana: Int, ventAprendidas: Map<Int, Int>, conf: Double,
        escala: Double, nAncla: Double
    ): PlanInterno? {
        val k = reales.size
        val rem = est.durs.size
        val m = rem + 1
        val t0 = reales.lastOrNull()?.fin ?: despertar

        val forma = lim.forma.toMutableList()
        for (i in 0 until m) {
            ventAprendidas[k + i]?.let { v ->
                forma[i] = max(lim.lo[i], min(lim.hi[i], v)).toDouble()
            }
        }

        val disponible = (noche - t0 - est.durs.sum()).toDouble()
        val x = repartir(disponible, lim.lo, lim.hi, forma).map { redondear(it) }.toMutableList()
        x[m - 1] += redondear(disponible) - x.sum()

        val pasos = mutableListOf<PasoInterno>()
        var tp = despertar
        for (r in reales) {
            pasos.add(
                PasoInterno(
                    clase = Clase.SIESTA, inicio = r.ini, fin = r.fin,
                    vigiliaPrevia = max(0, r.ini - tp), vigiliaMaxima = g.ventanaMax, real = true
                )
            )
            tp = r.fin
        }
        var t = t0
        for (j in 0 until rem) {
            val ini = t + x[j]
            val fin = ini + est.durs[j]
            val pos = k + j
            val clase = if (ultimaCorta && pos == nTotal - 1) Clase.CATNAP else Clase.SIESTA
            pasos.add(
                PasoInterno(
                    clase = clase, inicio = ini, fin = fin,
                    vigiliaPrevia = x[j], vigiliaMaxima = lim.hi[j],
                    exceso = max(0, x[j] - lim.hi[j]), deficit = max(0, lim.lo[j] - x[j])
                )
            )
            t = fin
        }
        pasos.add(
            PasoInterno(
                clase = Clase.NOCHE, inicio = noche, fin = noche,
                vigiliaPrevia = x[m - 1], vigiliaMaxima = lim.hi[m - 1],
                exceso = max(0, x[m - 1] - lim.hi[m - 1]), deficit = max(0, lim.lo[m - 1] - x[m - 1])
            )
        )

        val siestasTotal = est.siestasYa + est.durs.sum()
        val nocheLargo = (1440 - noche) + despertarManana
        val suenoTotal = siestasTotal + nocheLargo

        val exceso = pasos.sumOf { it.exceso }
        val deficit = pasos.sumOf { it.deficit }
        var tension = 0.0
        for (i in 0 until m) {
            val d = x[i].toDouble() - forma[i]
            tension += d * d
        }
        tension /= max(1, m).toDouble()

        val loT = g.totalMin * 60; val hiT = g.totalMax * 60
        val fuera = max(0.0, loT - suenoTotal.toDouble()) + max(0.0, suenoTotal.toDouble() - hiT)

        val wIdealEfe = wIdeal * (1 - 0.9 * conf)
        val wObjEfe = wObjetivo * (1 + 5 * conf)

        val excesoD = exceso.toDouble()
        val deficitD = deficit.toDouble()
        val suenoTotalD = suenoTotal.toDouble()
        val nTotalD = nTotal.toDouble()
        val nocheD = noche.toDouble()
        val objetivoD = objetivo.toDouble()

        val coste =
            wExceso * excesoD.pow(2) / 60 +
                wDeficit * deficitD.pow(2) / 60 +
                wRango * fuera.pow(2) / 60 +
                wIdealEfe * (suenoTotalD - g.suenoIdealMin).pow(2) / 60 +
                wObjEfe * (nocheD - objetivoD).pow(2) / 60 +
                wNSiestas * (nTotalD - (if (ultimaCorta) 0.5 else 0.0) - nAncla).pow(2) +
                (if (ultimaCorta) wCatnap else 0.0) +
                wRecorte * est.recorte.toDouble() +
                wEscala * (abs(escala - 1.0) * 10).pow(2) +
                wForma * tension / 60

        return PlanInterno(
            pasos = pasos, coste = coste, noche = noche, suenoTotal = suenoTotal,
            excesoMax = pasos.maxOfOrNull { it.exceso } ?: 0,
            deficitMax = pasos.maxOfOrNull { it.deficit } ?: 0,
            recorte = est.recorte
        )
    }

    // -------------------------------------------------------------------
    // Despertar de referencia
    // -------------------------------------------------------------------

    private fun despertarDeReferencia(
        bebe: Bebe, registros: List<Registro>, dia: Instant, ahora: Instant, despertarManual: Instant?
    ): Pair<Instant, Boolean> {
        val inicioDia = inicioDelDia(dia)
        val mediodia = fechaConMinuto(dia, 12 * 60)

        val real = registros.filter { it.tipo == TipoRegistro.NOCHE }
            .mapNotNull { it.fin }
            .filter { it >= inicioDia && it < mediodia && componenteHora(it) >= 4 }
            .maxOrNull()
        if (real != null) return real to false

        if (registros.any { it.tipo == TipoRegistro.NOCHE && it.fin == null && !mismoDia(it.inicio, dia) }) {
            return ahora to true
        }
        if (despertarManual != null && mismoDia(despertarManual, dia) && despertarManual < mediodia) {
            return despertarManual to false
        }
        horaMediaDespertarManana(registros, dia)?.let { return it to true }
        return fechaConMinuto(dia, 7 * 60) to true
    }

    // -------------------------------------------------------------------
    // Aprendizaje
    // -------------------------------------------------------------------

    /** Duración de siesta aprendida por posición (0 = 1ª siesta del día). */
    fun duracionesSiestaAprendidas(
        registros: List<Registro>, dia: Instant, g: GuiaEdadAjustada
    ): Map<Int, Int> {
        val hoy = inicioDelDia(dia)
        val corte = sumarDias(hoy, -aprendDias.toLong())

        val porDia = mutableMapOf<Instant, MutableList<Registro>>()
        for (r in registros) {
            if (r.tipo != TipoRegistro.SIESTA) continue
            if (r.fin == null) continue
            if (r.inicio < corte || r.inicio >= hoy) continue
            porDia.getOrPut(inicioDelDia(r.inicio)) { mutableListOf() }.add(r)
        }

        val porPos = mutableMapOf<Int, MutableList<Double>>()
        for ((_, siestas) in porDia) {
            val ordenadas = siestas.sortedBy { it.inicio }
            for ((pos, r) in ordenadas.withIndex()) {
                val f = r.fin ?: continue
                val mins = Duration.between(r.inicio, f).seconds / 60.0
                if (mins < 15 || mins > 180) continue
                porPos.getOrPut(pos) { mutableListOf() }.add(mins)
            }
        }

        val esperadas = max(1, redondear(g.siestasEsperadas))
        val salida = mutableMapOf<Int, Int>()
        for (pos in 0 until esperadas) {
            val muestras = porPos[pos] ?: continue
            if (muestras.size < aprendMinMuestras) continue
            val med = mediana(muestras)
            val c = min(1.0, muestras.size / aprendNConfianza)
            val base = if (pos >= esperadas - 1 && esperadas > 1) g.duracionSiesta * 0.7 else g.duracionSiesta.toDouble()
            val valor = base * (1 - c) + med * c
            salida[pos] = redondear(max(siestaMinima.toDouble(), min(150.0, valor)))
        }
        return salida
    }

    /** Ventana de vigilia real aprendida por posición. */
    fun ventanasAprendidas(
        registros: List<Registro>, dia: Instant, g: GuiaEdadAjustada
    ): Map<Int, Int> {
        val hoy = inicioDelDia(dia)
        val corte = sumarDias(hoy, -aprendDias.toLong())

        val porPos = mutableMapOf<Int, MutableList<Double>>()
        val dias = mutableSetOf<Instant>()
        for (r in registros) {
            if (!r.tipo.esSueno) continue
            if (r.inicio >= corte && r.inicio < hoy) dias.add(inicioDelDia(r.inicio))
        }
        for (d in dias) {
            val finDia = sumarDias(d, 1)
            val despertar = registros.mapNotNull { r ->
                if (r.tipo != TipoRegistro.NOCHE) return@mapNotNull null
                val f = r.fin ?: return@mapNotNull null
                if (f < d || f >= finDia) return@mapNotNull null
                val h = componenteHora(f)
                if (h in 4..11) f else null
            }.maxOrNull() ?: continue

            val siestas = registros.filter {
                it.tipo == TipoRegistro.SIESTA && it.inicio >= despertar && it.inicio < finDia
            }.sortedBy { it.inicio }

            var t = despertar
            for ((pos, s) in siestas.withIndex()) {
                val v = Duration.between(t, s.inicio).seconds / 60.0
                if (v in 20.0..420.0) {
                    porPos.getOrPut(pos) { mutableListOf() }.add(v)
                }
                val f = s.fin ?: break
                t = f
            }
        }

        val salida = mutableMapOf<Int, Int>()
        for ((pos, muestras) in porPos) {
            if (muestras.size < aprendMinMuestras) continue
            val med = mediana(muestras)
            val c = min(1.0, muestras.size / aprendNConfianza)
            val base = (g.ventanaMin + g.ventanaMax) / 2.0
            val valor = base * (1 - c) + med * c
            salida[pos] = redondear(max(g.ventanaMin * 0.7, min(g.ventanaMax * 1.3, valor)))
        }
        return salida
    }

    /** Hora de acostarse aprendida (minutos desde medianoche) y nº de muestras. */
    private fun bedtimeAprendidoConMuestras(
        registros: List<Registro>, dia: Instant, g: GuiaEdadAjustada
    ): Pair<Int?, Int> {
        val hoy = inicioDelDia(dia)
        val corte = sumarDias(hoy, -aprendDias.toLong())
        val muestras = registros.mapNotNull { r ->
            if (r.tipo != TipoRegistro.NOCHE) return@mapNotNull null
            if (r.inicio < corte || r.inicio >= hoy) return@mapNotNull null
            val h = componenteHora(r.inicio)
            if (h !in 17..23) return@mapNotNull null
            val z = r.inicio.zdt()
            (z.hour * 60 + z.minute).toDouble()
        }
        if (muestras.size < aprendMinMuestras) return null to muestras.size
        val valMed = mediana(muestras)
        val acotado = max(g.bedtimeMin.toDouble(), min(g.bedtimeMax.toDouble(), valMed))
        return redondear(acotado) to muestras.size
    }

    /** Cuántas siestas hace este bebé habitualmente (etapa del desarrollo, no dato del día). */
    private fun siestasHabituales(registros: List<Registro>, dia: Instant): Pair<Int?, Int> {
        val hoy = inicioDelDia(dia)
        val corte = sumarDias(hoy, -aprendDiasSiestas.toLong())
        val porDia = mutableMapOf<Instant, Int>()
        for (r in registros) {
            if (r.tipo != TipoRegistro.SIESTA) continue
            if (r.fin == null) continue
            if (r.inicio < corte || r.inicio >= hoy) continue
            val d = inicioDelDia(r.inicio)
            porDia[d] = (porDia[d] ?: 0) + 1
        }
        if (porDia.size < 3) return null to porDia.size
        val cuentas = porDia.values.map { it.toDouble() }
        return redondear(mediana(cuentas)) to porDia.size
    }

    private fun horaMediaDespertarManana(registros: List<Registro>, hoy: Instant): Instant? {
        val corte = sumarDias(hoy, -aprendDias.toLong())
        val candidatos = registros.mapNotNull { r ->
            if (r.tipo != TipoRegistro.NOCHE) return@mapNotNull null
            val fin = r.fin ?: return@mapNotNull null
            if (fin < corte || fin >= hoy) return@mapNotNull null
            val h = componenteHora(fin)
            if (h in 4..10) fin else null
        }
        if (candidatos.size < aprendMinMuestras) return null
        return promedioHoraDelDia(candidatos, hoy)
    }

    private fun promedioHoraDelDia(fechas: List<Instant>, dia: Instant): Instant? {
        if (fechas.isEmpty()) return null
        val segundos = fechas.map { f ->
            val z = f.zdt()
            (z.hour * 3600 + z.minute * 60).toDouble()
        }
        val media = segundos.sum() / segundos.size
        val mediaInt = media.toInt()
        val hora = mediaInt / 3600
        val minuto = (mediaInt % 3600) / 60
        return dia.zdt().toLocalDate().atStartOfDay(zonaMotor)
            .plusHours(hora.toLong()).plusMinutes(minuto.toLong()).toInstant()
    }
}
