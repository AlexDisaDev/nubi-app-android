package app.nubi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.nubi.data.Repositorio
import app.nubi.data.Supabase
import app.nubi.i18n.L10n
import app.nubi.ui.Navegacion
import app.nubi.ui.PantallaBienvenida
import app.nubi.ui.PantallaNuevoBebe
import app.nubi.ui.PantallaPaywall
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
    val primeraSincronizacionHecha by Repositorio.primeraSincronizacionHecha.collectAsState()
    var bienvenidaVista by remember { mutableStateOf(false) }
    val ambito = rememberCoroutineScope()

    // Permiso de notificaciones (obligatorio desde Android 13): se pide una
    // vez al entrar, para que los avisos de medicina y citas puedan sonar.
    // Antes de Android 13 las apps ya tienen el permiso concedido de serie.
    val contexto = LocalContext.current
    val lanzadorPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* se conceda o no, no hay nada más que hacer aquí */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            lanzadorPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    when {
        // Mismo tratamiento que VistaCargandoSesion en iOS: el logo completo
        // en vez de texto suelto, para no enseñar una pantalla en blanco
        // mientras se comprueba si ya había sesión guardada.
        cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.nubi_logo_splash),
                    contentDescription = null,
                    modifier = Modifier.width(160.dp)
                )
                Spacer(Modifier.height(20.dp))
                CircularProgressIndicator()
            }
        }

        // Antes de ofrecer "crear bebé", se espera a que termine el primer
        // intento de sincronizar: en un móvil nuevo con una cuenta YA
        // existente, el archivo local está vacío y se lee casi al
        // instante, pero el bebé real todavía puede estar bajando de
        // Supabase — sin este hueco, se ofrecería crear un bebé duplicado.
        datos.bebe == null && !primeraSincronizacionHecha ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        datos.bebe == null && !bienvenidaVista ->
            PantallaBienvenida(alContinuar = { bienvenidaVista = true })

        datos.bebe == null ->
            PantallaNuevoBebe(alGuardar = { })

        // Muro de pago duro, tal cual como iOS: hace falta una
        // suscripción vigente (o un código de regalo canjeado) para
        // entrar, no solo estar dentro de fechaCortePrueba — ver
        // Perfil.tieneAccesoPagado() en Modelos.kt para el porqué no es
        // lo mismo que puedeUsarApp(). Mientras el perfil no ha llegado
        // todavía de Supabase (datos.perfil == null, p. ej. justo tras
        // iniciar sesión en un móvil nuevo), se espera en vez de dejar
        // pasar: un hueco aquí sería la manera más fácil de saltarse el
        // muro de pago.
        datos.perfil == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        datos.perfil?.tieneAccesoPagado() != true -> PantallaPaywall()

        else -> Navegacion(
            alCerrarSesion = {
                ambito.launch { Supabase.client.auth.signOut() }
            }
        )
    }
}

/**
 * Botón "Continuar con Google" con el logotipo oficial de Google (las
 * cuatro franjas de color), siguiendo sus propias guías de marca para
 * botones de inicio de sesión: fondo claro, borde sutil, texto oscuro.
 */
@Composable
private fun BotonGoogle(entrando: Boolean, onClick: () -> Unit) {
    val colores = NubiTheme.colores
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(colores.superficie)
            .border(1.dp, colores.separador, RoundedCornerShape(50))
            .clickable(enabled = !entrando, onClick = onClick)
            .padding(vertical = 13.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_google_logo),
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            if (entrando) L10n.t("Un momento…") else L10n.t("Continuar con Google"),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = colores.tinta
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
    var entrandoConGoogle by remember { mutableStateOf(false) }
    val ambito = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NubiGeometria.margen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Mismo logo y mismo sitio que en VistaLogin de iOS: encabezando la
        // pantalla de login, sin tocar ninguna otra imagen de la app.
        Image(
            painter = painterResource(R.drawable.nubi_logo_splash),
            contentDescription = null,
            modifier = Modifier.width(140.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            L10n.t("Inicia sesión para continuar"),
            style = MaterialTheme.typography.bodyMedium,
            color = NubiTheme.colores.tintaSuave
        )
        Spacer(Modifier.height(28.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = NubiTheme.colores.superficie),
            shape = RoundedCornerShape(NubiGeometria.radio),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                L10n.t("¿Ya eras usuario de Nubi? Tranquilo: tus datos no se van a borrar, siguen guardados tal y como estaban."),
                style = MaterialTheme.typography.bodySmall,
                color = NubiTheme.colores.tintaSuave,
                modifier = Modifier.padding(14.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        BotonGoogle(
            entrando = entrandoConGoogle,
            onClick = {
                ambito.launch {
                    entrandoConGoogle = true
                    try {
                        Supabase.client.auth.signInWith(Google)
                        error = null
                    } catch (e: Exception) {
                        error = "${L10n.t("No se pudo entrar con Google:")} ${e.message}"
                    }
                    entrandoConGoogle = false
                }
            }
        )

        Spacer(Modifier.height(28.dp))
        Text(
            L10n.t("o con tu email"),
            style = MaterialTheme.typography.bodySmall,
            color = NubiTheme.colores.tintaTenue
        )
        Spacer(Modifier.height(8.dp))

        if (enlaceEnviado) {
            Text(
                "${L10n.t("Te hemos enviado un enlace a")} $correo. ${L10n.t("Ábrelo desde el correo en este móvil para entrar.")}",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        } else {
            OutlinedTextField(
                value = correo,
                onValueChange = { correo = it },
                label = { Text(L10n.t("Tu email")) },
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
                            error = "${L10n.t("No se pudo enviar el enlace:")} ${e.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(L10n.t("Enviarme un enlace"))
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(28.dp))
        Text(
            L10n.t("Al continuar aceptas nuestros términos y la política de privacidad de Nubi."),
            style = MaterialTheme.typography.bodySmall,
            color = NubiTheme.colores.tintaTenue,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
