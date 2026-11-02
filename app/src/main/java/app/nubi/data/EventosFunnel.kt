package app.nubi.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Nombres de evento del embudo de conversión (ver backend/006_eventos_funnel.sql
 * y backend/007_funnel_registro_y_bebe.sql). EXACTAMENTE los mismos nombres
 * que usa iOS (ver EventosFunnel.swift) — es el contrato entre las dos
 * plataformas y las consultas que se hagan luego sobre la tabla. No los
 * cambies sin cambiarlos también ahí.
 *
 * "registro_completado" y "bebe_creado" NO se registran desde aquí: los
 * inserta el servidor con un trigger (ver 007_funnel_registro_y_bebe.sql),
 * porque eso es más robusto que cualquier comprobación en la app.
 */
object EventoNombre {
    const val PAYWALL_MOSTRADO = "paywall_mostrado"
    const val PLAN_SELECCIONADO = "plan_seleccionado"
    const val TRIAL_INICIADO = "trial_iniciado"
    const val TRIAL_CONFIRMADO = "trial_confirmado"
    const val COMPRA_FALLIDA = "compra_fallida"
    const val RESTAURAR_COMPRA_PULSADO = "restaurar_compra_pulsado"
}

/**
 * Un evento del funnel pendiente de subir a Supabase. Se guarda en disco
 * igual que CambioPendiente (ver AlmacenLocal/Sincronizador): si no hay red
 * en ese momento, no se pierde — se reintenta en la siguiente
 * sincronización. Al ser de solo-inserción (nunca se actualiza ni se
 * borra), cada uno lleva su propio idLocal en vez de compartir clave con
 * otro evento del mismo nombre.
 */
@Serializable
data class EventoFunnelPendiente(
    val idLocal: String = UUID.randomUUID().toString(),
    val evento: String,
    val metadata: Map<String, String> = emptyMap(),
    val creadoEn: Fecha = Instant.now()
)

/**
 * Fila tal cual se manda a la tabla eventos_funnel. usuario_id se manda
 * explícito porque la política de RLS de insert lo exige (usuario_id =
 * auth.uid() o null) — no hay forma de que Postgres lo rellene solo aquí.
 */
@Serializable
data class FilaEventoFunnel(
    @SerialName("usuario_id") val usuarioId: String? = null,
    @SerialName("dispositivo_id") val dispositivoId: String,
    val plataforma: String = "android",
    val evento: String,
    val metadata: Map<String, String> = emptyMap(),
    @SerialName("creado_en") val creadoEn: Fecha
)

/**
 * Punto único para registrar eventos del embudo de conversión desde
 * cualquier pantalla: EventosFunnel.registrar(EventoNombre.PAYWALL_MOSTRADO).
 *
 * Nunca lanza ni bloquea a quien llama: solo encola el evento en local
 * (persistido en disco de inmediato) e intenta subirlo enseguida si hay
 * red — mismo patrón robusto que el resto de datos de la app (ver
 * Repositorio y Sincronizador). Si falla o no hay conexión, se reintenta
 * solo en la siguiente sincronización.
 */
object EventosFunnel {
    fun registrar(evento: String, metadata: Map<String, String> = emptyMap()) {
        Repositorio.registrarEventoFunnel(evento, metadata)
    }
}
