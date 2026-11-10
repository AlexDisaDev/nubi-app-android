package app.nubi.notificaciones

import android.content.Context
import app.nubi.data.Compras
import app.nubi.i18n.L10n
import java.time.Instant
import java.time.ZoneId

/**
 * Aviso de "te quedan 3 días de prueba" — equivalente Android de
 * NotificadorPrueba.swift en iOS. Mismo copy, mismo principio: nunca
 * menciona cancelar, solo anima a quedarse en Nubi Premium (así lo pidió
 * el usuario explícitamente tras el primer borrador en iOS).
 *
 * iOS saca "cuándo acaba mi prueba" en vivo de StoreKit
 * (Transaction.currentEntitlements). Android no tiene ese dato por
 * usuario en Billing — ProductDetails es solo catálogo, no dice nada de
 * la compra de nadie — así que Compras.kt guarda esa fecha en el momento
 * en que detecta una compra con oferta de prueba gratis
 * (Compras.vencePruebaAndroid()). Esto NUNCA decide acceso: eso lo sigue
 * diciendo premium_hasta del servidor, como siempre.
 */
object NotificadorPrueba {
    private const val ID = "prueba_vence"
    private const val ID_DEBUG = "debug_prueba_preview"
    private const val DIAS_AVISO = 3L
    private const val HORA_AVISO = 10

    /**
     * Reprograma el aviso a partir de lo que haya guardado
     * Compras.vencePruebaAndroid() ahora mismo. Se llama desde
     * Repositorio.reprogramarAvisos() — igual que los avisos de
     * medicina/citas/sueño — así que se recalcula solo al arrancar la app,
     * tras reiniciar el móvil y justo después de procesar una compra.
     */
    fun reprogramar(contexto: Context) {
        val venceEl = Compras.vencePruebaAndroid()
        if (venceEl == null) {
            AvisosNubi.cancelar(contexto, ID)
            return
        }
        val zona = ZoneId.systemDefault()
        val dispara = venceEl.atZone(zona).toLocalDate()
            .minusDays(DIAS_AVISO)
            .atTime(HORA_AVISO, 0)
            .atZone(zona)
            .toInstant()
        AvisosNubi.programar(contexto, ID, dispara, titulo(), texto())
    }

    private fun titulo() = L10n.t("Te quedan 3 días de prueba")

    private fun texto() = L10n.t(
        "Sigue anticipándote a cada siesta de tu bebé sin cortes: no pierdas lo que has avanzado con Nubi Premium."
    )

    /** Para el botón "[Debug] Probar aviso de prueba" en Ajustes: dispara
     *  el aviso ya mismo con un id propio, para no pisar ni cancelar el
     *  aviso real programado con ID. */
    fun probarAhora(contexto: Context) {
        AvisosNubi.programar(contexto, ID_DEBUG, Instant.now().plusSeconds(5), titulo(), texto())
    }
}
