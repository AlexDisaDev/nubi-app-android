package app.nubi.i18n

/**
 * Los idiomas que soporta Nubi — mismos cuatro idiomas y mismo "Automático"
 * que en iOS (ver Idioma.swift: enum IdiomaNubi). El rawValue de cada caso es
 * el mismo que en iOS y es lo que se guarda en Ajustes (DatosNubi.idioma),
 * para que un futuro traspaso de ajustes entre plataformas no tenga que
 * traducir nada.
 */
enum class IdiomaNubi(val rawValue: String) {
    SISTEMA("sistema"),
    ESPANOL("es"),
    INGLES("en"),
    ITALIANO("it"),
    FRANCES("fr");

    /** Nombre tal y como se ve en el selector de Ajustes — en el propio idioma. */
    val nombre: String
        get() = when (this) {
            SISTEMA -> "Automático"
            ESPANOL -> "Español"
            INGLES -> "English"
            ITALIANO -> "Italiano"
            FRANCES -> "Français"
        }

    companion object {
        /** Admite tanto el rawValue ("es", "en"…) como el nombre del enum, por si acaso. */
        fun desdeValor(valor: String?): IdiomaNubi =
            entries.firstOrNull { it.rawValue == valor } ?: SISTEMA
    }
}
