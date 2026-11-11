package app.nubi.data

import app.nubi.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Punto único de verdad de "¿esta cuenta tiene premium ahora mismo?" para
 * todo el modo freemium (PantallaHoy, PantallaDia, Navegacion, Ajustes).
 *
 * Ninguna pantalla debe llamar a Perfil.tieneAccesoPagado() directamente:
 * siempre a través de [Premium.activo], para que el interruptor de prueba
 * de abajo afecte a todos los sitios bloqueados a la vez, sin tocarlos uno
 * a uno, y para que el día de mañana cambiar la condición de desbloqueo
 * (código de regalo, otra fuente) solo haga falta tocar este archivo.
 */
object Premium {

    /**
     * Interruptor SOLO para builds de depuración (fila en PantallaAjustes,
     * visible solo si [disponibleEnPruebas]): simula tener premium sin
     * pasar por Play Billing ni configurar un comprador de prueba en Play
     * Console, para poder ver el desbloqueo completo en Android Studio.
     *
     * En una build de release, BuildConfig.DEBUG es `false` en tiempo de
     * compilación, así que [activo] ignora este interruptor por completo
     * ahí — no hay forma de que un usuario real lo active.
     */
    private val _forzadoEnPruebas = MutableStateFlow(false)
    val forzadoEnPruebas: StateFlow<Boolean> = _forzadoEnPruebas.asStateFlow()

    /** Si la fila de "simular premium" debe aparecer en Ajustes. */
    val disponibleEnPruebas: Boolean = BuildConfig.DEBUG

    fun alternarPruebas() {
        if (BuildConfig.DEBUG) _forzadoEnPruebas.value = !_forzadoEnPruebas.value
    }

    /**
     * La única pregunta que debe hacerse la UI para decidir si algo premium
     * se desbloquea.
     *
     * [premiumCompartido] es DatosNubi.premiumCompartido (ver Compartir.kt
     * y Sincronizador.bajarCambios()): true si el dueño del bebé actual
     * —sea este móvil u otro, iPhone o Android— tiene premium activo
     * ahora mismo. Es el equivalente de Sincronizador.esParticipante en
     * iOS, pero mirando el pago real en vez de solo "soy participante".
     */
    fun activo(perfil: Perfil?, premiumCompartido: Boolean = false): Boolean =
        (BuildConfig.DEBUG && _forzadoEnPruebas.value) ||
            perfil?.tieneAccesoPagado() == true ||
            premiumCompartido
}
