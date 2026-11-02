package app.nubi.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Todo lo que Nubi guarda de un bebé, en un solo objeto.
 *
 * Es el equivalente de la struct "Disco" de Almacen.swift: un único
 * documento con todo dentro, que se escribe entero en cada cambio. Para el
 * volumen de datos de esta app (unos miles de registros como mucho) es más
 * simple y más robusto que una base de datos local, y además se parece a lo
 * que ya hace iOS.
 */
@Serializable
data class DatosNubi(
    val version: Int = 1,
    val bebe: Bebe? = null,
    val registros: List<Registro> = emptyList(),
    val medicinas: List<Medicina> = emptyList(),
    val medidas: List<Medida> = emptyList(),
    val citas: List<Cita> = emptyList(),
    val vacunas: List<EstadoVacuna> = emptyList(),
    val gruposVacunas: List<GrupoVacunas> = emptyList(),
    val vacunasPropias: List<VacunaPropia> = emptyList(),
    val diario: List<EntradaDiario> = emptyList(),
    val extracciones: List<ExtraccionLeche> = emptyList(),
    val usosLeche: List<UsoLeche> = emptyList(),
    val comidas: List<ComidaComplementaria> = emptyList(),
    val enfermedades: List<Enfermedad> = emptyList(),

    /**
     * Estado de la cuenta (prueba/premium) leído de la tabla "perfiles".
     * Nunca se escribe desde aquí ni se marca como pendiente: solo lo
     * cambia el servidor (ver Modelos.kt).
     */
    val perfil: Perfil? = null,

    /** Hora del despertar marcado a mano (iOS: despertarManual). */
    val despertarManual: Fecha? = null,

    /**
     * Tema elegido en Ajustes: "sistema", "claro" u "oscuro".
     * Es una preferencia de este móvil, no se sincroniza.
     */
    val tema: String = "sistema",

    /**
     * Idioma elegido en Ajustes (rawValue de IdiomaNubi — ver
     * app.nubi.i18n.Idioma.kt): "sistema", "es", "en", "it" o "fr". Igual que
     * el tema, es una preferencia de este móvil, no se sincroniza. Con
     * "sistema" (el valor por defecto) la app sigue el idioma del teléfono.
     */
    val idioma: String = "sistema",

    /**
     * Comunidad autónoma elegida para el calendario vacunal (nombre del
     * enum Comunidad, p. ej. "MADRID"). Igual que el tema, es una
     * preferencia de este móvil: no hay columna para esto en Supabase.
     */
    val comunidad: String = "COMUN",

    /** Última vez que se tocó algo en este móvil. */
    val modificado: Fecha? = null,

    /**
     * Filas creadas, cambiadas o borradas aquí que todavía no se han
     * mandado a Supabase. Se guarda en disco a propósito: si el usuario
     * apunta una toma en el parque sin cobertura y cierra la app, al
     * volver a abrirla eso sigue pendiente de subir.
     */
    val pendientes: List<CambioPendiente> = emptyList(),

    /** Momento del último pull con éxito, para pedir solo lo que cambió. */
    val ultimaSincronizacion: Fecha? = null,

    /**
     * Eventos del embudo de conversión (ver EventosFunnel.kt) todavía sin
     * subir a Supabase. Misma idea que "pendientes" pero en su propia
     * lista: son filas de solo inserción, nunca se "sustituyen" como un
     * CambioPendiente de la misma fila.
     */
    val eventosFunnel: List<EventoFunnelPendiente> = emptyList(),

    /**
     * Id anónimo de esta instalación. Sirve para relacionar eventos del
     * funnel aunque todavía no haya sesión iniciada (ver
     * eventos_funnel.dispositivo_id). No es un identificador de usuario:
     * una reinstalación genera uno nuevo.
     *
     * Empieza vacío a propósito. Si el valor por defecto fuese un UUID
     * nuevo, cada vez que se leyera un JSON guardado sin este campo (las
     * instalaciones que ya existían antes de esta versión) se generaría uno
     * distinto, y el id no sería estable hasta el primer guardado. Con la
     * cadena vacía el caso "todavía no tiene id" es explícito y lo resuelve
     * [AlmacenLocal.dispositivoIdEstable], que lo crea y lo persiste una
     * sola vez.
     */
    val dispositivoId: String = ""
)

/**
 * Guarda y lee DatosNubi del almacenamiento del móvil.
 *
 * Funciona SIEMPRE, con o sin conexión: es la fuente de verdad de la app.
 * Supabase es una copia que se pone al día cuando hay red (ver Sincronizador).
 */
class AlmacenLocal(
    private val contexto: Context,
    private val ambito: CoroutineScope
) {
    private val json = Json {
        ignoreUnknownKeys = true   // que una versión vieja no se rompa con campos nuevos
        encodeDefaults = true
        prettyPrint = false
    }

    private val fichero: File
        get() = File(contexto.filesDir, "nubi.json")

    private val candado = Mutex()

    private val _datos = MutableStateFlow(DatosNubi())
    val datos: StateFlow<DatosNubi> = _datos.asStateFlow()

    private val _cargando = MutableStateFlow(true)
    val cargando: StateFlow<Boolean> = _cargando.asStateFlow()

    suspend fun cargar() {
        withContext(Dispatchers.IO) {
            val leido = runCatching {
                if (fichero.exists()) json.decodeFromString<DatosNubi>(fichero.readText())
                else DatosNubi()
            }.getOrElse {
                // Si el fichero está corrupto, no se borra: se renombra, para
                // poder recuperarlo a mano si hiciera falta. Perder los datos
                // de un bebé no es una opción aceptable.
                runCatching {
                    fichero.renameTo(File(contexto.filesDir, "nubi.json.roto"))
                }
                DatosNubi()
            }
            _datos.value = leido
            _cargando.value = false
        }
    }

    /**
     * Cambia los datos y los guarda. El bloque recibe lo que hay ahora y
     * devuelve cómo debe quedar.
     *
     * La escritura es atómica (fichero temporal + renombrado): si el móvil se
     * apaga a media escritura, el nubi.json anterior queda intacto en vez de
     * quedarse a medias.
     */
    suspend fun modificar(bloque: (DatosNubi) -> DatosNubi) {
        candado.withLock {
            val nuevo = bloque(_datos.value).copy(modificado = Instant.now())
            _datos.value = nuevo
            withContext(Dispatchers.IO) {
                runCatching {
                    val temporal = File(contexto.filesDir, "nubi.json.tmp")
                    temporal.writeText(json.encodeToString(nuevo))
                    if (!temporal.renameTo(fichero)) {
                        // renameTo puede fallar si el destino existe en algunos
                        // sistemas de ficheros; se reintenta borrando antes.
                        fichero.delete()
                        temporal.renameTo(fichero)
                    }
                }
            }
        }
    }

    /** Versión que no suspende, para llamar desde la interfaz. */
    fun modificarEnSegundoPlano(bloque: (DatosNubi) -> DatosNubi) {
        ambito.launch { modificar(bloque) }
    }

    /**
     * Marca una fila como pendiente de subir. Si ya había un pendiente para
     * esa misma fila se sustituye, para no acumular trabajo repetido: lo
     * que cuenta es el estado final, no cuántas veces se tocó.
     */
    suspend fun marcarPendiente(tabla: String, id: String, borrado: Boolean = false) {
        modificar { d ->
            d.copy(
                pendientes = d.pendientes.filterNot { it.tabla == tabla && it.id == id } +
                    CambioPendiente(tabla, id, borrado)
            )
        }
    }

    /**
     * Encola un evento del embudo de conversión (ver EventosFunnel.kt).
     *
     * La cola tiene tope. Sin él, si las subidas fallan mucho tiempo (sin
     * red, servidor caído) la lista crece sin límite, y como DatosNubi se
     * reescribe entero en cada `modificar`, cada guardado del resto de la
     * app se iría volviendo más lento. Al llegar al tope se tiran los más
     * viejos: para medir un embudo, perder eventos antiguos de un móvil que
     * lleva semanas sin conexión es mucho menos grave que degradar la app.
     */
    suspend fun encolarEventoFunnel(evento: String, metadata: Map<String, String>) {
        modificar { d ->
            val cola = d.eventosFunnel + EventoFunnelPendiente(evento = evento, metadata = metadata)
            d.copy(eventosFunnel = cola.takeLast(MAX_EVENTOS_FUNNEL))
        }
    }

    /**
     * Devuelve el id de dispositivo, creándolo y guardándolo la primera vez.
     * Así es estable de verdad, también en instalaciones que ya existían
     * antes de que este campo apareciera (ver [DatosNubi.dispositivoId]).
     */
    suspend fun dispositivoIdEstable(): String {
        datos.value.dispositivoId.takeIf { it.isNotEmpty() }?.let { return it }
        val nuevo = UUID.randomUUID().toString()
        modificar { d ->
            // Se vuelve a mirar dentro del candado: otra corrutina puede
            // haberlo creado mientras tanto, y dos ids distintos para la
            // misma instalación falsearían el recuento de dispositivos.
            if (d.dispositivoId.isEmpty()) d.copy(dispositivoId = nuevo) else d
        }
        return datos.value.dispositivoId
    }

    private companion object {
        /** Tope de eventos del funnel guardados a la espera de subir. */
        const val MAX_EVENTOS_FUNNEL = 200
    }
}
