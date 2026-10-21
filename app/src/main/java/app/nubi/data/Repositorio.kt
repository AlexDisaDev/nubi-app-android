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
}
