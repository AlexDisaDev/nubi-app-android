package app.nubi.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

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

    private var iniciado = false

    fun iniciar(contexto: Context) {
        if (iniciado) return
        iniciado = true

        local = AlmacenLocal(contexto.applicationContext, ambito)
        sincronizador = Sincronizador(local)

        ambito.launch {
            local.cargar()
            // Primer intento de sincronizar al arrancar. Si no hay sesión o
            // no hay red, no hace nada y ya se reintentará.
            sincronizador.sincronizar()
        }
    }

    fun sincronizarEnSegundoPlano() {
        ambito.launch { sincronizador.sincronizar() }
    }

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
}
