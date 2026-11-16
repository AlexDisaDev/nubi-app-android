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
    // Paywall (pedidos por marketing, nov 2026). Sustituyen a los antiguos
    // paywall_mostrado / plan_seleccionado / trial_iniciado /
    // trial_confirmado / compra_fallida / restaurar_compra_pulsado, que
    // siguen en la tabla como histórico pero ya no se registran.
    const val PAYWALL_VIEWED = "paywall_viewed"           // se muestra el paywall
    const val PAYWALL_CLOSED = "paywall_closed"           // pulsa la X
    const val PLAN_SELECTED = "plan_selected"             // elige plan (metadata plan_id)
    const val PAYWALL_CTA_TAPPED = "paywall_cta_tapped"   // pulsa Suscribirme / Probar gratis
    const val PURCHASE_STARTED = "purchase_started"       // se abre la hoja de compra de Play
    const val PURCHASE_COMPLETED = "purchase_completed"   // el servidor confirma la compra con Google
    const val PURCHASE_CANCELLED = "purchase_cancelled"   // cierra la hoja de compra
    const val PURCHASE_FAILED = "purchase_failed"         // error real (metadata motivo)
    const val RESTORE_TAPPED = "restore_tapped"           // pulsa Restaurar
    const val RESTORE_COMPLETED = "restore_completed"     // se recupera una suscripción

    // Embudo de la pantalla de acceso (PantallaLogin). Android solo tiene
    // botón de Google (no de Apple), así que aquí nunca se registra un
    // equivalente a "apple_tapped" — ese nombre solo lo usa iOS, pero sigue
    // siendo el mismo contrato de tabla (ver EventoNombre.swift).
    const val AUTH_SCREEN_VIEWED = "auth_screen_viewed"
    const val GOOGLE_TAPPED = "google_tapped"
    const val AUTH_COMPLETED = "auth_completed"
    const val AUTH_FAILED = "auth_failed"
    const val AUTH_ABANDONED = "auth_abandoned"
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
    val creadoEn: Fecha = Instant.now(),
    /**
     * Veces que este evento concreto ha fallado al subir cuando otros del
     * mismo lote sí subieron (ver Sincronizador.subirEventosFunnelPendientes).
     * Un fallo general —sin red, servidor caído— no cuenta aquí: en ese caso
     * no falla el evento, falla la conexión, y descartarlo sería perder un
     * dato bueno. Solo se cuenta cuando el evento falla él solo, que es la
     * señal de que trae algo mal y nunca va a entrar.
     */
    val intentos: Int = 0
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
 * cualquier pantalla: EventosFunnel.registrar(EventoNombre.PAYWALL_VIEWED).
 *
 * Nunca lanza ni bloquea a quien llama: solo encola el evento en local
 * (persistido en disco de inmediato) e intenta subirlo enseguida si hay
 * red — mismo patrón robusto que el resto de datos de la app (ver
 * Repositorio y Sincronizador). Si falla o no hay conexión, se reintenta
 * solo en la siguiente sincronización.
 */
object EventosFunnel {
    fun registrar(evento: String, metadata: Map<String, String> = emptyMap()) {
        // Contexto común a todos los eventos; lo que pase quien llama manda.
        Repositorio.registrarEventoFunnel(evento, contextoComun() + metadata)
    }

    /**
     * País de la cuenta de Google Play (ISO de 2 letras, "ES", "US"). Lo
     * rellena Compras al conectar con Play; hasta entonces, la región del
     * móvil. @Volatile porque se escribe desde el hilo de Billing.
     */
    @Volatile var paisTienda: String? = null

    private val versionApp: String = "${app.nubi.BuildConfig.VERSION_NAME} (${app.nubi.BuildConfig.VERSION_CODE})"

    private fun contextoComun(): Map<String, String> {
        val pais = paisTienda ?: java.util.Locale.getDefault().country.takeIf { it.isNotBlank() }
        return if (pais != null) mapOf("version_app" to versionApp, "pais" to pais)
        else mapOf("version_app" to versionApp)
    }
}

/**
 * Botón bloqueado que acaba de abrir el paywall ("hoy_prediccion",
 * "linea_dias_anteriores"…). Quien abre el paywall lo marca justo antes y
 * el paywall lo recoge al abrirse; sin marca reciente se usa la pantalla.
 */
object OrigenPaywall {
    @Volatile private var ultimo: String? = null
    @Volatile private var en: Long = 0

    fun marcar(origen: String) {
        ultimo = origen
        en = System.currentTimeMillis()
    }

    /** La marca si es de hace menos de 5 s (y la borra), o porDefecto. */
    fun tomar(porDefecto: String): String {
        val o = ultimo
        val reciente = o != null && System.currentTimeMillis() - en < 5_000
        ultimo = null
        return if (reciente) o!! else porDefecto
    }
}

/**
 * Lo que se sabe del paywall abierto ahora mismo, para añadirlo a sus
 * eventos: desde dónde se abrió y cuándo. Solo hay uno abierto a la vez.
 */
object SesionPaywall {
    @Volatile var origen: String = "desconocido"
    @Volatile var abiertoEn: Long = System.currentTimeMillis()

    fun abrir(origen: String) {
        this.origen = origen
        abiertoEn = System.currentTimeMillis()
    }

    /** origen + segundos desde que se abrió, más lo que se pase. */
    fun meta(extra: Map<String, String> = emptyMap()): Map<String, String> =
        mapOf(
            "origen" to origen,
            "segundos" to ((System.currentTimeMillis() - abiertoEn) / 1000).toString()
        ) + extra
}
