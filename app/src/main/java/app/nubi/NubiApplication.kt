package app.nubi

import android.app.Application
import app.nubi.data.Compras
import app.nubi.data.Repositorio
import app.nubi.data.ResenasAppStore

/**
 * Punto de entrada de la app. El cliente de Supabase (cuenta + estado de
 * suscripción/plazo de gracia, compartido con iOS y la web) vive en
 * app.nubi.data.Supabase — ver README, fase 1.
 *
 * Aquí se arranca el repositorio: carga el nubi.json del móvil y lanza el
 * primer intento de sincronizar. También se arranca la conexión con Google
 * Play Billing (ver app.nubi.data.Compras) para que esté lista en cuanto
 * haya una pantalla de suscripción que la use.
 */
class NubiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Repositorio.iniciar(this)
        Compras.iniciar(this)
        // Solo deja listo el contexto y lee la caché; la descarga del feed
        // de reseñas se dispara al abrir el paywall, no al arrancar.
        ResenasAppStore.iniciar(this)
    }
}
