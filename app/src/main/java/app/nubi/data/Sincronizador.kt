package app.nubi.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

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
}

/**
 * Una fila creada, cambiada o borrada en este móvil que todavía no se ha
 * mandado a Supabase.
 */
@Serializable
data class CambioPendiente(
    val tabla: String,
    val id: String,
    val borrado: Boolean = false
)

/**
 * Pone al día Supabase con lo de este móvil y viceversa.
 *
 * REGLAS, que es lo que de verdad importa en un sincronizador:
 *
 * 1. El móvil manda sobre lo suyo pendiente. Si una fila está en la lista
 *    de pendientes, lo que venga del servidor para esa fila se ignora: lo
 *    que el usuario acaba de apuntar nunca se pisa con una versión vieja.
 * 2. Para el resto, gana la marca de tiempo más reciente (actualizado_en).
 * 3. Nada se borra en local por no venir del servidor. Si una fila no
 *    aparece en la respuesta, se queda como está; borrar solo ocurre
 *    cuando el usuario lo pide expresamente, y entonces va como un
 *    pendiente de tipo borrado.
 *
 * La regla 3 es deliberadamente conservadora: ante la duda, se conservan
 * los datos del bebé aunque sobre alguna fila de más.
 */
class Sincronizador(private val local: AlmacenLocal) {

    enum class Estado { EN_REPOSO, SINCRONIZANDO, SIN_CONEXION, ERROR }

    private val _estado = MutableStateFlow(Estado.EN_REPOSO)
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    private val _ultimoError = MutableStateFlow<String?>(null)
    val ultimoError: StateFlow<String?> = _ultimoError.asStateFlow()

    // Para que la UI pueda esperar a que termine (con éxito o sin él) el
    // primer intento de sincronizar antes de decidir que no hay bebé — ver
    // el comentario de uso en MainActivity.ContenidoApp(). Sin esto, en un
    // móvil nuevo con una cuenta YA existente, local.cargar() deja
    // datos.bebe en null casi al instante (el archivo local está vacío) y
    // la app ofrece crear un bebé duplicado mientras el bebé real de
    // Supabase todavía está bajando en segundo plano.
    private val _primeraSincronizacionHecha = MutableStateFlow(false)
    val primeraSincronizacionHecha: StateFlow<Boolean> = _primeraSincronizacionHecha.asStateFlow()

    /**
     * Sube lo pendiente y baja lo que haya cambiado. Es seguro llamarla
     * tantas veces como haga falta: si no hay sesión o no hay red, no hace
     * nada y lo pendiente sigue pendiente.
     */
    suspend fun sincronizar() {
        if (Supabase.client.auth.currentSessionOrNull() == null) return
        if (_estado.value == Estado.SINCRONIZANDO) return

        _estado.value = Estado.SINCRONIZANDO
        try {
            withContext(Dispatchers.IO) {
                subirPendientes()
                registrarPlataformaSiHaceFalta()
                subirEventosFunnelPendientes()
                bajarCambios()
            }
            _estado.value = Estado.EN_REPOSO
            _ultimoError.value = null
            local.modificar { it.copy(ultimaSincronizacion = Instant.now()) }
        } catch (e: Exception) {
            // Sin conexión no es un fallo: es el estado normal de un parque.
            // Lo pendiente se queda pendiente y se reintenta más tarde.
            _estado.value = Estado.SIN_CONEXION
            _ultimoError.value = e.message
        } finally {
            // Se marca pase lo que pase (éxito, sin conexión o error): si
            // de verdad no hay red, no tiene sentido dejar a alguien
            // esperando para siempre a un intento que nunca llegará.
            _primeraSincronizacionHecha.value = true
        }
    }

    // -----------------------------------------------------------------
    // Subida
    // -----------------------------------------------------------------
    private suspend fun subirPendientes() {
        val datos = local.datos.value
        if (datos.pendientes.isEmpty()) return

        val hechos = mutableListOf<CambioPendiente>()

        for (cambio in datos.pendientes) {
            val ok = runCatching {
                if (cambio.borrado) borrarRemoto(cambio)
                else subirFila(datos, cambio)
            }.isSuccess

            // Si una fila concreta falla (por ejemplo, datos inválidos), se
            // deja pendiente y se sigue con las demás en vez de abortar
            // la sincronización entera.
            if (ok) hechos += cambio
        }

        if (hechos.isNotEmpty()) {
            local.modificar { d -> d.copy(pendientes = d.pendientes - hechos.toSet()) }
        }
    }

    private suspend fun borrarRemoto(cambio: CambioPendiente) {
        Supabase.client.from(cambio.tabla).delete {
            filter { eq("id", cambio.id) }
        }
    }

    private suspend fun subirFila(datos: DatosNubi, cambio: CambioPendiente) {
        when (cambio.tabla) {
            Tablas.BEBES ->
                datos.bebe?.takeIf { it.id == cambio.id }?.let { subir(cambio.tabla, it) }

            Tablas.REGISTROS ->
                datos.registros.find { it.id == cambio.id }?.let { subir(cambio.tabla, it) }

            Tablas.MEDICINAS ->
                datos.medicinas.find { it.id == cambio.id }?.let { subir(cambio.tabla, it) }

            Tablas.MEDIDAS ->
                datos.medidas.find { it.id == cambio.id }?.let { subir(cambio.tabla, it) }

            Tablas.CITAS ->
                datos.citas.find { it.id == cambio.id }?.let { subir(cambio.tabla, it) }

            // vacunas_estado no tiene id propio: su clave es (bebe_id, clave),
            // y en los pendientes se guarda como "bebeId|clave".
            Tablas.VACUNAS_ESTADO -> {
                val partes = cambio.id.split("|", limit = 2)
                datos.vacunas.find { it.bebeId == partes.getOrNull(0) && it.clave == partes.getOrNull(1) }
                    ?.let { subir(cambio.tabla, it) }
            }

            Tablas.VACUNAS_GRUPOS ->
                datos.gruposVacunas.find { it.id == cambio.id }?.let { subir(cambio.tabla, it) }

            Tablas.VACUNAS_PROPIAS ->
                datos.vacunasPropias.find { it.id == cambio.id }?.let { subir(cambio.tabla, it) }

            Tablas.DIARIO ->
                datos.diario.find { it.id == cambio.id }?.let { subir(cambio.tabla, it) }

            Tablas.EXTRACCIONES ->
                datos.extracciones.find { it.id == cambio.id }?.let { subir(cambio.tabla, it) }

            Tablas.USOS_LECHE ->
                datos.usosLeche.find { it.id == cambio.id }?.let { subir(cambio.tabla, it) }

            Tablas.COMIDAS ->
                datos.comidas.find { it.id == cambio.id }?.let { subir(cambio.tabla, it) }

            Tablas.ENFERMEDADES ->
                datos.enfermedades.find { it.id == cambio.id }?.let { subir(cambio.tabla, it) }
        }
    }

    private suspend inline fun <reified T : Any> subir(tabla: String, fila: T) {
        Supabase.client.from(tabla).upsert(fila)
    }

    /**
     * Sube los eventos del funnel pendientes (ver EventosFunnel.kt). Son
     * filas de solo inserción —nunca se actualizan ni se leen de vuelta—,
     * así que no entran en el merge de bajarCambios(): se suben uno a uno
     * y se quitan de la cola local solo los que confirma el insert. Si
     * alguno falla (p. ej. sin red), se queda pendiente y se reintenta en
     * la siguiente sincronización, igual que subirPendientes().
     */
    /**
     * Deja constancia, una sola vez, de que esta cuenta usa Android.
     *
     * Los eventos que crea el servidor (registro_completado, bebe_creado)
     * no pueden saber la plataforma: un trigger de Postgres no ve si el
     * INSERT venía de un móvil o de otro. Guardándolo una vez en el perfil
     * (ver backend/012_plataforma_por_usuario.sql), las vistas del embudo
     * pueden resolverlo para todos los eventos de ese usuario, incluidos
     * los que ya estaban guardados.
     *
     * Se marca en local al conseguirlo para no repetir la llamada en cada
     * sincronización. Si falla, se reintenta en la siguiente.
     */
    private suspend fun registrarPlataformaSiHaceFalta() {
        if (local.datos.value.plataformaRegistrada) return
        if (Supabase.client.auth.currentUserOrNull() == null) return

        val ok = runCatching {
            Supabase.client.postgrest.rpc(
                "registrar_plataforma",
                buildJsonObject { put("p_plataforma", "android") }
            )
        }.isSuccess

        if (ok) local.modificar { it.copy(plataformaRegistrada = true) }
    }

    /**
     * Sube los eventos del embudo que estén pendientes.
     *
     * Dos tipos de fallo que conviene no confundir:
     *
     * - **Fallo general** (sin red, servidor caído): fallan todos. No se
     *   descarta nada ni se cuenta ningún intento, porque los eventos están
     *   bien; lo que no está es la conexión. Se reintentan enteros más
     *   tarde.
     * - **Fallo de un evento concreto** (algo en esa fila que el servidor
     *   nunca va a aceptar): falla él mientras otros del mismo lote sí
     *   entran. Ese sí suma intento, y tras [MAX_INTENTOS_EVENTO] se
     *   descarta. Si no, se quedaría atascado para siempre, reintentándose
     *   en cada sincronización y sin llegar nunca.
     */
    private suspend fun subirEventosFunnelPendientes() {
        val pendientes = local.datos.value.eventosFunnel
        if (pendientes.isEmpty()) return

        val miId = Supabase.client.auth.currentUserOrNull()?.id
        val dispositivoId = local.dispositivoIdEstable()
        val subidos = mutableSetOf<String>()
        val fallados = mutableSetOf<String>()

        for (evento in pendientes) {
            val ok = runCatching {
                Supabase.client.from(Tablas.EVENTOS_FUNNEL).insert(
                    FilaEventoFunnel(
                        usuarioId = miId,
                        dispositivoId = dispositivoId,
                        evento = evento.evento,
                        metadata = evento.metadata,
                        creadoEn = evento.creadoEn
                    )
                )
            }.isSuccess
            if (ok) subidos += evento.idLocal else fallados += evento.idLocal
        }

        // Si no entró ninguno, se asume fallo general: no se toca la cola.
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

    /**
     * PostgREST solo devuelve 1000 filas por consulta aunque no se pida
     * límite ninguno (tope de servidor, no de esta app). Con un bebé de
     * meses esto no se nota, pero un diario o un historial de medidas puede
     * superarlo con el tiempo — sin esto, las filas de más allá de la 1000
     * simplemente desaparecerían de este móvil en cada sincronización.
     */
    private suspend inline fun <reified T : Any> traerTodo(tabla: String): List<T> {
        val tamanoPagina = 1000L
        val resultado = mutableListOf<T>()
        var desde = 0L
        while (true) {
            val pagina = Supabase.client.from(tabla).select {
                range(desde, desde + tamanoPagina - 1)
            }.decodeList<T>()
            resultado += pagina
            if (pagina.size < tamanoPagina) break
            desde += tamanoPagina
        }
        return resultado
    }

    // -----------------------------------------------------------------
    // Bajada
    //
    // De momento se trae todo lo del usuario en cada sincronización. Es más
    // tráfico del necesario, pero las filas de un bebé se cuentan por miles
    // como mucho, y evita toda una familia de errores de "me faltó un
    // cambio por culpa del filtro de fecha". Cuando el volumen lo pida, se
    // filtra por actualizado_en > ultimaSincronizacion.
    //
    // No hace falta filtrar por usuario: de eso ya se encarga la seguridad
    // por filas (RLS) del propio Supabase.
    // -----------------------------------------------------------------
    private suspend fun bajarCambios() {
        val pendientes = local.datos.value.pendientes.map { it.id }.toSet()

        val bebes = traerTodo<Bebe>(Tablas.BEBES)
        val registros = traerTodo<Registro>(Tablas.REGISTROS)
        val medicinas = traerTodo<Medicina>(Tablas.MEDICINAS)
        val medidas = traerTodo<Medida>(Tablas.MEDIDAS)
        val citas = traerTodo<Cita>(Tablas.CITAS)
        val vacunas = traerTodo<EstadoVacuna>(Tablas.VACUNAS_ESTADO)
        val grupos = traerTodo<GrupoVacunas>(Tablas.VACUNAS_GRUPOS)
        val propias = traerTodo<VacunaPropia>(Tablas.VACUNAS_PROPIAS)
        val diario = traerTodo<EntradaDiario>(Tablas.DIARIO)
        val extracciones = traerTodo<ExtraccionLeche>(Tablas.EXTRACCIONES)
        val usos = traerTodo<UsoLeche>(Tablas.USOS_LECHE)
        val comidas = traerTodo<ComidaComplementaria>(Tablas.COMIDAS)
        val enfermedades = traerTodo<Enfermedad>(Tablas.ENFERMEDADES)

        // El perfil (prueba/premium) no tiene pendientes propios: lo gestiona
        // el servidor en exclusiva, aquí solo se lee la fila del usuario.
        val miId = Supabase.client.auth.currentUserOrNull()?.id
        val perfil = miId?.let {
            Supabase.client.from(Tablas.PERFILES).select {
                filter { eq("id", it) }
            }.decodeList<Perfil>().firstOrNull()
        }

        local.modificar { d ->
            d.copy(
                bebe = bebes.firstOrNull()?.takeIf { it.id !in pendientes } ?: d.bebe,
                perfil = perfil ?: d.perfil,
                registros = fundir(d.registros, registros, pendientes, { it.id }, { it.actualizadoEn }),
                medicinas = fundir(d.medicinas, medicinas, pendientes, { it.id }, { it.actualizadoEn }),
                medidas = fundir(d.medidas, medidas, pendientes, { it.id }, { it.actualizadoEn }),
                citas = fundir(d.citas, citas, pendientes, { it.id }, { it.actualizadoEn }),
                vacunas = fundir(d.vacunas, vacunas, pendientes,
                    { "${it.bebeId}|${it.clave}" }, { it.actualizadoEn }),
                gruposVacunas = fundir(d.gruposVacunas, grupos, pendientes, { it.id }, { it.actualizadoEn }),
                vacunasPropias = fundir(d.vacunasPropias, propias, pendientes, { it.id }, { it.actualizadoEn }),
                diario = fundir(d.diario, diario, pendientes, { it.id }, { it.actualizadoEn }),
                extracciones = fundir(d.extracciones, extracciones, pendientes, { it.id }, { it.actualizadoEn }),
                usosLeche = fundir(d.usosLeche, usos, pendientes, { it.id }, { it.actualizadoEn }),
                comidas = fundir(d.comidas, comidas, pendientes, { it.id }, { it.actualizadoEn }),
                enfermedades = fundir(d.enfermedades, enfermedades, pendientes, { it.id }, { it.actualizadoEn })
            )
        }
    }

    /**
     * Mezcla la lista local con la del servidor aplicando las tres reglas
     * de arriba: lo pendiente no se toca, de lo demás gana lo más reciente,
     * y nada local desaparece por no venir del servidor.
     */
    private fun <T> fundir(
        locales: List<T>,
        remotas: List<T>,
        pendientes: Set<String>,
        clave: (T) -> String,
        marca: (T) -> Instant
    ): List<T> {
        val resultado = locales.associateByTo(LinkedHashMap(), clave)

        for (remota in remotas) {
            val k = clave(remota)
            if (k in pendientes) continue          // regla 1
            val actual = resultado[k]
            if (actual == null || marca(remota).isAfter(marca(actual))) {
                resultado[k] = remota              // regla 2
            }
        }
        return resultado.values.toList()
    }

    private companion object {
        /**
         * Veces que se reintenta un evento del funnel que falla él solo
         * (mientras otros del mismo lote sí suben) antes de descartarlo.
         */
        const val MAX_INTENTOS_EVENTO = 5
    }
}
