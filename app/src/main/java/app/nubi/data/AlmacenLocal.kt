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

    /** Hora del despertar marcado a mano (iOS: despertarManual). */
    val despertarManual: Fecha? = null,

    /** Última vez que se tocó algo en este móvil. */
    val modificado: Fecha? = null,

    /**
     * Ids de filas creadas o cambiadas aquí que todavía no se han subido a
     * Supabase. Mientras haya algo en esta lista, el sincronizador tiene
     * trabajo pendiente. Se guarda en disco a propósito: si el usuario
     * apunta una toma en el parque sin cobertura y cierra la app, al
     * volver a abrirla eso sigue pendiente de subir.
     */
    val pendientesDeSubir: Set<String> = emptySet(),

    /** Momento del último pull con éxito, para pedir solo lo que cambió. */
    val ultimaSincronizacion: Fecha? = null
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

    /** Marca filas como pendientes de subir a Supabase. */
    suspend fun marcarPendientes(vararg ids: String) {
        modificar { it.copy(pendientesDeSubir = it.pendientesDeSubir + ids) }
    }
}
