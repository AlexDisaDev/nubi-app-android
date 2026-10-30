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

    /**
     * Locale de Java para formatear nombres de día/mes y mayúsculas/
     * minúsculas acorde al idioma efectivo — Android por sí solo usaría el
     * locale del sistema, no el elegido en Ajustes (que puede ser distinto
     * si el usuario forzó un idioma).
     */
    val localeJava: Locale
        get() = when (idiomaEfectivo) {
            IdiomaNubi.INGLES -> Locale("en", "US")
            IdiomaNubi.ITALIANO -> Locale("it", "IT")
            IdiomaNubi.FRANCES -> Locale("fr", "FR")
            else -> Locale("es", "ES")
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
        val entrada = tablaTraducciones[textoEspanol] ?: tablaTraduccionesExtra[textoEspanol]
        return entrada?.get(clave) ?: textoEspanol
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

    /** "5 d de vida"/"3 sem de vida"/"2 m de vida" — edad abreviada. */
    fun textoEdadCorta(dias: Int): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> when {
            dias < 14 -> "$dias d old"
            dias < 61 -> "${dias / 7} wk old"
            else -> "${(dias / 30.44).toInt()} mo old"
        }
        IdiomaNubi.ITALIANO -> when {
            dias < 14 -> "$dias g di vita"
            dias < 61 -> "${dias / 7} sett di vita"
            else -> "${(dias / 30.44).toInt()} m di vita"
        }
        IdiomaNubi.FRANCES -> when {
            dias < 14 -> "$dias j de vie"
            dias < 61 -> "${dias / 7} sem de vie"
            else -> "${(dias / 30.44).toInt()} m de vie"
        }
        else -> when {
            dias < 14 -> "$dias d de vida"
            dias < 61 -> "${dias / 7} sem de vida"
            else -> "${(dias / 30.44).toInt()} m de vida"
        }
    }

    fun nMeses(n: Int): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> if (n == 1) "1 month" else "$n months"
        IdiomaNubi.ITALIANO -> if (n == 1) "1 mese" else "$n mesi"
        IdiomaNubi.FRANCES -> if (n == 1) "1 mois" else "$n mois"
        else -> if (n == 1) "1 mes" else "$n meses"
    }

    fun nSemanas(n: Int): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> if (n == 1) "1 week" else "$n weeks"
        IdiomaNubi.ITALIANO -> if (n == 1) "1 settimana" else "$n settimane"
        IdiomaNubi.FRANCES -> if (n == 1) "1 semaine" else "$n semaines"
        else -> if (n == 1) "1 semana" else "$n semanas"
    }

    fun medidasAnotadas(n: Int): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> if (n == 1) "1 measurement logged" else "$n measurements logged"
        IdiomaNubi.ITALIANO -> if (n == 1) "1 misura registrata" else "$n misure registrate"
        IdiomaNubi.FRANCES -> if (n == 1) "1 mesure enregistrée" else "$n mesures enregistrées"
        else -> if (n == 1) "1 medida anotada" else "$n medidas anotadas"
    }

    /** Puerto de aúnNoHayMedida(_:) de Traducciones.swift — construcción
     *  gramatical distinta por idioma, no una simple concatenación. Recibe
     *  el título en español de la magnitud (Peso/Estatura/Cabeza). */
    fun aunNoHayMedida(magnitudTitulo: String): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> {
            val n = when (magnitudTitulo) {
                "Peso" -> "weight"
                "Estatura" -> "height"
                else -> "head measurements"
            }
            "No $n yet"
        }
        IdiomaNubi.ITALIANO -> {
            val n = when (magnitudTitulo) {
                "Peso" -> "dato sul peso"
                "Estatura" -> "dato sull'altezza"
                else -> "dato sulla circonferenza cranica"
            }
            "Ancora nessun $n"
        }
        IdiomaNubi.FRANCES -> {
            val n = when (magnitudTitulo) {
                "Peso" -> "poids"
                "Estatura" -> "taille"
                else -> "périmètre crânien"
            }
            "Pas encore de $n"
        }
        else -> "Aún no hay ${magnitudTitulo.lowercase()}"
    }

    fun vacunasMarcadas(puestas: Int, total: Int): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> "$puestas of $total given"
        IdiomaNubi.ITALIANO -> "$puestas di $total fatte"
        IdiomaNubi.FRANCES -> "$puestas sur $total faits"
        else -> "$puestas de $total marcadas"
    }

    fun citasPendientes(n: Int): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> if (n == 1) "1 appointment pending" else "$n appointments pending"
        IdiomaNubi.ITALIANO -> if (n == 1) "1 visita in sospeso" else "$n visite in sospeso"
        IdiomaNubi.FRANCES -> if (n == 1) "1 rendez-vous à venir" else "$n rendez-vous à venir"
        else -> if (n == 1) "1 cita pendiente" else "$n citas pendientes"
    }

    fun enfermedadesPasadas(n: Int): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> if (n == 1) "1 past illness" else "$n past illnesses"
        IdiomaNubi.ITALIANO -> if (n == 1) "1 malattia passata" else "$n malattie passate"
        IdiomaNubi.FRANCES -> if (n == 1) "1 maladie passée" else "$n maladies passées"
        else -> if (n == 1) "1 enfermedad pasada" else "$n enfermedades pasadas"
    }

    fun enfermedadesResumen(activas: Int, total: Int): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> "$activas ongoing · $total total"
        IdiomaNubi.ITALIANO -> "$activas in corso · $total in totale"
        IdiomaNubi.FRANCES -> "$activas en cours · $total au total"
        else -> "$activas en curso · $total en total"
    }

    fun vacunasTotalTexto(puestas: Int, total: Int): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> if (puestas == 0) (if (total == 1) "1 vaccine" else "$total vaccines") else "$puestas of $total given"
        IdiomaNubi.ITALIANO -> if (puestas == 0) (if (total == 1) "1 vaccino" else "$total vaccini") else "$puestas di $total fatti"
        IdiomaNubi.FRANCES -> if (puestas == 0) (if (total == 1) "1 vaccin" else "$total vaccins") else "$puestas sur $total faits"
        else -> if (puestas == 0) (if (total == 1) "1 vacuna" else "$total vacunas") else "$puestas de $total puestas"
    }

    fun revisionCon(tipo: String): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> "Visit with $tipo"
        IdiomaNubi.ITALIANO -> "Visita con $tipo"
        IdiomaNubi.FRANCES -> "Rendez-vous avec $tipo"
        else -> "Revisión con $tipo"
    }

    fun nEntradas(n: Int): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> if (n == 1) "1 entry" else "$n entries"
        IdiomaNubi.ITALIANO -> if (n == 1) "1 voce" else "$n voci"
        IdiomaNubi.FRANCES -> if (n == 1) "1 entrée" else "$n entrées"
        else -> if (n == 1) "1 entrada" else "$n entradas"
    }

    fun nHitos(n: Int): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> if (n == 1) "1 milestone" else "$n milestones"
        IdiomaNubi.ITALIANO -> if (n == 1) "1 tappa" else "$n tappe"
        IdiomaNubi.FRANCES -> if (n == 1) "1 étape" else "$n étapes"
        else -> if (n == 1) "1 hito" else "$n hitos"
    }

    /** "5 d"/"3 m"/"2 a" — edad abreviada en la fecha de una entrada del diario. */
    fun textoEdadAbrev(dias: Int): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> when {
            dias < 31 -> "$dias d"
            else -> { val meses = (dias / 30.44).toInt(); if (meses < 24) "$meses mo" else "${meses / 12} y" }
        }
        IdiomaNubi.ITALIANO -> when {
            dias < 31 -> "$dias g"
            else -> { val meses = (dias / 30.44).toInt(); if (meses < 24) "$meses m" else "${meses / 12} a" }
        }
        IdiomaNubi.FRANCES -> when {
            dias < 31 -> "$dias j"
            else -> { val meses = (dias / 30.44).toInt(); if (meses < 24) "$meses m" else "${meses / 12} a" }
        }
        else -> when {
            dias < 31 -> "$dias d"
            else -> { val meses = (dias / 30.44).toInt(); if (meses < 24) "$meses m" else "${meses / 12} a" }
        }
    }

    fun nExtracciones(n: Int): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> if (n == 1) "1 pumping session" else "$n pumping sessions"
        IdiomaNubi.ITALIANO -> if (n == 1) "1 estrazione" else "$n estrazioni"
        IdiomaNubi.FRANCES -> if (n == 1) "1 extraction" else "$n extractions"
        else -> if (n == 1) "1 extracción" else "$n extracciones"
    }

    fun nUsos(n: Int): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> if (n == 1) "1 use" else "$n uses"
        IdiomaNubi.ITALIANO -> if (n == 1) "1 utilizzo" else "$n utilizzi"
        IdiomaNubi.FRANCES -> if (n == 1) "1 utilisation" else "$n utilisations"
        else -> if (n == 1) "1 uso" else "$n usos"
    }

    fun duraFrigorifico(dias: Int): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> "Lasts up to $dias days in the fridge"
        IdiomaNubi.ITALIANO -> "Dura fino a $dias giorni in frigorifero"
        IdiomaNubi.FRANCES -> "Se conserve jusqu'à $dias jours au réfrigérateur"
        else -> "Dura hasta $dias días en el frigorífico"
    }

    fun duraCongelador(meses: Int): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> "Lasts up to $meses months in the freezer"
        IdiomaNubi.ITALIANO -> "Dura fino a $meses mesi nel congelatore"
        IdiomaNubi.FRANCES -> "Se conserve jusqu'à $meses mois au congélateur"
        else -> "Dura hasta $meses meses en el congelador"
    }

    fun diasRestantesPrueba(dias: Long): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> "$dias trial ${if (dias == 1L) "day" else "days"} left"
        IdiomaNubi.ITALIANO -> "Ti restano $dias giorni di prova"
        IdiomaNubi.FRANCES -> "Il vous reste $dias jours d'essai"
        else -> "Te quedan $dias días de prueba"
    }

    /**
     * Edad corta (días/semanas/meses, sin resto) — usada en la cabecera del
     * bebé. Sin equivalente directo en iOS: escrita a mano siguiendo el
     * mismo estilo que el resto de funciones de esta clase.
     */
    fun textoEdad(dias: Int): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> when {
            dias < 14 -> if (dias == 1) "1 day" else "$dias days"
            dias < 61 -> "${dias / 7} weeks"
            else -> "${(dias / 30.44).toInt()} months"
        }
        IdiomaNubi.ITALIANO -> when {
            dias < 14 -> if (dias == 1) "1 giorno" else "$dias giorni"
            dias < 61 -> "${dias / 7} settimane"
            else -> "${(dias / 30.44).toInt()} mesi"
        }
        IdiomaNubi.FRANCES -> when {
            dias < 14 -> if (dias == 1) "1 jour" else "$dias jours"
            dias < 61 -> "${dias / 7} semaines"
            else -> "${(dias / 30.44).toInt()} mois"
        }
        else -> when {
            dias < 14 -> if (dias == 1) "1 día" else "$dias días"
            dias < 61 -> "${dias / 7} semanas"
            else -> "${(dias / 30.44).toInt()} meses"
        }
    }

    /**
     * Edad detallada (días/semanas/meses y resto en días) — más precisa que
     * [textoEdad], usada en la ficha del bebé. Tampoco tiene equivalente
     * directo en iOS.
     */
    fun textoEdadDetallada(dias: Int): String = when (idiomaEfectivo) {
        IdiomaNubi.INGLES -> when {
            dias < 14 -> if (dias == 1) "1 day" else "$dias days"
            dias < 61 -> "${dias / 7} weeks"
            else -> {
                val meses = dias / 30
                val resto = dias % 30
                if (resto == 0) "$meses ${if (meses == 1) "month" else "months"}"
                else "$meses ${if (meses == 1) "month" else "months"} $resto ${if (resto == 1) "day" else "days"}"
            }
        }
        IdiomaNubi.ITALIANO -> when {
            dias < 14 -> if (dias == 1) "1 giorno" else "$dias giorni"
            dias < 61 -> "${dias / 7} settimane"
            else -> {
                val meses = dias / 30
                val resto = dias % 30
                if (resto == 0) "$meses ${if (meses == 1) "mese" else "mesi"}"
                else "$meses ${if (meses == 1) "mese" else "mesi"} e $resto ${if (resto == 1) "giorno" else "giorni"}"
            }
        }
        IdiomaNubi.FRANCES -> when {
            dias < 14 -> if (dias == 1) "1 jour" else "$dias jours"
            dias < 61 -> "${dias / 7} semaines"
            else -> {
                val meses = dias / 30
                val resto = dias % 30
                if (resto == 0) "$meses mois"
                else "$meses mois et $resto ${if (resto == 1) "jour" else "jours"}"
            }
        }
        else -> when {
            dias < 14 -> if (dias == 1) "1 día" else "$dias días"
            dias < 61 -> "${dias / 7} semanas"
            else -> {
                val meses = dias / 30
                val resto = dias % 30
                if (resto == 0) "$meses meses" else "$meses meses y $resto días"
            }
        }
    }
}
