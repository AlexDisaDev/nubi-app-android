package app.nubi.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Traducciones de Nubi — puerto de Traducciones.swift (iOS) a Android.
 *
 * Mismo diseño que en iOS: en vez de una clave artificial por string
 * ("ajustes.cerrar_sesion"), la clave ES el texto en español tal y como ya
 * está escrito en el código ([Pantallas.kt] y compañía), y [t] busca su
 * traducción en la tabla generada a partir de Traducciones.swift
 * ([tablaTraducciones], ~566 entradas). Si no hay traducción para un texto
 * (o el idioma efectivo es español), se devuelve el propio texto español:
 * así, un texto nuevo que todavía no se ha traducido no rompe nada, solo
 * sale en español también en los demás idiomas hasta que se añada su
 * entrada en la tabla.
 *
 * `idioma` es un [mutableStateOf]: leerlo desde un composable (directamente
 * o a través de [t]) suscribe la recomposición a los cambios, igual que
 * cualquier otro estado de Compose — no hace falta pasarlo como parámetro
 * por todas partes.
 */
object L10n {

    private var _idioma by mutableStateOf(IdiomaNubi.SISTEMA)

    /** Idioma elegido en Ajustes ("Automático" por defecto). */
    var idioma: IdiomaNubi
        get() = _idioma
        set(valor) { _idioma = valor }

    /**
     * Se llama cada vez que cambia DatosNubi.idioma (ver Navegacion.kt), para
     * que lo guardado la última vez se recupere al arrancar — igual que
     * L10n.cargarGuardado() en iOS. Admite tanto el rawValue ("es", "en"…)
     * como "sistema"/null.
     */
    fun sincronizarDesdeGuardado(valorGuardado: String?) {
        idioma = IdiomaNubi.desdeValor(valorGuardado)
    }

    /**
     * Idioma efectivo: si el usuario no eligió "Automático", el elegido; si
     * eligió "Automático", el del sistema (si Android lo reporta como uno de
     * los cuatro soportados) y si no, español.
     */
    val idiomaEfectivo: IdiomaNubi
        get() {
            if (idioma != IdiomaNubi.SISTEMA) return idioma
            return when (Locale.getDefault().language.lowercase()) {
                "en" -> IdiomaNubi.INGLES
                "it" -> IdiomaNubi.ITALIANO
                "fr" -> IdiomaNubi.FRANCES
                else -> IdiomaNubi.ESPANOL
            }
        }

    private val claveIdioma: String?
        get() = when (idiomaEfectivo) {
            IdiomaNubi.INGLES -> "en"
            IdiomaNubi.ITALIANO -> "it"
            IdiomaNubi.FRANCES -> "fr"
            else -> null // español o sistema resuelto a español: no hace falta tabla
        }

    /**
     * Función principal para traducir: si no hay traducción para este texto
     * (o el idioma efectivo es español), devuelve el propio texto español.
     */
    fun t(textoEspanol: String): String {
        val clave = claveIdioma ?: return textoEspanol
        return tablaTraducciones[textoEspanol]?.get(clave) ?: textoEspanol
    }

    // -----------------------------------------------------------------
    // Frases con datos dentro que no caben en la tabla de arriba (puerto
    // literal de las funciones homónimas de Traducciones.swift).
    // -----------------------------------------------------------------

    val ahoraMismo: String
        get() = when (idiomaEfectivo) {
            IdiomaNubi.INGLES -> "just now"
            IdiomaNubi.ITALIANO -> "adesso"
            IdiomaNubi.FRANCES -> "à l'instant"
            else -> "ahora mismo"
        }

    fun enDias(n: Int): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> if (n == 1) "in $n day" else "in $n days"
        IdiomaNubi.ITALIANO -> if (n == 1) "tra $n giorno" else "tra $n giorni"
        IdiomaNubi.FRANCES -> if (n == 1) "dans $n jour" else "dans $n jours"
        else -> if (n == 1) "en $n día" else "en $n días"
    }

    fun haceMin(m: Int): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> "$m min ago"
        IdiomaNubi.ITALIANO -> "$m min fa"
        IdiomaNubi.FRANCES -> "il y a $m min"
        else -> "hace $m min"
    }

    fun haceHoras(h: Int, m: Int): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> if (m == 0) "$h h ago" else "$h h $m min ago"
        IdiomaNubi.ITALIANO -> if (m == 0) "$h h fa" else "$h h $m min fa"
        IdiomaNubi.FRANCES -> if (m == 0) "il y a $h h" else "il y a $h h $m min"
        else -> if (m == 0) "hace $h h" else "hace $h h $m min"
    }

    /** Edad legible del bebé, en días/semanas/meses/años según corresponda. */
    fun edadBebe(dias: Int, meses: Int): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> {
            if (meses < 1) {
                val w = dias / 7
                if (w <= 1) "$dias days" else "$w weeks"
            } else if (meses < 24) {
                "$meses ${if (meses == 1) "month" else "months"}"
            } else {
                val y = meses / 12
                val r = meses % 12
                if (r == 0) "$y ${if (y == 1) "year" else "years"}"
                else "$y ${if (y == 1) "year" else "years"} $r ${if (r == 1) "month" else "months"}"
            }
        }
        IdiomaNubi.ITALIANO -> {
            if (meses < 1) {
                val w = dias / 7
                if (w <= 1) "$dias giorni" else "$w settimane"
            } else if (meses < 24) {
                "$meses ${if (meses == 1) "mese" else "mesi"}"
            } else {
                val y = meses / 12
                val r = meses % 12
                if (r == 0) "$y ${if (y == 1) "anno" else "anni"}"
                else "$y ${if (y == 1) "anno" else "anni"} e $r ${if (r == 1) "mese" else "mesi"}"
            }
        }
        IdiomaNubi.FRANCES -> {
            if (meses < 1) {
                val w = dias / 7
                if (w <= 1) "$dias jours" else "$w semaines"
            } else if (meses < 24) {
                "$meses mois"
            } else {
                val y = meses / 12
                val r = meses % 12
                if (r == 0) "$y ${if (y == 1) "an" else "ans"}"
                else "$y ${if (y == 1) "an" else "ans"} et $r mois"
            }
        }
        else -> {
            if (meses < 1) {
                val w = dias / 7
                if (w <= 1) "$dias días" else "$w semanas"
            } else if (meses < 24) {
                "$meses meses"
            } else {
                val y = meses / 12
                val r = meses % 12
                if (r == 0) "$y años" else "$y años y $r meses"
            }
        }
    }

    fun despiertoDurante(duracion: String): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> "$duracion awake"
        IdiomaNubi.ITALIANO -> "$duracion sveglio"
        IdiomaNubi.FRANCES -> "$duracion éveillé"
        else -> "$duracion despierto"
    }

    fun seDespertó(nombre: String): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> "$nombre woke up"
        IdiomaNubi.ITALIANO -> "$nombre si è svegliato"
        IdiomaNubi.FRANCES -> "$nombre s'est réveillé"
        else -> "$nombre se despertó"
    }

    fun aLasHora(hora: String): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> "at $hora"
        IdiomaNubi.ITALIANO -> "alle $hora"
        IdiomaNubi.FRANCES -> "à $hora"
        else -> "a las $hora"
    }

    fun deSueno(duracion: String): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> "$duracion sleep"
        IdiomaNubi.ITALIANO -> "$duracion di sonno"
        IdiomaNubi.FRANCES -> "$duracion de sommeil"
        else -> "$duracion de sueño"
    }

    fun tomasResumen(n: Int): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> "$n ${if (n == 1) "feed" else "feeds"}"
        IdiomaNubi.ITALIANO -> "$n ${if (n == 1) "poppata" else "poppate"}"
        IdiomaNubi.FRANCES -> "$n ${if (n == 1) "tétée" else "tétées"}"
        else -> "$n ${if (n == 1) "toma" else "tomas"}"
    }

    fun sueleDormir(a: String, b: String): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> "At this age babies usually sleep between $a and $b h a day."
        IdiomaNubi.ITALIANO -> "A questa età di solito dorme tra $a e $b h al giorno."
        IdiomaNubi.FRANCES -> "À cet âge, un bébé dort généralement entre $a et $b h par jour."
        else -> "A su edad se suele dormir entre $a y $b h al día."
    }
}
