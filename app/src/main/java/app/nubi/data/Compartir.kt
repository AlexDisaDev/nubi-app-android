package app.nubi.data

import android.net.Uri
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Bebé compartido entre cuentas (backend/016_compartir_bebe.sql y
 * 017_bebes_compartidos_select.sql): quién más, aparte del dueño, puede
 * ver y anotar un bebé.
 *
 * Fase "fácil" primero: Android <-> Android, con el mismo esquema
 * (bebes_compartidos + invitaciones_bebe) que de puente servirá luego
 * para iPhone <-> Android — ver el comentario final de
 * 016_compartir_bebe.sql. El "Nubi compartido" de iOS por CloudKit no se
 * toca para nada.
 */
object Compartir {
    private const val TABLA = "bebes_compartidos"

    @Serializable
    private data class FilaBebeCompartido(val bebe_id: String, val usuario_id: String)

    private val _invitacionPendiente = MutableStateFlow<String?>(null)

    /**
     * Token de una invitación recibida por enlace (nubi://unirme?token=...)
     * a falta de confirmar. La UI (p.ej. PantallaRaiz) observa esto y, en
     * cuanto hay sesión, ofrece aceptarla.
     */
    val invitacionPendiente: StateFlow<String?> = _invitacionPendiente.asStateFlow()

    /**
     * Se llama desde MainActivity con el Uri de cualquier intent entrante.
     * Si es un enlace de invitación (nubi://unirme?token=...), guarda el
     * token para que la UI lo recoja; si no, no hace nada.
     */
    fun recibirEnlace(uri: Uri?) {
        if (uri == null || uri.host != "unirme") return
        val token = uri.getQueryParameter("token") ?: return
        _invitacionPendiente.value = token
    }

    /** La UI llama a esto tras aceptar (o cancelar) para no volver a preguntar. */
    fun descartarInvitacionPendiente() {
        _invitacionPendiente.value = null
    }

    /** El dueño crea una invitación; devuelve el token corto para el enlace. */
    suspend fun crearInvitacion(bebeId: String): String =
        Supabase.client.postgrest.rpc(
            "crear_invitacion_bebe",
            buildJsonObject { put("p_bebe_id", bebeId) }
        ).decodeAs()

    /** El invitado canjea el token; devuelve el id del bebé al que se unió. */
    suspend fun aceptarInvitacion(token: String): String =
        Supabase.client.postgrest.rpc(
            "aceptar_invitacion_bebe",
            buildJsonObject { put("p_token", token) }
        ).decodeAs()

    /** Cuántas personas (aparte del dueño) tienen acceso a este bebé ahora mismo. */
    suspend fun contarInvitados(bebeId: String): Int =
        Supabase.client.from(TABLA).select {
            filter { eq("bebe_id", bebeId) }
        }.decodeList<FilaBebeCompartido>().size

    /** ¿Este móvil ve el bebé porque se lo compartieron, y no porque sea el dueño? */
    suspend fun soyInvitadoDe(bebeId: String): Boolean {
        val miId = Supabase.client.auth.currentUserOrNull()?.id ?: return false
        return Supabase.client.from(TABLA).select {
            filter {
                eq("bebe_id", bebeId)
                eq("usuario_id", miId)
            }
        }.decodeList<FilaBebeCompartido>().isNotEmpty()
    }

    /**
     * El INVITADO deja de ver el bebé compartido: se desvincula por completo
     * (solo borra su propia fila — la RLS tampoco le deja borrar la de nadie
     * más). Igual que "Dejar de ver datos compartidos" de iOS, pero sin tocar
     * ningún dato del bebé, solo el acceso.
     */
    suspend fun dejarDeVerCompartido(bebeId: String) {
        val miId = Supabase.client.auth.currentUserOrNull()?.id ?: return
        Supabase.client.from(TABLA).delete {
            filter {
                eq("bebe_id", bebeId)
                eq("usuario_id", miId)
            }
        }
    }

    /**
     * El DUEÑO deja de compartir: revoca el acceso de TODOS los invitados a
     * este bebé de golpe (la RLS solo deja hacer esto al propio dueño).
     */
    suspend fun dejarDeCompartir(bebeId: String) {
        Supabase.client.from(TABLA).delete {
            filter { eq("bebe_id", bebeId) }
        }
    }

    /**
     * ¿El DUEÑO de este bebé tiene premium activo AHORA MISMO? (backend/0xx:
     * ver tiene_premium_compartido en Supabase.) Es la pieza que faltaba
     * para que compartir funcione igual que entre iPhones por CKShare, pero
     * de verdad: si quien invitó paga, los invitados ven premium; si no,
     * no. Sirve igual para Android<->Android que para iPhone<->Android,
     * porque no depende de CloudKit — ver Sincronizador.bajarCambios(),
     * que guarda el resultado en DatosNubi.premiumCompartido para que
     * Premium.activo() lo use sin tener que llamar aquí desde cada pantalla.
     *
     * Devuelve false si falla la llamada (sin red, etc.): nunca se debe
     * tratar un fallo de red como "sí hay premium compartido".
     */
    suspend fun tienePremiumCompartido(bebeId: String): Boolean =
        runCatching {
            Supabase.client.postgrest.rpc(
                "tiene_premium_compartido",
                buildJsonObject { put("p_bebe_id", bebeId) }
            ).decodeAs<Boolean>()
        }.getOrDefault(false)
}
