@file:OptIn(SupabaseInternal::class)

package app.nubi.data

import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.plugins.HttpTimeout

/**
 * Cliente único de Supabase para toda la app — mismo proyecto que usan iOS
 * y la web (ver README, fase 1): la cuenta y el estado de premium/plazo de
 * gracia viven aquí, no en el dispositivo.
 *
 * La clave de abajo es la "publishable key" (equivalente a la antigua
 * "anon key"): está pensada para ir en el cliente y el propio dashboard de
 * Supabase indica que es segura de compartir públicamente. La clave
 * "service_role" es la secreta y esa NUNCA debe ir en la app.
 */
object Supabase {
    private const val URL = "https://iroyjlpicrcjrnjdthsp.supabase.co"
    private const val PUBLISHABLE_KEY = "sb_publishable_FQdOYbx4UTOM62FpesEMcw_y4QG5KUk"

    val client = createSupabaseClient(
        supabaseUrl = URL,
        supabaseKey = PUBLISHABLE_KEY
    ) {
        install(Auth) {
            // Deben coincidir con el intent-filter del AndroidManifest y con
            // la Redirect URL "app.nubi://login-callback" añadida en
            // Supabase → Authentication → URL Configuration.
            scheme = "app.nubi"
            host = "login-callback"
        }
        install(Postgrest)
        // Para verificar-compra-android (ver app.nubi.data.Compras):
        // comprueba las compras de Play Billing contra Google antes de
        // activar premium.
        install(Functions)

        // Sin esto, el cliente HTTP no tiene ningún límite de tiempo: una
        // conexión que se queda a medias (típico en emuladores recién
        // creados, o con mala red) puede dejar colgada para siempre
        // cualquier llamada — incluida Sincronizador.sincronizar(), que
        // ahora bloquea la pantalla principal hasta que termina (ver
        // MainActivity.ContenidoApp() y primeraSincronizacionHecha).
        httpConfig {
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
        }
    }
}
