package app.nubi

import android.app.Application
import app.nubi.data.Repositorio

/**
 * Punto de entrada de la app. El cliente de Supabase (cuenta + estado de
 * suscripción/plazo de gracia, compartido con iOS y la web) vive en
 * app.nubi.data.Supabase — ver README, fase 1.
 *
 * Aquí se arranca el repositorio: carga el nubi.json del móvil y lanza el
 * primer intento de sincronizar.
 */
class NubiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Repositorio.iniciar(this)
    }
}
