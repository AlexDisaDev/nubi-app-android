package app.nubi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nubi.data.Supabase
import app.nubi.ui.theme.NubiTheme
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Si la app se abrió desde el enlace mágico del email, esto
        // completa el login (Supabase lee el token del propio intent).
        Supabase.client.handleDeeplinks(intent)

        setContent {
            NubiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PantallaRaiz()
                }
            }
        }
    }
}

@Composable
fun PantallaRaiz() {
    val sessionStatus by Supabase.client.auth.sessionStatus.collectAsState()

    when (sessionStatus) {
        is SessionStatus.Authenticated -> PantallaPrincipal()
        else -> PantallaLogin()
    }
}

/**
 * Login por email con enlace mágico (sin contraseña) — ver README, fase 1.
 */
@Composable
fun PantallaLogin() {
    var correo by remember { mutableStateOf("") }
    var enlaceEnviado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Nubi", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))

            if (enlaceEnviado) {
                Text("Te hemos enviado un enlace a $correo. Ábrelo desde el correo en este móvil para entrar.")
            } else {
                OutlinedTextField(
                    value = correo,
                    onValueChange = { correo = it },
                    label = { Text("Tu email") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                Supabase.client.auth.signInWith(OTP) {
                                    email = correo
                                }
                                enlaceEnviado = true
                                error = null
                            } catch (e: Exception) {
                                error = "No se pudo enviar el enlace: ${e.message}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enviarme un enlace para entrar")
                }
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * Pantalla provisional del esqueleto, ahora con la comprobación real de
 * acceso contra Supabase y un cuadro para probar el canjeo de códigos de
 * regalo de extremo a extremo. Se sustituye por la navegación real (Día,
 * Alimentación, Crecimiento, Salud, Ajustes…) en la fase 3.
 */
@Composable
fun PantallaPrincipal() {
    var puedeUsar by remember { mutableStateOf<Boolean?>(null) }
    var codigo by remember { mutableStateOf("") }
    var mensajeCanjeo by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun comprobarAcceso() {
        puedeUsar = try {
            Supabase.client.postgrest.rpc("puede_registrar").decodeAs<Boolean>()
        } catch (e: Exception) {
            null
        }
    }

    LaunchedEffect(Unit) { comprobarAcceso() }

    Scaffold { padding ->
        Column(modifier = Modifier.padding(padding).padding(24.dp)) {
            Text("Nubi Android — esqueleto del proyecto")
            Spacer(Modifier.height(12.dp))
            Text(
                when (puedeUsar) {
                    true -> "Acceso activo (prueba o premium)."
                    false -> "Plazo de prueba terminado — hace falta premium."
                    null -> "Comprobando acceso…"
                }
            )

            Spacer(Modifier.height(24.dp))
            Text("Canjear código de regalo (prueba)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = codigo,
                onValueChange = { codigo = it },
                label = { Text("Código") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        mensajeCanjeo = try {
                            // Los parámetros de un rpc van como JsonObject,
                            // no como data class (API de postgrest-kt).
                            Supabase.client.postgrest.rpc(
                                "canjear_codigo_regalo",
                                buildJsonObject { put("p_codigo", codigo) }
                            )
                            comprobarAcceso()
                            "Código canjeado correctamente."
                        } catch (e: Exception) {
                            "Error al canjear: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Canjear")
            }
            mensajeCanjeo?.let {
                Spacer(Modifier.height(8.dp))
                Text(it)
            }
        }
    }
}
