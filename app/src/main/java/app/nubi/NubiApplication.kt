package app.nubi

import android.app.Application

/**
 * Punto de entrada de la app. Aquí se inicializará el cliente de Supabase
 * en la fase de backend (cuenta + estado de suscripción/plazo de gracia,
 * compartido con iOS y la web — ver README, fase 1).
 */
class NubiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
