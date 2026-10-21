package app.nubi.data

/**
 * Comunidades autónomas de España (ver CalendarioVacunal.swift). "OTROS"
 * representa países sin calendario cargado: ahí la app no inventa un
 * calendario oficial, solo deja añadir vacunas a mano.
 */
enum class Comunidad(val nombre: String) {
    OTROS("Otros países"),
    COMUN("Calendario común (España)"),
    ANDALUCIA("Andalucía"),
    ARAGON("Aragón"),
    ASTURIAS("Asturias"),
    BALEARES("Illes Balears"),
    CANARIAS("Canarias"),
    CANTABRIA("Cantabria"),
    CASTILLA_LA_MANCHA("Castilla-La Mancha"),
    CASTILLA_Y_LEON("Castilla y León"),
    CATALUNA("Catalunya"),
    CEUTA("Ceuta"),
    VALENCIA("Comunitat Valenciana"),
    EXTREMADURA("Extremadura"),
    GALICIA("Galicia"),
    MADRID("Comunidad de Madrid"),
    MELILLA("Melilla"),
    MURCIA("Región de Murcia"),
    NAVARRA("Navarra"),
    PAIS_VASCO("País Vasco / Euskadi"),
    RIOJA("La Rioja");

    val esCalendarioOficial: Boolean get() = this != OTROS
}

data class ItemVacuna(val id: String, val nombre: String, val detalle: String)

data class GrupoVacunal(
    val id: String,
    val etiqueta: String,
    /** Edad en meses a la que corresponde el grupo. */
    val meses: Double,
    val items: List<ItemVacuna>
)

/**
 * Calendarios vacunales infantiles de España, curso 2026.
 *
 * Fuentes: calendario común de vacunación e inmunización a lo largo de toda
 * la vida aprobado por el Consejo Interterritorial del SNS el 12 de
 * diciembre de 2025, y el análisis comparativo de los calendarios
 * autonómicos publicado en marzo de 2026. Traducción 1:1 de
 * CalendarioVacunal.swift, con el mismo idCad estable entre comunidades
 * (`g2-hexa` es el mismo en todas) para que cambiar de comunidad no borre
 * lo que ya estaba marcado como puesto.
 *
 * Estado de la carga: hay diez comunidades con calendario propio verificado
 * (Andalucía, Aragón, Asturias, Castilla-La Mancha, Castilla y León,
 * Catalunya, Galicia, Madrid, Comunitat Valenciana y País Vasco). Las demás
 * muestran el calendario común y la app lo dice en pantalla.
 */
object CalendarioVacunal {

    private data class V(val cod: String, val nombre: String, val detalle: String)

    private val vrs = V("vrs", "Anticuerpo frente al VRS", "Nirsevimab, en su primera temporada de virus respiratorio sincitial.")
    private val hbNacer = V("hb", "Hepatitis B al nacer", "Solo si la madre es portadora o no se le hizo el cribado.")
    private val hexa = V("hexa", "Hexavalente", "Difteria, tétanos, tosferina, polio, Hib y hepatitis B.")
    private val vnc = V("vnc", "Neumococo", "Vacuna conjugada.")
    private val vnc20 = V("vnc", "Neumococo (VNC20)", "Vacuna conjugada de 20 serotipos.")
    private val menb = V("menb", "Meningococo B", "")
    private val menc = V("menc", "Meningococo C", "")
    private val menacwy = V("menacwy", "Meningococo ACWY", "")
    private val rota = V("rota", "Rotavirus", "Se administra por boca.")
    private val tv = V("tv", "Triple vírica", "Sarampión, rubéola y parotiditis.")
    private val varicela = V("var", "Varicela", "")
    private val tetra = V("srpv", "Tetravírica", "Triple vírica y varicela en una sola vacuna.")
    private val dtpaVpi = V("dtpavpi", "Refuerzo DTPa-VPI", "Difteria, tétanos, tosferina y polio.")
    private val dtpa = V("dtpa", "Refuerzo difteria-tétanos-tosferina", "")
    private val vpi = V("vpi", "Polio", "")
    private val tdpa = V("tdpa", "dTpa", "Refuerzo de difteria, tétanos y tosferina.")
    private val td = V("td", "Tétanos y difteria", "")
    private val vph = V("vph", "Virus del papiloma humano", "Una dosis, en niñas y niños.")
    private val gripe = V("gripe", "Gripe", "Campaña estacional.")
    private val hepA = V("hepa", "Hepatitis A", "")

    /** Añade el número de dosis al nombre sin cambiar el identificador. */
    private fun d(v: V, n: Int): V = V(v.cod, "${v.nombre} (${n}ª)", v.detalle)

    private fun nota(v: V, texto: String): V = V(v.cod, v.nombre, texto)

    private fun gr(gid: String, etiqueta: String, meses: Double, vs: List<V>): GrupoVacunal =
        GrupoVacunal(
            id = gid,
            etiqueta = etiqueta,
            meses = meses,
            items = vs.map { ItemVacuna(id = "$gid-${it.cod}", nombre = it.nombre, detalle = it.detalle) }
        )

    // MARK: Calendario común del SNS (2026)
    private val comun: List<GrupoVacunal> = listOf(
        gr("g0", "Al nacer", 0.0, listOf(vrs, hbNacer)),
        gr("g2", "2 meses", 2.0, listOf(d(hexa, 1), d(vnc, 1), d(menb, 1), d(rota, 1))),
        gr("g4", "4 meses", 4.0, listOf(d(hexa, 2), d(vnc, 2), d(menb, 2), d(rota, 2), menc)),
        gr("g6", "6 meses", 6.0, listOf(nota(gripe, "Una dosis por temporada, de 6 a 59 meses."))),
        gr("g11", "11 meses", 11.0, listOf(d(hexa, 3), d(vnc, 3))),
        gr("g12", "12 meses", 12.0, listOf(d(tv, 1), d(menb, 3), nota(menc, "Segunda dosis de meningococo C."))),
        gr("g15", "15 meses", 15.0, listOf(d(varicela, 1))),
        gr("g42", "3 – 4 años", 42.0, listOf(d(tv, 2), d(varicela, 2))),
        gr("g72", "6 años", 72.0, listOf(dtpaVpi)),
        gr("g144", "12 años", 144.0, listOf(vph, menacwy)),
        gr("g168", "14 años", 168.0, listOf(td))
    )

    // MARK: Comunidad de Madrid
    private val madrid: List<GrupoVacunal> = listOf(
        gr("g0", "Al nacer", 0.0, listOf(vrs, hbNacer)),
        gr("g2", "2 meses", 2.0, listOf(d(hexa, 1), d(vnc, 1), d(menb, 1), d(rota, 1))),
        gr("g4", "4 meses", 4.0, listOf(d(hexa, 2), d(vnc, 2), d(menb, 2), d(rota, 2), menc)),
        gr("g6", "6 meses", 6.0, listOf(nota(gripe, "Una dosis por temporada, de 6 a 59 meses."))),
        gr("g11", "11 meses", 11.0, listOf(d(hexa, 3), d(vnc, 3))),
        gr(
            "g12", "12 meses", 12.0, listOf(
                d(tv, 1),
                d(menb, 3),
                nota(menacwy, "Desde el 1 de enero de 2026 sustituye a la dosis de MenC.")
            )
        ),
        gr("g15", "15 meses", 15.0, listOf(d(varicela, 1))),
        gr("g36", "3 años", 36.0, listOf(nota(tetra, "Segunda dosis de triple vírica y varicela juntas."))),
        gr("g72", "6 años", 72.0, listOf(nota(dtpaVpi, "Los vacunados con pauta 3+1 reciben dTpa de baja carga."))),
        gr("g144", "12 años", 144.0, listOf(menacwy, vph)),
        gr("g168", "14 años", 168.0, listOf(tdpa))
    )

    // MARK: Andalucía
    private val andalucia: List<GrupoVacunal> = listOf(
        gr("g0", "Al nacer", 0.0, listOf(vrs, hbNacer)),
        gr(
            "g2", "2 meses", 2.0, listOf(
                d(hexa, 1), d(vnc20, 1), d(menb, 1),
                nota(rota, "Dos dosis por boca, a los 2 y 4 meses.")
            )
        ),
        gr("g4", "4 meses", 4.0, listOf(d(hexa, 2), d(vnc20, 2), d(menb, 2), d(rota, 2), d(menacwy, 1))),
        gr("g6", "6 meses", 6.0, listOf(d(vnc20, 3), nota(gripe, "Una dosis por temporada, de 6 a 59 meses."))),
        gr("g11", "11 meses", 11.0, listOf(d(hexa, 3), d(vnc20, 4))),
        gr("g12", "12 meses", 12.0, listOf(d(tv, 1), d(menacwy, 2))),
        gr("g15", "15 meses", 15.0, listOf(d(varicela, 1), d(menb, 3))),
        gr(
            "g24", "2 – 3 años", 30.0, listOf(
                nota(tv, "Segunda dosis, durante 2026 a los 2 y 3 años."),
                nota(varicela, "Segunda dosis, durante 2026 a los 2 y 3 años.")
            )
        ),
        gr("g72", "6 años", 72.0, listOf(nota(dtpaVpi, "Los vacunados con pauta 3+1 reciben Tdpa sin polio."))),
        gr("g144", "12 años", 144.0, listOf(menacwy, vph)),
        gr("g168", "14 años", 168.0, listOf(nota(tdpa, "A quienes no hayan recibido Tdpa a partir de los 10 años.")))
    )

    // MARK: País Vasco
    private val paisVasco: List<GrupoVacunal> = listOf(
        gr(
            "g2", "2 meses", 2.0, listOf(
                d(hexa, 1), d(vnc, 1), d(menb, 1),
                nota(rota, "Pauta entre los 2 y los 6 meses.")
            )
        ),
        gr("g4", "4 meses", 4.0, listOf(d(hexa, 2), d(vnc, 2), d(menb, 2), d(rota, 2), d(menacwy, 1))),
        gr("g6", "6 meses", 6.0, listOf(nota(gripe, "Una dosis por temporada, de 6 a 59 meses."))),
        gr("g11", "11 meses", 11.0, listOf(d(hexa, 3), d(vnc, 3))),
        gr("g12", "12 meses", 12.0, listOf(d(tv, 1), d(menb, 3), d(menacwy, 2))),
        gr("g15", "15 meses", 15.0, listOf(d(varicela, 1))),
        gr("g48", "4 años", 48.0, listOf(d(tv, 2), d(varicela, 2))),
        gr("g72", "6 años", 72.0, listOf(dtpaVpi)),
        gr("g144", "12 años", 144.0, listOf(vph, menacwy)),
        gr("g192", "16 años", 192.0, listOf(td, tdpa))
    )

    // MARK: Catalunya
    private val cataluna: List<GrupoVacunal> = listOf(
        gr("g0", "Al nacer", 0.0, listOf(nota(vrs, "A todos los menores de 12 meses en su primera temporada."))),
        gr("g2", "2 meses", 2.0, listOf(d(hexa, 1), d(vnc, 1), d(menb, 1), d(rota, 1))),
        gr("g4", "4 meses", 4.0, listOf(d(hexa, 2), d(vnc, 2), d(menb, 2), d(rota, 2), menc)),
        gr(
            "g6", "6 meses", 6.0, listOf(
                nota(rota, "Tercera dosis, según la vacuna utilizada."),
                nota(gripe, "Una dosis por temporada, de 6 a 59 meses.")
            )
        ),
        gr("g11", "11 meses", 11.0, listOf(d(hexa, 3), d(vnc, 3))),
        gr("g12", "12 meses", 12.0, listOf(d(tv, 1), d(menacwy, 1), d(menb, 3))),
        gr("g15", "15 meses", 15.0, listOf(d(varicela, 1), d(hepA, 1))),
        gr("g36", "3 años", 36.0, listOf(d(tv, 2), d(varicela, 2))),
        gr("g72", "6 años", 72.0, listOf(dtpaVpi, d(hepA, 2))),
        gr(
            "g132", "11 – 12 años", 138.0, listOf(
                tdpa, d(menacwy, 2), vph,
                nota(varicela, "Solo a quienes no estén inmunizados."),
                nota(hepA, "Solo a quienes no estén inmunizados.")
            )
        )
    )

    // MARK: Galicia
    private val galicia: List<GrupoVacunal> = listOf(
        gr("g0", "Al nacer", 0.0, listOf(vrs, hbNacer)),
        gr(
            "g2", "2 meses", 2.0, listOf(
                d(hexa, 1), d(vnc20, 1), d(menb, 1),
                nota(rota, "Dos dosis, a los 2 y 4 meses, en nacidos desde agosto de 2023.")
            )
        ),
        gr("g4", "4 meses", 4.0, listOf(d(hexa, 2), d(vnc20, 2), d(menb, 2), d(rota, 2), d(menacwy, 1))),
        gr("g6", "6 meses", 6.0, listOf(d(vnc20, 3), nota(gripe, "Una dosis por temporada, de 6 a 59 meses."))),
        gr("g11", "11 meses", 11.0, listOf(d(hexa, 3), d(vnc20, 4))),
        gr("g12", "12 meses", 12.0, listOf(d(tv, 1), d(menb, 3), d(menacwy, 2))),
        gr("g15", "15 meses", 15.0, listOf(d(varicela, 1))),
        gr("g36", "3 años", 36.0, listOf(nota(tetra, "Segunda dosis de triple vírica y varicela juntas."))),
        gr("g72", "6 años", 72.0, listOf(nota(dtpaVpi, "Los vacunados con pauta 2, 4, 6 y 18 meses reciben dTpa."))),
        gr(
            "g144", "12 años", 144.0, listOf(
                menacwy, vph,
                nota(varicela, "A quienes no la hayan pasado ni tengan las dos dosis.")
            )
        ),
        gr("g168", "14 años", 168.0, listOf(td))
    )

    // MARK: Comunitat Valenciana
    private val valencia: List<GrupoVacunal> = listOf(
        gr("g0", "Al nacer", 0.0, listOf(vrs, hbNacer)),
        gr("g2", "2 meses", 2.0, listOf(d(hexa, 1), d(vnc20, 1), d(menb, 1), d(rota, 1))),
        gr("g4", "4 meses", 4.0, listOf(d(hexa, 2), d(vnc20, 2), d(rota, 2), d(menacwy, 1))),
        gr(
            "g6", "6 meses", 6.0, listOf(
                d(vnc20, 3),
                nota(rota, "Tercera dosis, según la vacuna utilizada."),
                nota(gripe, "Una dosis por temporada, de 6 a 71 meses.")
            )
        ),
        gr("g11", "11 meses", 11.0, listOf(d(hexa, 3), d(vnc20, 4))),
        gr("g12", "12 meses", 12.0, listOf(d(tv, 1), d(menacwy, 2))),
        gr("g15", "15 meses", 15.0, listOf(d(varicela, 1))),
        gr("g24", "2 – 4 años", 36.0, listOf(nota(tetra, "Segunda dosis de triple vírica y varicela juntas."))),
        gr("g60", "5 – 6 años", 66.0, listOf(dtpaVpi)),
        gr("g144", "12 años", 144.0, listOf(vph, menacwy)),
        gr("g168", "14 años", 168.0, listOf(td))
    )

    // MARK: Castilla y León
    private val castillaYLeon: List<GrupoVacunal> = listOf(
        gr("g0", "Al nacer", 0.0, listOf(vrs, hbNacer)),
        gr("g2", "2 meses", 2.0, listOf(d(hexa, 1), d(vnc20, 1), d(menb, 1), d(rota, 1))),
        gr("g3", "3 meses", 3.0, listOf(nota(rota, "Segunda dosis, según la vacuna utilizada."))),
        gr("g4", "4 meses", 4.0, listOf(d(hexa, 2), d(vnc20, 2), d(menb, 2), d(rota, 3), d(menacwy, 1))),
        gr(
            "g6", "6 meses", 6.0, listOf(
                d(vnc20, 3),
                nota(gripe, "Anual en campaña: de 6 a 23 meses inyectable, de 2 a 11 años intranasal.")
            )
        ),
        gr("g11", "11 meses", 11.0, listOf(d(hexa, 3), d(vnc20, 4))),
        gr("g12", "12 meses", 12.0, listOf(d(tv, 1), d(menacwy, 2))),
        gr("g15", "15 meses", 15.0, listOf(d(varicela, 1))),
        gr("g36", "3 años", 36.0, listOf(d(tv, 2), d(varicela, 2))),
        gr("g72", "6 años", 72.0, listOf(dtpa, vpi)),
        gr(
            "g144", "12 años", 144.0, listOf(
                menacwy,
                nota(vph, "VPH9, una dosis, en niñas y niños."),
                nota(varicela, "Solo a quienes no la hayan pasado ni estén vacunados.")
            )
        ),
        gr("g168", "14 años", 168.0, listOf(td))
    )

    // MARK: Castilla-La Mancha
    private val castillaLaMancha: List<GrupoVacunal> = listOf(
        gr("g0", "Al nacer", 0.0, listOf(vrs, hbNacer)),
        gr("g2", "2 meses", 2.0, listOf(d(hexa, 1), d(vnc, 1), d(menb, 1), d(rota, 1))),
        gr("g4", "4 meses", 4.0, listOf(d(hexa, 2), d(vnc, 2), d(rota, 2), d(menacwy, 1))),
        gr(
            "g6", "6 meses", 6.0, listOf(
                d(vnc, 3), d(rota, 3),
                nota(gripe, "Anual, desde los 5 meses hasta los 5 años.")
            )
        ),
        gr("g11", "11 meses", 11.0, listOf(d(hexa, 3), d(vnc, 4))),
        gr("g12", "12 meses", 12.0, listOf(d(tv, 1), d(menacwy, 2))),
        gr("g15", "15 meses", 15.0, listOf(d(varicela, 1))),
        gr("g48", "4 años", 48.0, listOf(d(tv, 2), d(varicela, 2))),
        gr("g72", "6 años", 72.0, listOf(dtpa, vpi)),
        gr(
            "g144", "12 años", 144.0, listOf(
                menacwy, vph,
                nota(varicela, "Solo a quienes no estén inmunizados.")
            )
        ),
        gr("g168", "14 años", 168.0, listOf(td))
    )

    // MARK: Asturias
    private val asturias: List<GrupoVacunal> = listOf(
        gr(
            "g2", "2 meses", 2.0, listOf(
                d(hexa, 1), d(vnc, 1),
                nota(menb, "Meningococo B, en nacidos desde el 1 de noviembre de 2022."),
                d(rota, 1)
            )
        ),
        gr("g4", "4 meses", 4.0, listOf(d(hexa, 2), d(vnc, 2), d(menb, 2), d(rota, 2), d(menacwy, 1))),
        gr(
            "g6", "6 meses", 6.0, listOf(
                d(vnc, 3),
                nota(rota, "Tercera dosis, según la vacuna utilizada."),
                nota(gripe, "Una dosis por temporada, de 6 a 59 meses.")
            )
        ),
        gr("g11", "11 meses", 11.0, listOf(d(hexa, 3), d(vnc, 4))),
        gr("g12", "12 meses", 12.0, listOf(d(tv, 1), d(menb, 3), d(menacwy, 2))),
        gr("g15", "15 meses", 15.0, listOf(d(varicela, 1))),
        gr("g36", "3 años", 36.0, listOf(d(tv, 2), d(varicela, 2))),
        gr("g72", "6 años", 72.0, listOf(dtpaVpi)),
        gr(
            "g120", "10 años", 120.0, listOf(
                nota(vph, "Una dosis, en niñas y niños."),
                nota(varicela, "Solo a quienes no la hayan pasado ni estén vacunados.")
            )
        ),
        gr("g156", "13 años", 156.0, listOf(tdpa, menacwy))
    )

    // MARK: Aragón
    private val aragon: List<GrupoVacunal> = listOf(
        gr("g0", "Al nacer", 0.0, listOf(vrs, hbNacer)),
        gr(
            "g2", "2 meses", 2.0, listOf(
                d(hexa, 1), d(vnc, 1), d(menb, 1),
                nota(rota, "En nacidos desde el 1 de agosto de 2024.")
            )
        ),
        gr("g4", "4 meses", 4.0, listOf(d(hexa, 2), d(vnc, 2), d(menb, 2), d(rota, 2), d(menacwy, 1))),
        gr(
            "g6", "6 meses", 6.0, listOf(
                d(vnc, 3),
                nota(rota, "Tercera dosis, según la vacuna utilizada."),
                nota(gripe, "Una dosis por campaña, de 6 a 59 meses.")
            )
        ),
        gr("g11", "11 meses", 11.0, listOf(d(hexa, 3), d(vnc, 4))),
        gr(
            "g12", "12 meses", 12.0, listOf(
                d(tv, 1), d(menacwy, 2),
                nota(menb, "Tercera dosis, en nacidos desde el 1 de enero de 2023.")
            )
        ),
        gr("g15", "15 meses", 15.0, listOf(d(varicela, 1))),
        gr("g36", "3 años", 36.0, listOf(d(tv, 2), d(varicela, 2))),
        gr("g72", "6 años", 72.0, listOf(nota(dtpaVpi, "Los vacunados con pauta 3+1 reciben dTpa sin polio."))),
        gr(
            "g144", "12 años", 144.0, listOf(
                menacwy, vph,
                nota(varicela, "Solo a quienes no la hayan pasado ni estén vacunados.")
            )
        ),
        gr("g168", "14 años", 168.0, listOf(td))
    )

    // MARK: Índice
    private val porComunidad: Map<Comunidad, List<GrupoVacunal>> = mapOf(
        Comunidad.MADRID to madrid,
        Comunidad.ANDALUCIA to andalucia,
        Comunidad.PAIS_VASCO to paisVasco,
        Comunidad.CATALUNA to cataluna,
        Comunidad.GALICIA to galicia,
        Comunidad.VALENCIA to valencia,
        Comunidad.CASTILLA_Y_LEON to castillaYLeon,
        Comunidad.CASTILLA_LA_MANCHA to castillaLaMancha,
        Comunidad.ASTURIAS to asturias,
        Comunidad.ARAGON to aragon
    )

    /** Comunidades con calendario propio ya verificado. */
    val comunidadesCargadas: Set<Comunidad> get() = porComunidad.keys

    // MARK: API

    fun grupos(comunidad: Comunidad): List<GrupoVacunal> {
        if (comunidad == Comunidad.OTROS) return emptyList()
        return porComunidad[comunidad] ?: comun
    }

    fun totalItems(comunidad: Comunidad): Int = grupos(comunidad).sumOf { it.items.size }

    fun grupoActual(mesesDelBebe: Double, comunidad: Comunidad): GrupoVacunal? =
        grupos(comunidad).lastOrNull { it.meses <= mesesDelBebe + 0.5 }

    fun siguienteGrupo(mesesDelBebe: Double, comunidad: Comunidad): GrupoVacunal? =
        grupos(comunidad).firstOrNull { it.meses > mesesDelBebe + 0.5 }

    /** Aviso que acompaña al listado y que explica de dónde sale. */
    fun aviso(comunidad: Comunidad): String = when {
        comunidad == Comunidad.OTROS ->
            "Cada país tiene su propio calendario de vacunación. Usa la sección «Añadidas por ti» para guardar las vacunas de tu bebé."

        comunidad == Comunidad.COMUN ->
            "Calendario común del Sistema Nacional de Salud para 2026. Tu comunidad puede adelantar o añadir vacunas: elígela arriba."

        comunidadesCargadas.contains(comunidad) ->
            "Calendario de ${comunidad.nombre} para 2026. Contrástalo con la cartilla: las pautas dependen a menudo de la fecha de nacimiento."

        else ->
            "Todavía no tengo cargado el calendario propio de ${comunidad.nombre}, así que se muestra el común. Añade abajo lo que falte."
    }

    val avisoGeneral: String =
        "El calendario es orientativo y se actualiza cada año. La referencia válida es siempre la cartilla de tu bebé y tu enfermera de pediatría."
}

/**
 * Nombres habituales para el buscador de "añadir vacuna". No es una lista
 * cerrada: siempre se puede escribir una a mano.
 */
object CatalogoVacunas {

    val nombres: List<String> = listOf(
        "Hexavalente (DTPa-VPI-Hib-HB)",
        "Pentavalente (DTPa-VPI-Hib)",
        "Difteria, tétanos y tosferina (DTPa)",
        "dTpa (refuerzo)",
        "Tétanos y difteria (Td)",
        "Polio (VPI)",
        "Haemophilus influenzae b (Hib)",
        "Hepatitis A",
        "Hepatitis B",
        "Neumococo conjugada (VNC13)",
        "Neumococo conjugada (VNC20)",
        "Meningococo B",
        "Meningococo C",
        "Meningococo ACWY",
        "Rotavirus",
        "Triple vírica (sarampión, rubéola, paperas)",
        "Tetravírica (triple vírica y varicela)",
        "Varicela",
        "Gripe",
        "COVID-19",
        "Virus del papiloma humano (VPH)",
        "Anticuerpo frente al VRS (nirsevimab)",
        "BCG (tuberculosis)",
        "Fiebre tifoidea",
        "Fiebre amarilla",
        "Rabia",
        "Encefalitis japonesa",
        "Encefalitis centroeuropea",
        "Cólera"
    )

    fun buscar(texto: String): List<String> {
        val q = normalizar(texto.trim())
        if (q.isEmpty()) return nombres
        return nombres.filter { normalizar(it).contains(q) }
    }

    private fun normalizar(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .lowercase()
}
