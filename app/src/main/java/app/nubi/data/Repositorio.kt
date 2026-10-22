package app.nubi.data

import android.content.Context
import app.nubi.notificaciones.AvisosNubi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Punto único desde el que la interfaz accede a los datos.
 *
 * Se crea una sola vez al arrancar la app (ver NubiApplication) para que
 * todas las pantallas compartan el mismo almacén y el mismo sincronizador.
 */
object Repositorio {

    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var local: AlmacenLocal
        private set

    lateinit var sincronizador: Sincronizador
        private set

    private lateinit var contexto: Context

    private var iniciado = false

    /**
     * Hora (en punto, hora local) a la que avisa "Avisarme la tarde de
     * antes" de una cita: el día anterior a la cita, a esta hora.
     * TODO: confirmar que coincide con la hora que usa iOS — de momento es
     * una suposición razonable (20:00), no viene de ninguna captura.
     */
    private const val HORA_AVISO_CITA_PREVIA = 20

    fun iniciar(contexto: Context) {
        if (iniciado) return
        iniciado = true

        this.contexto = contexto.applicationContext
        local = AlmacenLocal(this.contexto, ambito)
        sincronizador = Sincronizador(local)

        AvisosNubi.crearCanal(this.contexto)

        ambito.launch {
            local.cargar()
            // Primer intento de sincronizar al arrancar. Si no hay sesión o
            // no hay red, no hace nada y ya se reintentará.
            sincronizador.sincronizar()
            // Y se repasan los avisos por si algo cambió mientras la app
            // estaba cerrada (por ejemplo, una toma de hace rato que ya
            // tendría que haber avisado y hay que descartar).
            reprogramarAvisos(this@Repositorio.contexto)
        }
    }

    /** Espera a que termine de leer nubi.json. La usa ReceptorArranque. */
    suspend fun esperarCarga() {
        local.cargando.first { !it }
    }

    /**
     * Vuelve a programar todos los avisos a partir de lo que hay en local
     * ahora mismo. Se llama al arrancar la app y tras reiniciar el móvil
     * (AlarmManager olvida las alarmas programadas en cada reinicio).
     */
    fun reprogramarAvisos(contexto: Context = this.contexto) {
        val datos = local.datos.value
        datos.medicinas.forEach { avisarDeMedicina(contexto, it) }
        datos.citas.forEach { avisarDeCita(contexto, it) }
    }

    private fun avisarDeMedicina(contexto: Context, medicina: Medicina) {
        val id = "medicina_${medicina.id}"
        val siguiente = medicina.horaSiguiente
        if (siguiente == null) {
            AvisosNubi.cancelar(contexto, id)
            return
        }
        val texto = medicina.nombre.ifBlank { "Toca la siguiente toma" }
        AvisosNubi.programar(contexto, id, siguiente, "Hora de la siguiente toma", texto)
    }

    private fun avisarDeCita(contexto: Context, cita: Cita) {
        val id = "cita_${cita.id}"
        if (!cita.recordar) {
            AvisosNubi.cancelar(contexto, id)
            return
        }
        val zona = ZoneId.systemDefault()
        val vispera = cita.fecha.atZone(zona).toLocalDate()
            .minusDays(1)
            .atTime(HORA_AVISO_CITA_PREVIA, 0)
            .atZone(zona)
            .toInstant()
        val texto = buildString {
            append(cita.titulo.ifBlank { "Tienes una cita" })
            if (cita.lugar.isNotBlank()) append(" · ${cita.lugar}")
        }
        AvisosNubi.programar(contexto, id, vispera, "Cita mañana", texto)
    }

    fun sincronizarEnSegundoPlano() {
        ambito.launch { sincronizador.sincronizar() }
    }

    /**
     * ¿Puede usar ahora mismo las funciones de pago (prueba de 7 días o
     * premium activo)? Mismo cálculo que puede_registrar() en Supabase.
     *
     * Si todavía no se ha sincronizado ni una vez (perfil == null, por
     * ejemplo nada más instalar sin conexión), se deja pasar: es preferible
     * no bloquear a alguien real por una lectura que aún no ha llegado:
     * en cuanto sincronice, el estado real manda.
     */
    fun puedeUsarApp(): Boolean = local.datos.value.perfil?.puedeUsarApp() ?: true

    // -----------------------------------------------------------------
    // Operaciones. Todas siguen el mismo patrón: escribir en local
    // (que funciona siempre), marcar como pendiente, y empujar a Supabase
    // sin bloquear a quien llama.
    // -----------------------------------------------------------------

    /** Tema claro/oscuro. No se marca como pendiente: es de este móvil. */
    fun guardarTema(valor: String) {
        ambito.launch { local.modificar { it.copy(tema = valor) } }
    }

    fun guardarBebe(bebe: Bebe) {
        ambito.launch {
            local.modificar { it.copy(bebe = bebe.copy(actualizadoEn = Instant.now())) }
            local.marcarPendiente(Tablas.BEBES, bebe.id)
            sincronizador.sincronizar()
        }
    }

    fun guardarRegistro(registro: Registro) {
        ambito.launch {
            val actualizado = registro.copy(actualizadoEn = Instant.now())
            local.modificar { d ->
                d.copy(registros = d.registros.filterNot { it.id == actualizado.id } + actualizado)
            }
            local.marcarPendiente(Tablas.REGISTROS, actualizado.id)
            sincronizador.sincronizar()
        }
    }

    fun borrarRegistro(id: String) {
        ambito.launch {
            local.modificar { d -> d.copy(registros = d.registros.filterNot { it.id == id }) }
            local.marcarPendiente(Tablas.REGISTROS, id, borrado = true)
            sincronizador.sincronizar()
        }
    }

    fun guardarMedicina(medicina: Medicina) {
        avisarDeMedicina(contexto, medicina)
        ambito.launch {
            val actualizada = medicina.copy(actualizadoEn = Instant.now())
            local.modificar { d ->
                d.copy(medicinas = d.medicinas.filterNot { it.id == actualizada.id } + actualizada)
            }
            local.marcarPendiente(Tablas.MEDICINAS, actualizada.id)
            sincronizador.sincronizar()
        }
    }

    fun borrarMedicina(id: String) {
        AvisosNubi.cancelar(contexto, "medicina_$id")
        ambito.launch {
            local.modificar { d -> d.copy(medicinas = d.medicinas.filterNot { it.id == id }) }
            local.marcarPendiente(Tablas.MEDICINAS, id, borrado = true)
            sincronizador.sincronizar()
        }
    }

    fun guardarMedida(medida: Medida) {
        ambito.launch {
            val actualizada = medida.copy(actualizadoEn = Instant.now())
            local.modificar { d ->
                d.copy(medidas = d.medidas.filterNot { it.id == actualizada.id } + actualizada)
            }
            local.marcarPendiente(Tablas.MEDIDAS, actualizada.id)
            sincronizador.sincronizar()
        }
    }

    fun borrarMedida(id: String) {
        ambito.launch {
            local.modificar { d -> d.copy(medidas = d.medidas.filterNot { it.id == id }) }
            local.marcarPendiente(Tablas.MEDIDAS, id, borrado = true)
            sincronizador.sincronizar()
        }
    }

    // -----------------------------------------------------------------
    // Salud: citas, vacunas (oficiales y propias) y enfermedades.
    // -----------------------------------------------------------------

    /** Comunidad para el calendario vacunal. No se marca como pendiente: es de este móvil. */
    fun guardarComunidad(valor: String) {
        ambito.launch { local.modificar { it.copy(comunidad = valor) } }
    }

    fun guardarCita(cita: Cita) {
        avisarDeCita(contexto, cita)
        ambito.launch {
            val actualizada = cita.copy(actualizadoEn = Instant.now())
            local.modificar { d ->
                d.copy(citas = d.citas.filterNot { it.id == actualizada.id } + actualizada)
            }
            local.marcarPendiente(Tablas.CITAS, actualizada.id)
            sincronizador.sincronizar()
        }
    }

    fun borrarCita(id: String) {
        AvisosNubi.cancelar(contexto, "cita_$id")
        ambito.launch {
            local.modificar { d -> d.copy(citas = d.citas.filterNot { it.id == id }) }
            local.marcarPendiente(Tablas.CITAS, id, borrado = true)
            sincronizador.sincronizar()
        }
    }

    /**
     * Marca puesta (o no) una vacuna del calendario oficial. vacunas_estado
     * no tiene id propio: su clave es (bebeId, clave), así que el pendiente
     * se guarda como "bebeId|clave" (ver Sincronizador.subirFila).
     */
    fun marcarVacuna(bebeId: String, clave: String, puesta: Boolean, fecha: Instant? = null) {
        ambito.launch {
            local.modificar { d ->
                val actual = d.vacunas.find { it.bebeId == bebeId && it.clave == clave }
                val actualizado = (actual ?: EstadoVacuna(bebeId = bebeId, clave = clave)).copy(
                    puesta = puesta,
                    fecha = if (puesta) (fecha ?: actual?.fecha ?: Instant.now()) else null,
                    actualizadoEn = Instant.now()
                )
                d.copy(vacunas = d.vacunas.filterNot { it.bebeId == bebeId && it.clave == clave } + actualizado)
            }
            local.marcarPendiente(Tablas.VACUNAS_ESTADO, "$bebeId|$clave")
            sincronizador.sincronizar()
        }
    }

    fun anadirGrupoPropio(bebeId: String, etiqueta: String, meses: Double) {
        ambito.launch {
            val grupo = GrupoVacunas(
                bebeId = bebeId, etiqueta = etiqueta, meses = meses, actualizadoEn = Instant.now()
            )
            local.modificar { d -> d.copy(gruposVacunas = d.gruposVacunas + grupo) }
            local.marcarPendiente(Tablas.VACUNAS_GRUPOS, grupo.id)
            sincronizador.sincronizar()
        }
    }

    fun borrarGrupoPropio(id: String) {
        ambito.launch {
            val hijos = local.datos.value.vacunasPropias.filter { it.grupoId == id }
            local.modificar { d ->
                d.copy(
                    gruposVacunas = d.gruposVacunas.filterNot { it.id == id },
                    // El borrado del grupo en Supabase arrastra a sus vacunas
                    // propias (on delete cascade); aquí se quitan igual de
                    // la lista local para que la pantalla no las siga viendo.
                    vacunasPropias = d.vacunasPropias.filterNot { it.grupoId == id }
                )
            }
            local.marcarPendiente(Tablas.VACUNAS_GRUPOS, id, borrado = true)
            hijos.forEach { local.marcarPendiente(Tablas.VACUNAS_PROPIAS, it.id, borrado = true) }
            sincronizador.sincronizar()
        }
    }

    fun anadirVacunaPropia(grupoId: String, nombre: String, puesta: Boolean = false, fecha: Instant? = null) {
        ambito.launch {
            val vacuna = VacunaPropia(
                grupoId = grupoId, nombre = nombre, puesta = puesta,
                fecha = if (puesta) (fecha ?: Instant.now()) else null,
                actualizadoEn = Instant.now()
            )
            local.modificar { d -> d.copy(vacunasPropias = d.vacunasPropias + vacuna) }
            local.marcarPendiente(Tablas.VACUNAS_PROPIAS, vacuna.id)
            sincronizador.sincronizar()
        }
    }

    fun marcarVacunaPropia(id: String, puesta: Boolean) {
        ambito.launch {
            local.modificar { d ->
                val actual = d.vacunasPropias.find { it.id == id } ?: return@modificar d
                val actualizada = actual.copy(
                    puesta = puesta,
                    fecha = if (puesta) (actual.fecha ?: Instant.now()) else null,
                    actualizadoEn = Instant.now()
                )
                d.copy(vacunasPropias = d.vacunasPropias.filterNot { it.id == id } + actualizada)
            }
            local.marcarPendiente(Tablas.VACUNAS_PROPIAS, id)
            sincronizador.sincronizar()
        }
    }

    fun borrarVacunaPropia(id: String) {
        ambito.launch {
            local.modificar { d -> d.copy(vacunasPropias = d.vacunasPropias.filterNot { it.id == id }) }
            local.marcarPendiente(Tablas.VACUNAS_PROPIAS, id, borrado = true)
            sincronizador.sincronizar()
        }
    }

    fun guardarEnfermedad(enfermedad: Enfermedad) {
        ambito.launch {
            val actualizada = enfermedad.copy(actualizadoEn = Instant.now())
            local.modificar { d ->
                d.copy(enfermedades = d.enfermedades.filterNot { it.id == actualizada.id } + actualizada)
            }
            local.marcarPendiente(Tablas.ENFERMEDADES, actualizada.id)
            sincronizador.sincronizar()
        }
    }

    fun borrarEnfermedad(id: String) {
        ambito.launch {
            local.modificar { d -> d.copy(enfermedades = d.enfermedades.filterNot { it.id == id }) }
            local.marcarPendiente(Tablas.ENFERMEDADES, id, borrado = true)
            sincronizador.sincronizar()
        }
    }

    // -----------------------------------------------------------------
    // Diario
    // -----------------------------------------------------------------

    fun guardarEntradaDiario(entrada: EntradaDiario) {
        ambito.launch {
            val actualizada = entrada.copy(actualizadoEn = Instant.now())
            local.modificar { d ->
                d.copy(diario = d.diario.filterNot { it.id == actualizada.id } + actualizada)
            }
            local.marcarPendiente(Tablas.DIARIO, actualizada.id)
            sincronizador.sincronizar()
        }
    }

    fun borrarEntradaDiario(id: String) {
        ambito.launch {
            local.modificar { d -> d.copy(diario = d.diario.filterNot { it.id == id }) }
            local.marcarPendiente(Tablas.DIARIO, id, borrado = true)
            sincronizador.sincronizar()
        }
    }

    // -----------------------------------------------------------------
    // Banco de leche
    // -----------------------------------------------------------------

    fun guardarExtraccion(extraccion: ExtraccionLeche) {
        ambito.launch {
            val actualizada = extraccion.copy(actualizadoEn = Instant.now())
            local.modificar { d ->
                d.copy(extracciones = d.extracciones.filterNot { it.id == actualizada.id } + actualizada)
            }
            local.marcarPendiente(Tablas.EXTRACCIONES, actualizada.id)
            sincronizador.sincronizar()
        }
    }

    fun borrarExtraccion(id: String) {
        ambito.launch {
            local.modificar { d -> d.copy(extracciones = d.extracciones.filterNot { it.id == id }) }
            local.marcarPendiente(Tablas.EXTRACCIONES, id, borrado = true)
            sincronizador.sincronizar()
        }
    }

    fun guardarUsoLeche(uso: UsoLeche) {
        ambito.launch {
            val actualizado = uso.copy(actualizadoEn = Instant.now())
            local.modificar { d ->
                d.copy(usosLeche = d.usosLeche.filterNot { it.id == actualizado.id } + actualizado)
            }
            local.marcarPendiente(Tablas.USOS_LECHE, actualizado.id)
            sincronizador.sincronizar()
        }
    }

    fun borrarUsoLeche(id: String) {
        ambito.launch {
            local.modificar { d -> d.copy(usosLeche = d.usosLeche.filterNot { it.id == id }) }
            local.marcarPendiente(Tablas.USOS_LECHE, id, borrado = true)
            sincronizador.sincronizar()
        }
    }

    // -----------------------------------------------------------------
    // Alimentación complementaria
    // -----------------------------------------------------------------

    fun guardarComida(comida: ComidaComplementaria) {
        ambito.launch {
            val actualizada = comida.copy(actualizadoEn = Instant.now())
            local.modificar { d ->
                d.copy(comidas = d.comidas.filterNot { it.id == actualizada.id } + actualizada)
            }
            local.marcarPendiente(Tablas.COMIDAS, actualizada.id)
            sincronizador.sincronizar()
        }
    }

    fun borrarComida(id: String) {
        ambito.launch {
            local.modificar { d -> d.copy(comidas = d.comidas.filterNot { it.id == id }) }
            local.marcarPendiente(Tablas.COMIDAS, id, borrado = true)
            sincronizador.sincronizar()
        }
    }
}
