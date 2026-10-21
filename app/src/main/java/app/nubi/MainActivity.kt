package app.nubi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nubi.data.Repositorio
import app.nubi.data.Supabase
import app.nubi.ui.Navegacion
import app.nubi.ui.PantallaBienvenida
import app.nubi.ui.PantallaNuevoBebe
import app.nubi.ui.theme.NubiGeometria
import app.nubi.ui.theme.NubiTheme
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Si la app se abrió DESDE CERO con el enlace del email, esto
        // completa el login (Supabase lee el token del propio intent).
        Supabase.client.handleDeeplinks(intent)

        setContent {
            val datos by Repositorio.local.datos.collectAsState()
            val oscuro = when (datos.tema) {
                "claro" -> false
                "oscuro" -> true
                else -> isSystemInDarkTheme()
            }

            NubiTheme(oscuro = oscuro) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PantallaRaiz()
                }
            }
        }
    }

    /**
     * El caso normal: la app ya estaba abierta esperando el enlace, así que
     * Android no pasa por onCreate — entrega el enlace aquí (la Activity es
     * "singleTop" en el manifiesto). Sin esto, el enlace devuelve a la app
     * pero la sesión nunca se completa.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Supabase.client.handleDeeplinks(intent)
    }
}

@Composable
fun PantallaRaiz() {
    val sessionStatus by Supabase.client.auth.sessionStatus.collectAsState()

    when (sessionStatus) {
        is SessionStatus.Authenticated -> ContenidoApp()
        else -> PantallaLogin()
    }
}

/**
 * Qué se ve una vez dentro, según lo que falte por hacer:
 * bienvenida (con el canjeo de código) → alta del bebé → la app.
 */
@Composable
fun ContenidoApp() {
    val datos by Repositorio.local.datos.collectAsState()
    val cargando by Repositorio.local.cargando.collectAsState()
    var bienvenidaVista by remember { mutableStateOf(false) }
    val ambito = rememberCoroutineScope()

    when {
        cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Un momento…", style = MaterialTheme.typography.bodyMedium)
        }

        datos.bebe == null && !bienvenidaVista ->
            PantallaBienvenida(alContinuar = { bienvenidaVista = true })

        datos.bebe == null ->
            PantallaNuevoBebe(alGuardar = { })

        else -> Navegacion(
            alCerrarSesion = {
                ambito.launch { Supabase.client.auth.signOut() }
            }
        )
    }
}

/**
 * Login. Google es el método principal: no depende de servidor de correo
 * propio ni de límites de envío, y vuelve a la app por el mismo enlace
 * app.nubi://login-callback que usa el enlace mágico.
 *
 * El email con enlace mágico se mantiene abajo como alternativa, pero el
 * servicio de correo de serie de Supabase solo envía 2 correos/hora y solo
 * a direcciones del equipo del proyecto, así que no sirve para usuarios
 * reales mientras no haya SMTP propio.
 */
@Composable
fun PantallaLogin() {
    var correo by remember { mutableStateOf("") }
    var enlaceEnviado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val ambito = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NubiGeometria.margen)
            .fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Nubi", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                ambito.launch {
                    try {
                        Supabase.client.auth.signInWith(Google)
                        error = null
                    } catch (e: Exception) {
                        error = "No se pudo entrar con Google: ${e.message}"
                    }
                }
            },
            shape = RoundedCornerShape(NubiGeometria.radioChico),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Entrar con Google")
        }

        Spacer(Modifier.height(32.dp))
        Text(
            "o con tu email",
            style = MaterialTheme.typography.bodySmall,
            color = NubiTheme.colores.tintaTenue
        )
        Spacer(Modifier.height(8.dp))

        if (enlaceEnviado) {
            Text(
                "Te hemos enviado un enlace a $correo. Ábrelo desde el correo en este móvil para entrar.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            OutlinedTextField(
                value = correo,
                onValueChange = { correo = it },
                label = { Text("Tu email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = {
                    ambito.launch {
                        try {
                            Supabase.client.auth.signInWith(OTP) { email = correo.trim() }
                            enlaceEnviado = true
                            error = null
                        } catch (e: Exception) {
                            error = "No se pudo enviar el enlace: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enviarme un enlace")
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
