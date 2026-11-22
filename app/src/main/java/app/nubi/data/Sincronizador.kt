package app.nubi.data

import app.nubi.BuildConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Nombres de las tablas en Supabase (ver backend/002_datos_bebe.sql).
 */
object Tablas {
    const val BEBES = "bebes"
    const val REGISTROS = "registros"
    const val MEDICINAS = "medicinas"
    const val MEDIDAS = "medidas"
    const val CITAS = "citas"
    const val VACUNAS_ESTADO = "vacunas_estado"
    const val VACUNAS_GRUPOS = "vacunas_grupos"
    const val VACUNAS_PROPIAS = "vacunas_propias"
    const val DIARIO = "diario"
    const val EXTRACCIONES = "extracciones_leche"
    const val USOS_LECHE = "usos_leche"
    const val COMIDAS = "comidas_complementarias"
    const val ENFERMEDADES = "enfermedades"
    const val PERFILES = "perfiles"
    const val EVENTOS_FUNNEL = "eventos_funnel"

    /**
     * Foto de perfil del bebé (backend/015_foto_bebe.sql). Tabla aparte de
     * "bebes" a propósito — ver el comentario de fotoPerfilBase64 en
     * AlmacenLocal.kt. Su clave no es "id" sino "bebe_id".
     */
    const val FOTOS_BEBE = "fotos_bebe"
}

/**
 * Fila de "fotos_bebe" (mismas columnas que usa iOS — ver
 * backend/015_foto_bebe.sql).
 */
@Serializable
private data class FilaFotoBebe(
    @SerialName("bebe_id") val bebeId: String,
    @SerialName("foto_base64") val fotoBase64: String? = null
)

@Serializable
private data class AccesoBebe(
    val autenticado: Boolean = false,
    @SerialName("puede_ver") val puedeVer: Boolean = true
)

/**
 * Una fila creada, cambiada o borrada en este móvil que todavía no ha
 * confirmado Supabase. Solo sale de la lista cuando el servidor ha
 * aceptado el cambio (o cuando ya no tiene sentido mandarlo).
 */
@Serializable
data class CambioPendiente(
    val tabla: String,
    val id: String,
    val borrado: Boolean = false,
    /**
     * Alta de la primera mezcla con el motor nuevo: se sube solo si la fila
     * no existe ya en el servidor (ON CONFLICT DO NOTHING). Nunca pisa nada.
     */
    val soloSiNoExiste: Boolean = false,
    val intentos: Int = 0,
    val ultimoError: String? = null
)

/**
 * Estado del motor de sincronización, guardado dentro de DatosNubi.
 */
@Serializable
data class EstadoMotor(
    /** Cuenta para la que vale este estado. Si cambia, se empieza de cero. */
    val usuarioId: String? = null,
    /** Bebé al que se refieren los cursores y baseServidor. */
    val bebeId: String? = null,
    /**
     * Bebé al que hay que pasar en la próxima descarga (al aceptar una
     * invitación). Hasta que el servidor no lo entrega, se sigue viendo lo
     * que había: nunca se vacía la pantalla a ciegas.
     */
    val bebeObjetivo: String? = null,
    /** Ya se hizo la primera mezcla (descarga completa) para este bebé. */
    val migrado: Boolean = false,
    /** actualizado_en más nuevo ya aplicado (formato ISO, en UTC). */
    val cursorDatos: String? = null,
    /** "en" del último borrado de historial_cambios ya aplicado. */
    val cursorBorrados: String? = null,
    /**
     * Cambios que el servidor ha rechazado varias veces (o que eran de otra
     * cuenta). No se reintentan, pero no se tiran: quedan aquí para
     * poder diagnosticar.
     */
    val fallidas: List<CambioPendiente> = emptyList()
)

/**
 * Sincronización con Supabase. Mismas reglas que MotorSync.swift en iOS:
 *
 * 1. SUPABASE MANDA. El móvil es una caché de lectura más una cola de
 *    cambios propios pendientes (DatosNubi.pendientes) que se guarda en
 *    disco y solo se vacía cuando el servidor confirma cada cambio. Sin
 *    conexión se puede seguir apuntando todo: se sube al volver la red.
 * 2. SE SUBEN SOLO LAS COLUMNAS TOCADAS (UPDATE de lo que difiere de
 *    baseServidor), nunca la fila entera: si dos personas cambian a la vez
 *    campos distintos de la misma fila, no se pisan. Si coinciden en el
 *    mismo campo, gana el último que llega a Supabase.
 * 3. Un UPDATE que no encuentra la fila (0 filas) quiere decir que la fila
 *    ya no existe: no se vuelve a crear.
 * 4. NADA SE BORRA EN EL MÓVIL POR NO VENIR EN UNA DESCARGA. Un borrado de
 *    otro dispositivo solo se aplica si historial_cambios lo confirma
 *    (backend/021_historial_cambios.sql).
 * 5. Se descarga solo lo de ESTE bebé y solo lo que ha cambiado desde la
 *    última vez (actualizado_en, con 2 minutos de solape), en orden estable
 *    y paginando por clave: una fila que cambie mientras se pagina nunca
 *    hace que otra se salte.
 * 6. Lo que tiene un cambio propio pendiente no se sustituye con lo que
 *    baje, y el cursor no avanza más allá de esa fila: en cuanto se suba,
 *    se vuelve a traer la versión buena.
 * 7. Que el bebé no llegue NO es motivo para vaciar el móvil (puede ser un
 *    problema pasajero de sesión). Solo si el servidor confirma, con
 *    estado_acceso_bebe (backend/022), que esta cuenta ya no puede verlo,
 *    quien estaba invitado deja de verlo aquí — y antes se guarda una copia.
 * 8. Primera vez con este motor (o con este bebé): descarga completa, el
 *    servidor gana, y lo que solo existe en este móvil se sube "solo si no
 *    existe". Nunca se crea un segundo bebé para la misma cuenta.
 */
class Sincronizador(private val local: AlmacenLocal) {

    enum class Estado { EN_REPOSO, SINCRONIZANDO, SIN_CONEXION, ERROR }

    private val _estado = MutableStateFlow(Estado.EN_REPOSO)
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    private val _ultimoError = MutableStateFlow<String?>(null)
    val ultimoError: StateFlow<String?> = _ultimoError.asStateFlow()

    // Para que la UI pueda esperar a que termine (con éxito o sin él) el
    // primer intento de sincronizar antes de decidir que no hay bebé — ver
    // MainActivity.ContenidoApp(): sin esto, en un móvil nuevo con una
    // cuenta YA existente se ofrecería crear un bebé duplicado mientras el
    // real todavía está bajando.
    private val _primeraSincronizacionHecha = MutableStateFlow(false)
    val primeraSincronizacionHecha: StateFlow<Boolean> = _primeraSincronizacionHecha.asStateFlow()

    /** Progreso de la bajada en curso (0..1), o null si no se baja nada. */
    private val _progresoBajada = MutableStateFlow<Float?>(null)
    val progresoBajada: StateFlow<Float?> = _progresoBajada.asStateFlow()

    // Las peticiones esperan su turno y se agrupan: si mientras una espera
    // empieza otra sincronización completa, esa ya la cubre.
    private val candado = Mutex()
    private val peticiones = AtomicLong(0)
    @Volatile private var peticionesCubiertas = 0L
    @Volatile private var ultimoResultado = false

    /**
     * Convierte filas de Supabase en modelos y al revés. coerceInputValues:
     * un null del servidor en un campo con valor por defecto no tira la
     * fila entera.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    /**
     * Sube lo pendiente y baja lo que haya cambiado. Es seguro llamarla
     * tantas veces como haga falta: si no hay sesión o no hay red, no hace
     * nada y lo pendiente sigue pendiente. Devuelve true si la bajada se
     * completó sin errores.
     */
    suspend fun sincronizar(): Boolean {
        if (Supabase.client.auth.currentSessionOrNull() == null) return false
        val miPeticion = peticiones.incrementAndGet()

        return candado.withLock {
            if (peticionesCubiertas >= miPeticion) return@withLock ultimoResultado
            val cubre = peticiones.get()
            val ok = sincronizarUnaVez()
            peticionesCubiertas = cubre
            ultimoResultado = ok
            ok
        }
    }

    private suspend fun sincronizarUnaVez(): Boolean {
        _estado.value = Estado.SINCRONIZANDO
        try {
            val ok = withContext(Dispatchers.IO) {
                val miId = Supabase.client.auth.currentUserOrNull()?.id?.lowercase()
                    ?: return@withContext false
                prepararMotor(miId)
                recogerCambiosSinMarcar(miId)
                subirPendientes(miId)
                registrarPlataformaSiHaceFalta()
                subirEventosFunnelPendientes()
                val bajadaOk = bajarCambios(miId)
                // La primera mezcla puede haber dejado altas en la cola.
                if (local.datos.value.pendientes.isNotEmpty()) subirPendientes(miId)
                bajadaOk
            }
            if (ok) {
                _estado.value = Estado.EN_REPOSO
                _ultimoError.value = null
                local.modificar { it.copy(ultimaSincronizacion = Instant.now()) }
            } else {
                _estado.value = Estado.SIN_CONEXION
            }
            return ok
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sin conexión no es un fallo: es el estado normal de un parque.
            // Lo pendiente se queda pendiente y se reintenta más tarde.
            _estado.value = Estado.SIN_CONEXION
            _ultimoError.value = e.message
            return false
        } finally {
            _progresoBajada.value = null
            _primeraSincronizacionHecha.value = true
        }
    }

    // -----------------------------------------------------------------
    // Tablas: cómo leer/escribir cada una en DatosNubi y cómo es su clave
    // -----------------------------------------------------------------

    private class Def<T>(
        val tabla: String,
        /** Orden de subida: lo referenciado antes que lo que lo referencia. */
        val prioridad: Int,
        val serializer: KSerializer<T>,
        val clave: (T) -> String,
        /** Bebé al que pertenece la fila (para no mezclar bebés). */
        val bebeDe: (T, DatosNubi) -> String?,
        val leer: (DatosNubi) -> List<T>,
        val poner: (DatosNubi, List<T>) -> DatosNubi,
        val columnasClave: List<String> = listOf("id"),
        /** Columna de desempate al paginar (única dentro del bebé). */
        val desempate: String = "id",
        /** Se sube siempre la fila entera (upsert), no solo lo tocado. */
        val siempreCompleta: Boolean = false,
        /** Solo el dueño del bebé puede cambiarla. */
        val soloDuenio: Boolean = false,
        /** Columnas que son de este móvil y nunca se suben ni se comparan. */
        val soloLocales: Set<String> = emptySet(),
        /** Columnas que nunca se mandan en un UPDATE. */
        val nuncaEnCambios: Set<String> = emptySet(),
        /** Para descargar: qué pedir y por qué columna filtrar el bebé. */
        val seleccion: String = "*",
        val columnaBebe: String = "bebe_id",
        /** Al aplicar lo del servidor, qué se conserva de la fila local. */
        val conservar: ((local: T?, servidor: T) -> T)? = null
    )

    private fun uuid(s: String) = s.lowercase()

    private val defs: List<Def<*>> = listOf(
        Def(
            Tablas.BEBES, 0, Bebe.serializer(), { uuid(it.id) }, { b, _ -> uuid(b.id) },
            { listOfNotNull(it.bebe) },
            { d, l -> d.copy(bebe = l.firstOrNull() ?: d.bebe) },
            soloDuenio = true,
            nuncaEnCambios = setOf("usuario_id", "creado_en"),
            columnaBebe = "id"
        ),
        Def(
            Tablas.REGISTROS, 1, Registro.serializer(), { uuid(it.id) }, { r, _ -> uuid(r.bebeId) },
            { it.registros }, { d, l -> d.copy(registros = l) }
        ),
        Def(
            Tablas.MEDICINAS, 1, Medicina.serializer(), { uuid(it.id) }, { r, _ -> uuid(r.bebeId) },
            { it.medicinas }, { d, l -> d.copy(medicinas = l) }
        ),
        Def(
            Tablas.MEDIDAS, 1, Medida.serializer(), { uuid(it.id) }, { r, _ -> uuid(r.bebeId) },
            { it.medidas }, { d, l -> d.copy(medidas = l) }
        ),
        Def(
            Tablas.CITAS, 1, Cita.serializer(), { uuid(it.id) }, { r, _ -> uuid(r.bebeId) },
            { it.citas }, { d, l -> d.copy(citas = l) }
        ),
        Def(
            Tablas.VACUNAS_ESTADO, 1, EstadoVacuna.serializer(),
            { "${uuid(it.bebeId)}|${it.clave}" }, { r, _ -> uuid(r.bebeId) },
            { it.vacunas }, { d, l -> d.copy(vacunas = l) },
            columnasClave = listOf("bebe_id", "clave"),
            desempate = "clave",
            siempreCompleta = true
        ),
        Def(
            Tablas.VACUNAS_GRUPOS, 1, GrupoVacunas.serializer(), { uuid(it.id) }, { r, _ -> uuid(r.bebeId) },
            { it.gruposVacunas }, { d, l -> d.copy(gruposVacunas = l) }
        ),
        Def(
            Tablas.VACUNAS_PROPIAS, 2, VacunaPropia.serializer(), { uuid(it.id) },
            { r, d -> d.gruposVacunas.firstOrNull { uuid(it.id) == uuid(r.grupoId) }?.bebeId?.let(::uuid) },
            { it.vacunasPropias }, { d, l -> d.copy(vacunasPropias = l) },
            seleccion = "*,vacunas_grupos!inner(bebe_id)",
            columnaBebe = "vacunas_grupos.bebe_id"
        ),
        Def(
            Tablas.DIARIO, 1, EntradaDiario.serializer(), { uuid(it.id) }, { r, _ -> uuid(r.bebeId) },
            { it.diario }, { d, l -> d.copy(diario = l) }
        ),
        Def(
            Tablas.EXTRACCIONES, 1, ExtraccionLeche.serializer(), { uuid(it.id) }, { r, _ -> uuid(r.bebeId) },
            { it.extracciones }, { d, l -> d.copy(extracciones = l) }
        ),
        Def(
            Tablas.USOS_LECHE, 1, UsoLeche.serializer(), { uuid(it.id) }, { r, _ -> uuid(r.bebeId) },
            { it.usosLeche }, { d, l -> d.copy(usosLeche = l) }
        ),
        Def(
            Tablas.COMIDAS, 1, ComidaComplementaria.serializer(), { uuid(it.id) }, { r, _ -> uuid(r.bebeId) },
            { it.comidas }, { d, l -> d.copy(comidas = l) },
            // Las fotos de las comidas son ficheros de ESTE móvil: no se
            // suben (otro móvil no podría abrirlas) y se conservan al bajar.
            soloLocales = setOf("fotos"),
            conservar = { localFila, servidor ->
                servidor.copy(fotos = localFila?.fotos ?: servidor.fotos.filter { File(it).exists() })
            }
        ),
        Def(
            Tablas.ENFERMEDADES, 1, Enfermedad.serializer(), { uuid(it.id) }, { r, _ -> uuid(r.bebeId) },
            { it.enfermedades }, { d, l -> d.copy(enfermedades = l) }
        )
    )

    private val defPorTabla: Map<String, Def<*>> = defs.associateBy { it.tabla }

    private fun prioridad(tabla: String) = defPorTabla[tabla]?.prioridad ?: 0

    /** La fila como la entiende Supabase, sin actualizado_en ni lo local. */
    private fun <T> fila(def: Def<T>, valor: T): JsonObject {
        val o = json.encodeToJsonElement(def.serializer, valor).jsonObject
        return JsonObject(o.filterKeys { k ->
            k != "actualizado_en" && k !in def.soloLocales &&
                // Bebé de antes de usuario_id: no se compara ni se manda vacío.
                !(k == "usuario_id" && (o[k] as? JsonPrimitive)?.contentOrNull.isNullOrBlank())
        })
    }

    private fun <T> decodificar(def: Def<T>, o: JsonObject): T? =
        try {
            json.decodeFromJsonElement(def.serializer, o)
        } catch (e: Exception) {
            null
        }

    private fun clavePendiente(tabla: String, id: String) = "$tabla|$id"

    private fun esDuenio(bebe: Bebe?, miId: String) =
        bebe == null || bebe.usuarioId.isBlank() || uuid(bebe.usuarioId) == miId

    // -----------------------------------------------------------------
    // Preparación
    // -----------------------------------------------------------------

    /**
     * Ajusta el estado del motor a la cuenta y al bebé de ahora. No toca
     * ningún dato del bebé: como mucho reinicia cursores y la base.
     */
    private suspend fun prepararMotor(miId: String) {
        local.modificar { d ->
            var m = d.motor
            var pendientes = d.pendientes
            var base = d.baseServidor
            if (m.usuarioId != null && m.usuarioId != miId) {
                // Otra cuenta en este móvil: lo pendiente era de la anterior.
                m = EstadoMotor(usuarioId = miId, fallidas = m.fallidas + pendientes)
                pendientes = emptyList()
                base = emptyMap()
            } else if (m.usuarioId == null) {
                // Primera vez con este motor: lo pendiente de antes es del
                // usuario y se conserva.
                m = m.copy(usuarioId = miId)
            }
            val bebeLocal = d.bebe?.id?.let(::uuid)
            val objetivo = m.bebeObjetivo ?: bebeLocal ?: m.bebeId
            if (objetivo != m.bebeId) {
                m = m.copy(bebeId = objetivo, migrado = false, cursorDatos = null, cursorBorrados = null)
                base = emptyMap()
            }
            // Claves siempre en minúsculas (los uuid de Postgres lo están).
            pendientes = pendientes.map { it.copy(id = normalizarClave(it.tabla, it.id)) }
                .distinctBy { clavePendiente(it.tabla, it.id) + "|" + it.borrado }
            d.copy(motor = m, pendientes = pendientes, baseServidor = base)
        }
    }

    private fun normalizarClave(tabla: String, id: String): String =
        if (tabla == Tablas.VACUNAS_ESTADO) id.substringBefore("|").lowercase() + "|" + id.substringAfter("|", "")
        else id.lowercase()

    /**
     * Red de seguridad: cualquier fila de este bebé que difiera de lo que
     * se sabe que tiene el servidor, y no esté ya en la cola, se pone en la
     * cola. Así un cambio guardado por un camino que se olvidara de
     * marcarlo como pendiente también llega al servidor, y nunca se pisa
     * con lo que baje.
     */
    private suspend fun recogerCambiosSinMarcar(miId: String) {
        local.modificar { d ->
            val m = d.motor
            val bebeId = m.bebeId ?: return@modificar d
            if (!m.migrado || m.bebeObjetivo != null) return@modificar d
            val invitado = !esDuenio(d.bebe, miId)
            val enCola = d.pendientes.map { clavePendiente(it.tabla, it.id) }.toSet()
            val nuevos = mutableListOf<CambioPendiente>()
            for (def in defs) {
                if (invitado && def.soloDuenio) continue
                recoger(def, d, bebeId, enCola, nuevos)
            }
            if (nuevos.isEmpty()) d else d.copy(pendientes = d.pendientes + nuevos)
        }
    }

    private fun <T> recoger(
        def: Def<T>, d: DatosNubi, bebeId: String, enCola: Set<String>, nuevos: MutableList<CambioPendiente>
    ) {
        val base = d.baseServidor[def.tabla] ?: emptyMap()
        for (f in def.leer(d)) {
            if (def.bebeDe(f, d) != bebeId) continue
            val k = def.clave(f)
            if (clavePendiente(def.tabla, k) in enCola) continue
            val b = base[k]
            if (b == null || fila(def, f) != b) nuevos += CambioPendiente(def.tabla, k)
        }
    }

    // -----------------------------------------------------------------
    // Subida
    // -----------------------------------------------------------------

    private sealed class Resultado {
        /** Aceptado. enviado = la fila local tal como se mandó. */
        class Hecho(val enviado: JsonObject?, val nuevaBase: JsonObject?, val filaServidor: JsonObject? = null) : Resultado()
        /** La fila ya no existe en el servidor: no se resucita. */
        object NoExiste : Resultado()
        /** Ya no hay nada que mandar (la fila local ya no está, o no es nuestra). */
        object Descartar : Resultado()
        /** No se debe mandar nunca (p. ej. un segundo bebé para la cuenta). */
        class Bloqueada(val motivo: String) : Resultado()
        /** El servidor no la acepta (datos o permisos). */
        class Rechazada(val motivo: String) : Resultado()
        /** Red o sesión: se reintenta más tarde, sin contar intento. */
        class ReintentarLuego(val motivo: String) : Resultado()
    }

    private suspend fun subirPendientes(miId: String) {
        val ops = local.datos.value.pendientes
            .withIndex()
            .sortedWith(compareBy({ prioridad(it.value.tabla) }, { it.index }))
            .map { it.value }

        for (op in ops) {
            val d = local.datos.value
            val actual = d.pendientes.firstOrNull { it.tabla == op.tabla && it.id == op.id } ?: continue
            val resultado = try {
                enviar(actual, d, miId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                clasificar(e)
            }
            if (resultado is Resultado.ReintentarLuego) {
                // Sin red o sin sesión: el resto también fallaría. Se para
                // aquí y se respeta el orden en el siguiente intento.
                _ultimoError.value = resultado.motivo
                return
            }
            aplicarResultado(actual, resultado)
        }
    }

    private suspend fun aplicarResultado(op: CambioPendiente, r: Resultado) {
        local.modificar { d ->
            val enCola = d.pendientes.firstOrNull { it.tabla == op.tabla && it.id == op.id }
            fun sinOp() = d.pendientes.filterNot { it.tabla == op.tabla && it.id == op.id }
            when (r) {
                is Resultado.Hecho -> {
                    val baseTabla = (d.baseServidor[op.tabla] ?: emptyMap()).toMutableMap()
                    if (op.borrado) baseTabla.remove(op.id) else r.nuevaBase?.let { baseTabla[op.id] = it }
                    var nd = d.copy(baseServidor = d.baseServidor + (op.tabla to baseTabla))
                    // ¿Ha cambiado la fila en local mientras se subía? Si es
                    // así, se queda en la cola para mandar lo nuevo.
                    val sigueIgual = when {
                        enCola == null -> true
                        op.borrado -> enCola.borrado
                        else -> !enCola.borrado && filaLocal(d, op.tabla, op.id) == r.enviado
                    }
                    // Era un alta "solo si no existe" y ya existía: gana el
                    // servidor, y se pone en el móvil su versión.
                    if (r.filaServidor != null && sigueIgual) {
                        nd = ponerFilaServidor(nd, op.tabla, op.id, r.filaServidor)
                    }
                    if (sigueIgual) {
                        nd.copy(pendientes = sinOp())
                    } else {
                        nd.copy(pendientes = nd.pendientes.map {
                            if (it.tabla == op.tabla && it.id == op.id) it.copy(soloSiNoExiste = false) else it
                        })
                    }
                }
                // La fila ya no existe en el servidor: no se resucita, y se
                // apunta como "sabida" para no volver a mandarla en cada
                // pasada. No se borra del móvil por inferencia: si el
                // historial confirma el borrado, desaparecerá entonces.
                Resultado.NoExiste -> {
                    val actualLocal = filaLocal(d, op.tabla, op.id)
                    val baseTabla = (d.baseServidor[op.tabla] ?: emptyMap()).let { b ->
                        if (actualLocal != null && op.tabla != Tablas.FOTOS_BEBE) b + (op.id to actualLocal) else b
                    }
                    d.copy(pendientes = sinOp(), baseServidor = d.baseServidor + (op.tabla to baseTabla))
                }
                Resultado.Descartar -> d.copy(pendientes = sinOp())
                is Resultado.Bloqueada -> d.copy(
                    pendientes = sinOp(),
                    motor = d.motor.copy(fallidas = (d.motor.fallidas + op.copy(ultimoError = r.motivo)).takeLast(200))
                )
                is Resultado.Rechazada -> {
                    val intentos = (enCola?.intentos ?: op.intentos) + 1
                    if (intentos >= MAX_INTENTOS_CAMBIO) {
                        d.copy(
                            pendientes = sinOp(),
                            motor = d.motor.copy(
                                fallidas = (d.motor.fallidas + op.copy(intentos = intentos, ultimoError = r.motivo)).takeLast(200)
                            )
                        )
                    } else {
                        d.copy(pendientes = d.pendientes.map {
                            if (it.tabla == op.tabla && it.id == op.id) it.copy(intentos = intentos, ultimoError = r.motivo) else it
                        })
                    }
                }
                is Resultado.ReintentarLuego -> d
            }
        }
    }

    private fun filaFotoLocal(bebeId: String, foto: String?) = buildJsonObject {
        put("bebe_id", bebeId)
        put("foto_base64", foto)
    }

    private fun filaLocal(d: DatosNubi, tabla: String, id: String): JsonObject? {
        if (tabla == Tablas.FOTOS_BEBE) return filaFotoLocal(id, d.fotoPerfilBase64)
        val def = defPorTabla[tabla] ?: return null
        return filaLocalDe(def, d, id)
    }

    private fun <T> filaLocalDe(def: Def<T>, d: DatosNubi, id: String): JsonObject? =
        def.leer(d).firstOrNull { def.clave(it) == id }?.let { fila(def, it) }

    private fun ponerFilaServidor(d: DatosNubi, tabla: String, id: String, o: JsonObject): DatosNubi {
        val def = defPorTabla[tabla] ?: return d
        return ponerFilaServidorDe(def, d, id, o)
    }

    private fun <T> ponerFilaServidorDe(def: Def<T>, d: DatosNubi, id: String, o: JsonObject): DatosNubi {
        val modelo = decodificar(def, o) ?: return d
        val lista = def.leer(d)
        val actual = lista.firstOrNull { def.clave(it) == id }
        val nuevo = def.conservar?.invoke(actual, modelo) ?: modelo
        val nuevaLista = if (actual == null) lista + nuevo else lista.map { if (def.clave(it) == id) nuevo else it }
        val baseTabla = (d.baseServidor[def.tabla] ?: emptyMap()) + (id to fila(def, modelo))
        return def.poner(d, nuevaLista).copy(baseServidor = d.baseServidor + (def.tabla to baseTabla))
    }

    private suspend fun enviar(op: CambioPendiente, d: DatosNubi, miId: String): Resultado {
        val invitado = !esDuenio(d.bebe, miId)

        if (op.tabla == Tablas.FOTOS_BEBE) {
            if (invitado) return Resultado.Descartar
            if (op.borrado) {
                Supabase.client.from(Tablas.FOTOS_BEBE).delete { filter { eq("bebe_id", op.id) } }
                return Resultado.Hecho(null, null)
            }
            val foto = d.fotoPerfilBase64 ?: return Resultado.Descartar
            val filaFoto = filaFotoLocal(op.id, foto)
            Supabase.client.from(Tablas.FOTOS_BEBE).upsert(JsonArray(listOf(filaFoto))) {
                onConflict = "bebe_id"
            }
            return Resultado.Hecho(filaFoto, null)
        }

        val def = defPorTabla[op.tabla] ?: return Resultado.Descartar
        if (invitado && def.soloDuenio) return Resultado.Descartar
        return enviarFila(def, op, d, miId)
    }

    private suspend fun <T> enviarFila(def: Def<T>, op: CambioPendiente, d: DatosNubi, miId: String): Resultado {
        val tabla = Supabase.client.from(def.tabla)

        if (op.borrado) {
            tabla.delete { filter { filtrarClave(def, op.id) } }
            return Resultado.Hecho(null, null)
        }

        val modelo = def.leer(d).firstOrNull { def.clave(it) == op.id } ?: return Resultado.Descartar
        val enviado = fila(def, modelo)
        val base = d.baseServidor[def.tabla]?.get(op.id)

        // Sin base conocida y sin ser alta de la primera mezcla: puede ser una
        // fila nueva o un cambio a una que ya existe (cola del motor viejo,
        // cambio de bebé...). Primero se intenta como cambio; solo si no
        // existe, y el historial no dice que se borró, se crea.
        if (base == null && !def.siempreCompleta && !op.soloSiNoExiste && def.tabla != Tablas.BEBES) {
            val cuerpo = JsonObject(enviado.filterKeys { it !in def.columnasClave && it !in def.nuncaEnCambios })
            val cambiadas = tabla.update(cuerpo) {
                select(Columns.list(def.columnasClave))
                filter { filtrarClave(def, op.id) }
            }.decodeList<JsonObject>()
            if (cambiadas.isNotEmpty()) return Resultado.Hecho(enviado, enviado)
            if (estaBorradaEnHistorial(def.tabla, op.id)) return Resultado.NoExiste
            tabla.upsert(JsonArray(listOf(enviado))) {
                onConflict = def.columnasClave.joinToString(",")
                ignoreDuplicates = true
            }
            return Resultado.Hecho(enviado, enviado)
        }

        if (base == null || def.siempreCompleta) {
            var filaEnvio = enviado
            if (def.tabla == Tablas.BEBES) {
                filaEnvio = JsonObject(enviado + ("usuario_id" to JsonPrimitive(miId)))
                if (base == null) {
                    // Nunca un segundo bebé para la misma cuenta.
                    val otros = Supabase.client.from(Tablas.BEBES).select(Columns.list("id")) {
                        filter {
                            eq("usuario_id", miId)
                            neq("id", op.id)
                        }
                        limit(1)
                    }.decodeList<JsonObject>()
                    if (otros.isNotEmpty()) return Resultado.Bloqueada("la cuenta ya tiene otro bebé")
                }
            }
            val respuesta = tabla.upsert(JsonArray(listOf(filaEnvio))) {
                onConflict = def.columnasClave.joinToString(",")
                if (op.soloSiNoExiste) {
                    ignoreDuplicates = true
                    select(Columns.list(def.columnasClave))
                }
            }
            if (op.soloSiNoExiste && respuesta.decodeList<JsonObject>().isEmpty()) {
                // Ya existía en el servidor: no se ha tocado. Se trae su versión.
                val servidor = tabla.select { filter { filtrarClave(def, op.id) } }
                    .decodeList<JsonObject>().firstOrNull()
                return Resultado.Hecho(enviado, servidor?.let { o -> decodificar(def, o)?.let { fila(def, it) } } ?: enviado, servidor)
            }
            return Resultado.Hecho(enviado, enviado)
        }

        val cambios = JsonObject(enviado.filter { (k, v) ->
            k !in def.columnasClave && k !in def.nuncaEnCambios && base[k] != v
        })
        if (cambios.isEmpty()) return Resultado.Hecho(enviado, base)

        val filas = tabla.update(cambios) {
            select(Columns.list(def.columnasClave))
            filter { filtrarClave(def, op.id) }
        }.decodeList<JsonObject>()
        if (filas.isEmpty()) return Resultado.NoExiste
        return Resultado.Hecho(enviado, JsonObject(base + cambios))
    }

    private suspend fun estaBorradaEnHistorial(tabla: String, id: String): Boolean = try {
        Supabase.client.from("historial_cambios").select(Columns.list("id")) {
            filter {
                eq("tabla", tabla)
                eq("fila_id", id)
                eq("operacion", "DELETE")
            }
            limit(1)
        }.decodeList<JsonObject>().isNotEmpty()
    } catch (e: PostgrestRestException) {
        if (e.code in setOf("42P01", "PGRST205", "PGRST200")) false else throw e
    }

    /**
     * De las filas que el historial da por borradas, las que SIGUEN
     * existiendo en el servidor (alguien las volvió a subir después).
     * Esas no se borran del móvil.
     */
    private suspend fun siguenExistiendo(candidatos: List<Borrado>): Set<String> {
        val vivos = mutableSetOf<String>()
        for ((tablaNombre, grupo) in candidatos.groupBy { it.tabla }) {
            val def = defPorTabla[tablaNombre] ?: continue
            if (def.columnasClave.size != 1) continue
            for (trozo in grupo.map { it.filaId }.distinct().chunked(100)) {
                Supabase.client.from(def.tabla).select(Columns.list(def.columnasClave)) {
                    filter { isIn(def.columnasClave[0], trozo) }
                }.decodeList<JsonObject>().forEach { o ->
                    o[def.columnasClave[0]]?.jsonPrimitive?.contentOrNull?.let { vivos += clavePendiente(tablaNombre, uuid(it)) }
                }
            }
        }
        return vivos
    }

    private fun io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder.filtrarClave(
        def: Def<*>, id: String
    ) {
        if (def.columnasClave.size == 2) {
            val partes = id.split("|", limit = 2)
            eq(def.columnasClave[0], partes.getOrElse(0) { "" })
            eq(def.columnasClave[1], partes.getOrElse(1) { "" })
        } else {
            eq(def.columnasClave[0], id)
        }
    }

    private fun clasificar(e: Exception): Resultado = when (e) {
        is PostgrestRestException -> {
            val codigo = e.code.orEmpty()
            // PGRST3xx = token caducado o no válido: es de sesión, no de datos.
            if (codigo.isEmpty() || codigo.startsWith("PGRST3") || e.statusCode >= 500 || e.statusCode == 401) {
                Resultado.ReintentarLuego("$codigo ${e.message}")
            } else {
                Resultado.Rechazada("$codigo ${e.message}")
            }
        }
        is RestException ->
            if (e.statusCode >= 500 || e.statusCode == 401 || e.statusCode == 408 || e.statusCode == 429) {
                Resultado.ReintentarLuego(e.message ?: "")
            } else {
                Resultado.Rechazada("${e.statusCode} ${e.message}")
            }
        is IOException -> Resultado.ReintentarLuego(e.message ?: "sin conexión")
        else -> Resultado.Rechazada(e.message ?: e.javaClass.simpleName)
    }

    // -----------------------------------------------------------------
    // Plataforma y embudo (sin cambios de comportamiento)
    // -----------------------------------------------------------------

    /**
     * Deja constancia, una sola vez, de que esta cuenta usa Android (ver
     * backend/012_plataforma_por_usuario.sql). Si falla, se reintenta en la
     * siguiente sincronización.
     */
    private suspend fun registrarPlataformaSiHaceFalta() {
        if (BuildConfig.DEBUG) return
        if (local.datos.value.plataformaRegistrada) return
        if (Supabase.client.auth.currentUserOrNull() == null) return

        val ok = try {
            Supabase.client.postgrest.rpc(
                "registrar_plataforma",
                buildJsonObject { put("p_plataforma", "android") }
            )
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

        if (ok) local.modificar { it.copy(plataformaRegistrada = true) }
    }

    /**
     * Sube los eventos del embudo que estén pendientes (ver EventosFunnel.kt).
     * Si no entra ninguno se asume fallo general (sin red) y no se toca la
     * cola; si fallan solo algunos, esos suman intento y tras
     * [MAX_INTENTOS_EVENTO] se descartan.
     */
    private suspend fun subirEventosFunnelPendientes() {
        val pendientes = local.datos.value.eventosFunnel
        if (pendientes.isEmpty()) return

        val miId = Supabase.client.auth.currentUserOrNull()?.id
        val dispositivoId = local.dispositivoIdEstable()
        val subidos = mutableSetOf<String>()
        val fallados = mutableSetOf<String>()

        for (evento in pendientes) {
            val ok = try {
                Supabase.client.from(Tablas.EVENTOS_FUNNEL).insert(
                    FilaEventoFunnel(
                        usuarioId = miId,
                        dispositivoId = dispositivoId,
                        evento = evento.evento,
                        metadata = evento.metadata,
                        creadoEn = evento.creadoEn
                    )
                )
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (ok) subidos += evento.idLocal else fallados += evento.idLocal
        }

        if (subidos.isEmpty()) return

        local.modificar { d ->
            d.copy(
                eventosFunnel = d.eventosFunnel.mapNotNull { e ->
                    when (e.idLocal) {
                        in subidos -> null
                        in fallados -> e.copy(intentos = e.intentos + 1)
                            .takeIf { it.intentos < MAX_INTENTOS_EVENTO }
                        else -> e
                    }
                }
            )
        }
    }

    // -----------------------------------------------------------------
    // Bajada
    // -----------------------------------------------------------------

    private class Borrado(val tabla: String, val filaId: String, val en: String)

    /**
     * Devuelve true si se pudo hablar con el servidor (aunque no hubiera
     * nada nuevo). Una excepción de red sale hacia arriba.
     */
    private suspend fun bajarCambios(miId: String): Boolean {
        val totalPasos = defs.size + 3  // tablas + perfil + foto + premium
        val hechos = AtomicInteger(0)
        fun pasoHecho() {
            _progresoBajada.value = hechos.incrementAndGet().toFloat() / totalPasos
        }
        _progresoBajada.value = 0f

        // El perfil (prueba/premium) lo gestiona el servidor en exclusiva.
        val perfil = Supabase.client.from(Tablas.PERFILES).select {
            filter { eq("id", miId) }
        }.decodeList<Perfil>().firstOrNull()
        pasoHecho()
        if (perfil != null) local.modificar { it.copy(perfil = perfil) }

        // ¿Qué bebé? El de este móvil (o al que se va a pasar). Si no hay
        // ninguno, el de la cuenta; si no tiene, uno compartido con ella.
        var m = local.datos.value.motor
        var bebeId = m.bebeId
        if (bebeId == null) {
            bebeId = buscarBebeDeLaCuenta(miId) ?: run {
                _progresoBajada.value = 1f
                return true
            }
            local.modificar { d ->
                d.copy(motor = d.motor.copy(bebeId = bebeId, bebeObjetivo = bebeId, migrado = false,
                    cursorDatos = null, cursorBorrados = null), baseServidor = emptyMap())
            }
            m = local.datos.value.motor
        }

        val cambiaDeBebe = m.bebeObjetivo != null && m.bebeObjetivo != local.datos.value.bebe?.id?.let(::uuid)
        if (cambiaDeBebe && local.datos.value.pendientes.isNotEmpty()) {
            // Antes de pasar a otro bebé, que suba lo pendiente del actual.
            return false
        }

        val completa = !m.migrado || cambiaDeBebe
        val desde = if (completa) null else m.cursorDatos?.let { restarSolape(it) }
        val desdeBorrados = m.cursorBorrados?.let { restarSolape(it) }

        // 1) Borrados confirmados, ANTES que los datos: si una fila aparece
        //    en los datos, existe ahora mismo y gana a su borrado.
        val borrados = traerBorrados(bebeId, desdeBorrados)

        // 2) El bebé. Si no llega, no se aplica NADA de esta pasada.
        val filaBebe = Supabase.client.from(Tablas.BEBES).select {
            filter { eq("id", bebeId) }
        }.decodeList<JsonObject>().firstOrNull()

        if (filaBebe == null) {
            _progresoBajada.value = 1f
            val bebeLocal = local.datos.value.bebe
            val esMio = bebeLocal != null && uuid(bebeLocal.id) == bebeId && esDuenio(bebeLocal, miId)
            when {
                // Bebé al que se iba a pasar y que el servidor no entrega:
                // se sigue viendo lo que había. Solo si el servidor confirma
                // que no hay acceso se abandona el cambio; si no, se
                // reintenta en la próxima sincronización.
                cambiaDeBebe -> {
                    if (accesoPerdido(bebeId)) {
                        local.modificar { d ->
                            d.copy(motor = d.motor.copy(bebeObjetivo = null, bebeId = d.bebe?.id?.let(::uuid),
                                migrado = false, cursorDatos = null, cursorBorrados = null))
                        }
                    }
                    return false
                }
                // Bebé propio que todavía no está en la nube: se sube todo.
                // (Salvo que la cuenta ya tenga OTRO bebé en el servidor: p. ej.
                // un móvil nuevo que abrió sin red y creó uno. Entonces se pasa
                // al de la cuenta; lo de este móvil queda en la copia.)
                esMio && !m.migrado -> {
                    val propio = buscarBebePropio(miId)
                    if (propio != null && propio != bebeId) cambiarABebe(propio) else subirTodoPorPrimeraVez(bebeId)
                }
                // Era invitado (u otra cuenta): solo si el servidor confirma
                // que ya no hay acceso, se deja de ver.
                !esMio -> if (accesoPerdido(bebeId) && local.datos.value.motor.bebeId == bebeId) {
                    perderAcceso("sin-acceso")
                }
            }
            return true
        }
        pasoHecho()

        // 3) Foto: primero solo la marca; la foto en sí solo si cambió.
        val marcasFoto = Supabase.client.from(Tablas.FOTOS_BEBE).select(Columns.list("actualizado_en")) {
            filter { eq("bebe_id", bebeId) }
        }.decodeList<JsonObject>()
        var fotoServidor: String? = null
        val marcaFoto = marcasFoto.firstOrNull()?.let { marcaDe(it) }
        if (marcaFoto != null && (desde == null || marcaFoto.isAfter(leerFecha(desde) ?: Instant.EPOCH))) {
            fotoServidor = Supabase.client.from(Tablas.FOTOS_BEBE).select {
                filter { eq("bebe_id", bebeId) }
            }.decodeList<FilaFotoBebe>().firstOrNull()?.fotoBase64
        }
        pasoHecho()

        // 4) Tablas de datos, todas a la vez (el bebé ya está arriba).
        val filas: Map<String, List<JsonObject>> = coroutineScope {
            defs.filter { it.tabla != Tablas.BEBES }
                .map { def -> def.tabla to async { traer(def, bebeId, desde).also { pasoHecho() } } }
                .associate { (t, diferido) -> t to diferido.await() }
        } + (Tablas.BEBES to listOf(filaBebe))

        val premiumCompartido = try {
            Compartir.tienePremiumCompartido(bebeId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        pasoHecho()

        // Un borrado del historial solo se aplica si la fila no ha vuelto a
        // aparecer en esta descarga NI existe ahora en el servidor (la
        // descarga es incremental: una fila resubida antes no viene).
        val recibidasAhora = mutableSetOf<String>()
        for (def in defs) for (o in filas[def.tabla].orEmpty()) {
            claveDeFila(def, o)?.let { recibidasAhora += clavePendiente(def.tabla, it) }
        }
        val vivos = siguenExistiendo(borrados.filter {
            defPorTabla.containsKey(it.tabla) && clavePendiente(it.tabla, it.filaId) !in recibidasAhora
        })

        if (cambiaDeBebe && local.datos.value.bebe != null) local.respaldar("cambio-bebe")

        val duenioDelBebe = filaBebe["usuario_id"]?.jsonPrimitive?.contentOrNull?.let(::uuid)
        val invitado = duenioDelBebe != null && duenioDelBebe != miId

        local.modificar { d0 ->
            // La sesión o el bebé pueden haber cambiado mientras se bajaba.
            if (d0.motor.usuarioId != miId || d0.motor.bebeId != bebeId) return@modificar d0
            if (cambiaDeBebe && d0.pendientes.isNotEmpty()) return@modificar d0

            var d = if (cambiaDeBebe) vaciarBebe(d0) else d0
            val migrado = d.motor.migrado && !cambiaDeBebe

            // Claves que han llegado ahora (existen en el servidor).
            val recibidas = mutableSetOf<String>()
            for (def in defs) for (o in filas[def.tabla].orEmpty()) {
                claveDeFila(def, o)?.let { recibidas += clavePendiente(def.tabla, it) }
            }

            // Borrados: solo los de filas que NO han vuelto a aparecer.
            val borradosAplicables = borrados.filter {
                val ck = clavePendiente(it.tabla, it.filaId)
                defPorTabla.containsKey(it.tabla) && ck !in recibidas && ck !in vivos
            }
            val borradosSet = borradosAplicables.map { clavePendiente(it.tabla, it.filaId) }.toSet()
            // Lo pendiente de una fila ya borrada no la resucita.
            d = d.copy(pendientes = d.pendientes.filterNot {
                !it.borrado && clavePendiente(it.tabla, it.id) in borradosSet
            })

            val pendientes = d.pendientes.map { clavePendiente(it.tabla, it.id) }.toSet()
            val marcas = Marcas()
            for (def in defs) {
                d = aplicarTabla(def, d, filas[def.tabla].orEmpty(), borradosSet, pendientes,
                    protegerCambiosLocales = migrado && !(invitado && def.soloDuenio), marcas = marcas)
            }
            // Vacunas propias de un grupo borrado (el borrado en cascada no
            // deja rastro en el historial).
            val gruposBorrados = borradosAplicables.filter { it.tabla == Tablas.VACUNAS_GRUPOS }.map { it.filaId }.toSet()
            if (gruposBorrados.isNotEmpty()) {
                d = d.copy(vacunasPropias = d.vacunasPropias.filterNot { uuid(it.grupoId) in gruposBorrados })
            }

            // Foto: solo se aplica cuando el servidor TIENE foto (que no haya
            // fila no quita la de este móvil).
            val fotoPendiente = clavePendiente(Tablas.FOTOS_BEBE, bebeId) in pendientes
            if (fotoServidor != null && !fotoPendiente) d = d.copy(fotoPerfilBase64 = fotoServidor)

            // Primera mezcla: lo que solo existe en este móvil se sube "solo
            // si no existe". Si ya está en el servidor, no se pisa.
            if (!migrado) {
                val altas = mutableListOf<CambioPendiente>()
                for (def in defs) {
                    if (invitado && def.soloDuenio) continue
                    altasSoloLocales(def, d, bebeId, recibidas, pendientes, altas)
                }
                if (!invitado && d.fotoPerfilBase64 != null && marcaFoto == null && !fotoPendiente) {
                    altas += CambioPendiente(Tablas.FOTOS_BEBE, bebeId)
                }
                d = d.copy(pendientes = d.pendientes + altas)
            }

            val cursor = marcas.cursor(if (completa) null else d.motor.cursorDatos)
            // Sin borrados nuevos, el cursor de datos (hora del servidor)
            // vale: todo lo borrado antes ya ha llegado en esta pasada.
            val cursorBorr = borrados.maxByOrNull { leerFecha(it.en) ?: Instant.EPOCH }?.en
                ?: d.motor.cursorBorrados ?: cursor
            d.copy(
                premiumCompartido = premiumCompartido ?: d.premiumCompartido,
                motor = d.motor.copy(migrado = true, bebeObjetivo = null, cursorDatos = cursor, cursorBorrados = cursorBorr)
            )
        }
        return true
    }

    /** Lo más nuevo aplicado, sin pasar de la fila más vieja que se dejó sin aplicar. */
    private class Marcas {
        var maxima: Instant? = null
        var minimaOmitida: Instant? = null
        fun vista(t: Instant?) { if (t != null && (maxima == null || t.isAfter(maxima))) maxima = t }
        fun omitida(t: Instant?) { if (t != null && (minimaOmitida == null || t.isBefore(minimaOmitida))) minimaOmitida = t }
        fun cursor(anterior: String?): String? {
            val previa = anterior?.let { leerFecha(it) }
            var c = listOfNotNull(previa, maxima).maxOrNull()
            val om = minimaOmitida
            if (om != null && (c == null || om.isBefore(c))) c = om
            return c?.toString()
        }
    }

    private fun <T> aplicarTabla(
        def: Def<T>, d: DatosNubi, filas: List<JsonObject>, borrados: Set<String>,
        pendientes: Set<String>, protegerCambiosLocales: Boolean, marcas: Marcas
    ): DatosNubi {
        val tieneBorrados = borrados.any { it.startsWith(def.tabla + "|") }
        if (filas.isEmpty() && !tieneBorrados) return d
        val porClave = LinkedHashMap<String, T>()
        for (f in def.leer(d)) porClave[def.clave(f)] = f
        val base = (d.baseServidor[def.tabla] ?: emptyMap()).toMutableMap()

        // La fila del bebé se pide siempre entera: no cuenta para el cursor.
        val cuenta = def.tabla != Tablas.BEBES
        for (o in filas) {
            val marca = if (cuenta) marcaDe(o) else null
            val modelo = decodificar(def, o)
            // Fila que esta versión no sabe leer (p. ej. un valor nuevo de
            // la otra plataforma): no se aplica y el cursor no la salta.
            if (modelo == null) { marcas.omitida(marca); continue }
            val k = def.clave(modelo)
            if (clavePendiente(def.tabla, k) in pendientes) {
                // No se toca la fila local, pero se apunta cómo está en el
                // servidor: así al subir solo va lo que de verdad difiere.
                if (base[k] == null) base[k] = fila(def, modelo)
                marcas.omitida(marca)
                continue
            }
            val actual = porClave[k]
            val b = base[k]
            if (protegerCambiosLocales && b != null) {
                // Cambio o borrado local que aún no está en la cola (justo
                // entre guardarlo y marcarlo): no se pisa.
                if (actual == null || fila(def, actual) != b) { marcas.omitida(marca); continue }
            }
            porClave[k] = def.conservar?.invoke(actual, modelo) ?: modelo
            base[k] = fila(def, modelo)
            marcas.vista(marca)
        }
        if (tieneBorrados) {
            for (k in porClave.keys.toList()) {
                if (clavePendiente(def.tabla, k) in borrados) porClave.remove(k)
            }
            for (k in base.keys.toList()) {
                if (clavePendiente(def.tabla, k) in borrados) base.remove(k)
            }
        }
        return def.poner(d, porClave.values.toList()).copy(baseServidor = d.baseServidor + (def.tabla to base))
    }

    private fun <T> claveDeFila(def: Def<T>, o: JsonObject): String? = decodificar(def, o)?.let(def.clave)

    private fun <T> altasSoloLocales(
        def: Def<T>, d: DatosNubi, bebeId: String, recibidas: Set<String>,
        pendientes: Set<String>, altas: MutableList<CambioPendiente>
    ) {
        for (f in def.leer(d)) {
            if (def.bebeDe(f, d) != bebeId) continue
            val k = def.clave(f)
            val ck = clavePendiente(def.tabla, k)
            if (ck in recibidas || ck in pendientes) continue
            altas += CambioPendiente(def.tabla, k, soloSiNoExiste = true)
        }
    }

    /**
     * Bebé propio que no está en Supabase (cuenta nueva sin conexión, o
     * nunca llegó a subirse): todo lo del móvil a la cola, "solo si no
     * existe". El bebé va el primero y enviar() comprueba antes que la
     * cuenta no tenga ya otro.
     */
    private suspend fun subirTodoPorPrimeraVez(bebeId: String) {
        local.modificar { d ->
            val enCola = d.pendientes.map { clavePendiente(it.tabla, it.id) }.toSet()
            val altas = mutableListOf<CambioPendiente>()
            for (def in defs) altasSoloLocales(def, d, bebeId, emptySet(), enCola, altas)
            if (d.fotoPerfilBase64 != null && clavePendiente(Tablas.FOTOS_BEBE, bebeId) !in enCola) {
                altas += CambioPendiente(Tablas.FOTOS_BEBE, bebeId)
            }
            d.copy(pendientes = d.pendientes + altas, motor = d.motor.copy(migrado = true))
        }
    }

    /**
     * El bebé no ha llegado y no es nuestro. Solo si el servidor CONFIRMA
     * que hay sesión y que esta cuenta ya no puede verlo
     * (backend/022_estado_acceso_bebe.sql), se deja de ver en este móvil.
     * Ante cualquier duda (sin la función, error de red...), no se hace nada.
     */
    private suspend fun accesoPerdido(bebeId: String): Boolean {
        val acceso = try {
            Supabase.client.postgrest.rpc(
                "estado_acceso_bebe",
                buildJsonObject { put("p_bebe_id", bebeId) }
            ).decodeAs<AccesoBebe>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        return acceso.autenticado && !acceso.puedeVer
    }

    /**
     * Deja el móvil sin el bebé (guardando antes una copia): al perder el
     * acceso confirmado, o al dejar de ver un bebé compartido por decisión
     * propia. Se conserva lo que es del móvil (tema, idioma, avisos...).
     */
    suspend fun perderAcceso(motivo: String) {
        local.respaldar(motivo)
        local.modificar { d ->
            vaciarBebe(d).copy(
                pendientes = emptyList(),
                motor = EstadoMotor(usuarioId = d.motor.usuarioId, fallidas = (d.motor.fallidas + d.pendientes).takeLast(200))
            )
        }
    }

    /**
     * Al aceptar una invitación: en la próxima descarga se pasa a ese bebé.
     * Hasta que el servidor lo entregue, se sigue viendo lo de antes.
     */
    suspend fun cambiarABebe(bebeId: String) {
        local.modificar { d -> d.copy(motor = d.motor.copy(bebeObjetivo = uuid(bebeId))) }
    }

    private fun vaciarBebe(d: DatosNubi) = d.copy(
        bebe = null,
        registros = emptyList(),
        medicinas = emptyList(),
        medidas = emptyList(),
        citas = emptyList(),
        vacunas = emptyList(),
        gruposVacunas = emptyList(),
        vacunasPropias = emptyList(),
        diario = emptyList(),
        extracciones = emptyList(),
        usosLeche = emptyList(),
        comidas = emptyList(),
        enfermedades = emptyList(),
        fotoPerfilBase64 = null,
        despertarManual = null,
        premiumCompartido = false,
        baseServidor = emptyMap(),
        sellosRemotos = emptyMap()
    )

    private suspend fun buscarBebePropio(miId: String): String? =
        Supabase.client.from(Tablas.BEBES).select(Columns.list("id")) {
            filter { eq("usuario_id", miId) }
            order("creado_en", Order.ASCENDING)
            limit(1)
        }.decodeList<JsonObject>().firstOrNull()?.get("id")?.jsonPrimitive?.contentOrNull?.let(::uuid)

    /** El bebé de la cuenta; si no tiene, el primero compartido con ella. */
    private suspend fun buscarBebeDeLaCuenta(miId: String): String? {
        val propio = Supabase.client.from(Tablas.BEBES).select(Columns.list("id")) {
            filter { eq("usuario_id", miId) }
            order("creado_en", Order.ASCENDING)
            limit(1)
        }.decodeList<JsonObject>().firstOrNull()
        val elegido = propio ?: Supabase.client.from(Tablas.BEBES).select(Columns.list("id")) {
            order("creado_en", Order.ASCENDING)
            limit(1)
        }.decodeList<JsonObject>().firstOrNull()
        return elegido?.get("id")?.jsonPrimitive?.contentOrNull?.let(::uuid)
    }

    /**
     * Filas de una tabla para el bebé cambiadas después de [desde] (o todas
     * si es null), en orden estable (actualizado_en, desempate) y paginando
     * por clave.
     */
    private suspend fun traer(def: Def<*>, bebeId: String, desde: String?): List<JsonObject> {
        val todas = mutableListOf<JsonObject>()
        var ultimo: Pair<String, String>? = null
        while (true) {
            val u = ultimo
            val pagina = Supabase.client.from(def.tabla).select(Columns.raw(def.seleccion)) {
                filter {
                    eq(def.columnaBebe, bebeId)
                    if (desde != null) gt("actualizado_en", desde)
                    if (u != null) {
                        or {
                            gt("actualizado_en", u.first)
                            and {
                                eq("actualizado_en", u.first)
                                gt(def.desempate, u.second)
                            }
                        }
                    }
                }
                order("actualizado_en", Order.ASCENDING)
                order(def.desempate, Order.ASCENDING)
                limit(TAM_PAGINA)
            }.decodeList<JsonObject>()
            todas += pagina
            if (pagina.size.toLong() < TAM_PAGINA) break
            val ultima = pagina.last()
            val marca = marcaDe(ultima)?.toString() ?: break
            val desempate = ultima[def.desempate]?.jsonPrimitive?.contentOrNull ?: break
            ultimo = marca to desempate
        }
        return todas
    }

    private suspend fun traerBorrados(bebeId: String, desde: String?): List<Borrado> {
        val todos = mutableListOf<Borrado>()
        var ultimoId: Long? = null
        while (true) {
            val u = ultimoId
            val pagina = try {
                Supabase.client.from("historial_cambios").select(Columns.list("id", "tabla", "fila_id", "en")) {
                    filter {
                        eq("bebe_id", bebeId)
                        eq("operacion", "DELETE")
                        if (desde != null) gt("en", desde)
                        if (u != null) gt("id", u)
                    }
                    order("id", Order.ASCENDING)
                    limit(TAM_PAGINA)
                }.decodeList<JsonObject>()
            } catch (e: PostgrestRestException) {
                // Servidor sin historial_cambios todavía: sin borrados que aplicar.
                if (e.code in setOf("42P01", "PGRST205", "PGRST200")) return emptyList()
                throw e
            }
            for (o in pagina) {
                val tabla = o["tabla"]?.jsonPrimitive?.contentOrNull ?: continue
                val filaId = o["fila_id"]?.jsonPrimitive?.contentOrNull ?: continue
                val en = o["en"]?.jsonPrimitive?.contentOrNull?.let { leerFecha(it)?.toString() } ?: continue
                // La clave de vacunas_estado lleva texto ("bebe|clave"): solo
                // la parte del bebé es un uuid.
                val clave = if (tabla == Tablas.VACUNAS_ESTADO) {
                    filaId.substringBefore("|").lowercase() + "|" + filaId.substringAfter("|")
                } else uuid(filaId)
                todos += Borrado(tabla, clave, en)
            }
            if (pagina.size.toLong() < TAM_PAGINA) break
            ultimoId = pagina.last()["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: break
        }
        return todos
    }

    private fun marcaDe(o: JsonObject): Instant? =
        o["actualizado_en"]?.jsonPrimitive?.contentOrNull?.let { leerFecha(it) }

    private fun restarSolape(cursor: String): String? =
        leerFecha(cursor)?.minusSeconds(SOLAPE_SEGUNDOS)?.toString()

    // -----------------------------------------------------------------
    // Tiempo real
    //
    // Realtime se usa solo como aviso de "algo ha cambiado, repasa": quien
    // decide qué hacer con ese cambio sigue siendo sincronizar().
    // -----------------------------------------------------------------

    private var canalTiempoReal: RealtimeChannel? = null
    private var trabajoTiempoReal: Job? = null
    private val avisoDeCambioRemoto = Channel<Unit>(Channel.CONFLATED)

    /**
     * Se suscribe a los cambios remotos de todas las tablas de datos y,
     * cada vez que llega alguno, vuelve a llamar a sincronizar(). Segura
     * de llamar sin sesión (no hace nada) y más de una vez.
     */
    fun activarTiempoReal(ambitoPadre: CoroutineScope) {
        if (canalTiempoReal != null) return
        val usuarioId = Supabase.client.auth.currentUserOrNull()?.id ?: return

        val canal = Supabase.client.channel("cambios-datos-$usuarioId")
        canalTiempoReal = canal
        val trabajo = SupervisorJob(ambitoPadre.coroutineContext[Job])
        trabajoTiempoReal = trabajo
        val ambito = CoroutineScope(ambitoPadre.coroutineContext + trabajo)

        for (tabla in TABLAS_EN_TIEMPO_REAL) {
            canal.postgresChangeFlow<PostgresAction>(schema = "public") { table = tabla }
                .onEach { avisoDeCambioRemoto.trySend(Unit) }
                .launchIn(ambito)
        }

        ambito.launch {
            for (aviso in avisoDeCambioRemoto) {
                // Se agrupan varios cambios seguidos en un solo sincronizar().
                delay(800)
                sincronizar()
            }
        }

        ambito.launch {
            runCatching { canal.subscribe() }
        }
    }

    /** Corta la suscripción de tiempo real (cierre de sesión). */
    suspend fun desactivarTiempoReal() {
        val canal = soltarCanalTiempoReal() ?: return
        runCatching { canal.unsubscribe() }
    }

    /**
     * Parte síncrona de desactivarTiempoReal(): marca el canal como cerrado
     * y para sus oyentes al momento. Devuelve el canal para darse de baja.
     */
    fun soltarCanalTiempoReal(): RealtimeChannel? {
        val canal = canalTiempoReal ?: return null
        canalTiempoReal = null
        trabajoTiempoReal?.cancel()
        trabajoTiempoReal = null
        return canal
    }

    private companion object {
        /** Veces que se reintenta un evento del funnel que falla él solo. */
        const val MAX_INTENTOS_EVENTO = 5

        /** Rechazos del servidor antes de apartar un cambio a "fallidas". */
        const val MAX_INTENTOS_CAMBIO = 3

        /** Filas por página al descargar (el tope de PostgREST es 1000). */
        const val TAM_PAGINA = 500L

        /**
         * Solape al pedir "lo cambiado desde": actualizado_en lo pone el
         * servidor al empezar cada transacción, y una que tarde en confirmar
         * puede aparecer con una hora algo anterior al último cursor.
         */
        const val SOLAPE_SEGUNDOS = 120L

        fun leerFecha(s: String): Instant? = try {
            OffsetDateTime.parse(s).toInstant()
        } catch (e: Exception) {
            try { Instant.parse(s) } catch (e2: Exception) { null }
        }

        val TABLAS_EN_TIEMPO_REAL = listOf(
            Tablas.BEBES, Tablas.REGISTROS, Tablas.MEDICINAS, Tablas.MEDIDAS, Tablas.CITAS,
            Tablas.VACUNAS_ESTADO, Tablas.VACUNAS_GRUPOS, Tablas.VACUNAS_PROPIAS, Tablas.DIARIO,
            Tablas.EXTRACCIONES, Tablas.USOS_LECHE, Tablas.COMIDAS, Tablas.ENFERMEDADES,
            Tablas.FOTOS_BEBE
        )
    }
}
