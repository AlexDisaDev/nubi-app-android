package app.nubi

import android.app.Application

/**
 * Punto de entrada de la app. El cliente de Supabase (cuenta + estado de
 * suscripción/plazo de gracia, compartido con iOS y la web) vive en
 * app.nubi.data.Supabase — ver README, fase 1.
 */
class NubiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
