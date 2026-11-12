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

    /**
     * ¿El DUEÑO de "bebe" tiene premium activo? (ver Compartir.kt y
     * Sincronizador.bajarCambios()). Es el equivalente Android de
     * Sincronizador.esParticipante en iOS, pero basado en pago real en vez
     * de en el simple hecho de ser participante: si eres el dueño, da lo
     * mismo que perfil.tieneAccesoPagado(); si eres invitado, refleja el
     * premium de quien te invitó. Premium.activo() lo usa junto al perfil
     * propio — nunca a solas.
     */
    val premiumCompartido: Boolean = false,

    /** Hora del despertar marcado a mano (iOS: despertarManual). */
    val despertarManual: Fecha? = null,

    /**
     * Foto de perfil del bebé, en base64 (JPEG redimensionado a 400px,
     * igual que iOS — ver NubiApp.resized en el repo de iOS). Va aparte
     * de "bebe" a propósito: Bebe se sube a Supabase con un upsert()
     * genérico tal cual (ver Sincronizador.subir), y la tabla "bebes" no
     * tiene esta columna — si viviera dentro de Bebe, ese upsert fallaría.
     * Se sincroniza por su cuenta contra "fotos_bebe" (backend/015_foto_bebe.sql,
     * la misma tabla que ya usa iOS) usando el mecanismo normal de
     * pendientes con Tablas.FOTOS_BEBE (ver Sincronizador).
     */
    val fotoPerfilBase64: String? = null,

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

    /**
     * Aviso de "toca comer" (ver NotificadorComida.swift en iOS): si está
     * activo, y cada cuántos minutos desde la última toma (biberón o
     * pecho) se quiere el recordatorio. Apagado por defecto, igual que
     * iOS; es una preferencia de este móvil (como tema/idioma), no se
     * sincroniza.
     */
    val avisoComidaActivo: Boolean = false,
    val avisoComidaIntervaloMin: Int = 180,
    /**
     * Epoch millis de la "última toma" (Registro.inicio) para la que ya se
     * lanzó el aviso de "casi al momento" cuando ya tocaba comer y no se
     * había anotado la toma (ver avisarDeComida en Repositorio.kt). Evita
     * que el aviso se repita en cada recálculo mientras no se anota una
     * toma nueva — preferencia de este móvil, no se sincroniza.
     */
    val avisoComidaUltimaTomaAvisadaMs: Long? = null,

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
    val dispositivoId: String = "",

    /**
     * Si ya se le ha dicho al servidor que esta cuenta usa Android
     * (ver Sincronizador.registrarPlataformaSiHaceFalta). Solo evita
     * repetir la llamada; si se perdiera, se volvería a mandar y el
     * servidor lo trata igual.
     */
    val plataformaRegistrada: Boolean = false,

    /**
     * Último actualizado_en de Supabase ya aplicado a este móvil, por
     * tabla y por clave (ver Sincronizador.mezclarPorFecha). Con esto se
     * puede detectar un borrado remoto real: una clave que antes estaba
     * aquí y ya no aparece en la descarga — a diferencia de fundir() (ver
     * su comentario), que nunca borra nada local por no venir del
     * servidor. Sin esto, un borrado hecho por el dueño del bebé (o por
     * cualquier otro participante) nunca desaparecía en este móvil, se
     * quedaba viéndose para siempre aunque ya no existiera en Supabase.
     */
    val sellosRemotos: Map<String, Map<String, Fecha>> = emptyMap(),

    /**
     * Si ya se ha hecho al menos una mezcla con sellosRemotos desde que
     * existe este mecanismo. La primera vez que corre en una instalación
     * que ya tenía datos de antes (sellosRemotos vacío, campo nuevo) no
     * hay ningún sello guardado todavía, así que no se puede distinguir
     * "esto cambió de verdad en remoto" de "este móvil tiene un cambio
     * propio sin subir todavía". Esa primera vez, igual que fundir(),
     * solo se añade lo que falte (nunca se sustituye ni se borra nada
     * existente) y se usa para fijar la base de sellos; a partir de la
     * segunda ya se mezcla y se borra de verdad.
     */
    val sellosRemotosInicializados: Boolean = false
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
