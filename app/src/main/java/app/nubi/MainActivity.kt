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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.nubi.data.Compartir
import app.nubi.data.EventoNombre
import app.nubi.data.EventosFunnel
import app.nubi.data.Repositorio
import app.nubi.data.Supabase
import app.nubi.i18n.L10n
import app.nubi.ui.Navegacion
import app.nubi.ui.PantallaCargandoCompartido
import app.nubi.ui.PantallaNuevoBebe
import app.nubi.ui.theme.NubiGeometria
import app.nubi.ui.theme.NubiTheme
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Si la app se abrió DESDE CERO con el enlace del email, esto
        // completa el login (Supabase lee el token del propio intent).
        Supabase.client.handleDeeplinks(intent)

        // Si se abrió con un enlace de invitación (nubi://unirme?token=...),
        // esto no hace nada si no lo es — y si lo es, guarda el token para
        // que ContenidoApp ofrezca aceptarlo en cuanto haya sesión.
        Compartir.recibirEnlace(intent.data)
        // Importante: limpiar el data del intent una vez leído. Si no, el
        // mismo token reaparece cada vez que el sistema recrea esta Activity
        // (poca memoria, cambio de configuración, reabrir desde Recientes…)
        // sin pasar por un enlace nuevo de verdad — Android sigue
        // devolviendo este mismo intent en getIntent()/onCreate. Sin este
        // borrado, una invitación ya aceptada hace tiempo vuelve a aparecer
        // y falla con "invitacion_ya_usada" al intentar aceptarla otra vez.
        intent.data = null

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
        Compartir.recibirEnlace(intent.data)
        // setIntent(intent) para que getIntent() quede con ESTE intent (el
        // nuevo, no el de cuando se abrió la Activity por primera vez), y
        // luego se le borra el data por el mismo motivo que en onCreate: que
        // una recreación posterior de la Activity no vuelva a leer este
        // mismo token de invitación.
        setIntent(intent)
        intent.data = null
    }
}

@Composable
fun PantallaRaiz() {
    val sessionStatus by Supabase.client.auth.sessionStatus.collectAsState()

    // Repositorio.iniciar() (NubiApplication) ya intenta sincronizar una vez
    // al arrancar, pero en ese momento normalmente todavía no hay sesión
    // (sincronizar() se cancela al instante si no la hay) y nadie vuelve a
    // pedirlo después del login. Sin este disparador, un móvil nuevo con
    // una cuenta ya existente se queda esperando para siempre la señal de
    // ContenidoApp() (primeraSincronizacionHecha), porque esa sincronización
    // nunca llega a ejecutarse de verdad. sincronizar() ya es seguro de
    // llamar varias veces (se ignora si ya hay una en marcha).
    LaunchedEffect(sessionStatus) {
        if (sessionStatus is SessionStatus.Authenticated) {
            Repositorio.sincronizarEnSegundoPlano()
            // Para enterarse al instante de lo que suba la pareja (o el
            // propio iPhone) sin esperar a volver a abrir la app.
            Repositorio.activarTiempoReal()
        }
    }

    when (sessionStatus) {
        is SessionStatus.Authenticated -> ContenidoApp()
        // Al arrancar, sessionStatus empieza en Initializing mientras
        // Supabase lee de disco la sesión guardada (casi siempre la hay).
        // Antes esto caía en el "else" de abajo y se veía un parpadeo de
        // PantallaLogin durante ese instante, aunque hubiera sesión. Se
        // enseña la misma pantalla de logo+spinner que ya se usa más abajo
        // (ContenidoApp, caso "cargando") hasta que se sepa de verdad si
        // hay sesión o no.
        is SessionStatus.Initializing -> PantallaCargandoSesion()
        else -> PantallaLogin()
    }
}

@Composable
private fun PantallaCargandoSesion() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
    val ambito = rememberCoroutineScope()

    // Invitación por enlace (nubi://unirme?token=...) pendiente de aceptar
    // — ver Compartir.kt. Se ofrece aquí, por encima de cualquier pantalla,
    // para que funcione tanto si ya se tiene bebé como si todavía no.
    val invitacionPendiente by Compartir.invitacionPendiente.collectAsState()
    var aceptandoInvitacion by remember { mutableStateOf(false) }
    var errorInvitacion by remember { mutableStateOf<String?>(null) }
    // Tras aceptar una invitación: pantalla completa de "Preparando el bebé
    // compartido…" hasta que el bebé y sus registros están en el móvil.
    var cargandoCompartido by remember { mutableStateOf(false) }

    // Aviso único de newsletter para usuarios ya existentes (los nuevos ya
    // responden esto en PantallaNuevoBebe, así que newsletter_preguntado les
    // queda en true desde el alta y nunca llegan a ver este diálogo). Se
    // pide solo cuando ya hay bebé y perfil, y no hay una invitación de por
    // medio (esa tiene prioridad y este diálogo puede esperar a la próxima
    // sincronización/apertura de la app).
    val mostrarPreguntaNewsletter = datos.bebe != null &&
        datos.perfil?.newsletterPreguntado == false &&
        invitacionPendiente == null

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

    Box(Modifier.fillMaxSize()) {
    when {
        // Mismo tratamiento que VistaCargandoSesion en iOS, y que
        // PantallaCargandoSesion (arriba, en PantallaRaiz): el logo completo
        // en vez de texto suelto, para no enseñar una pantalla en blanco
        // mientras se comprueba si ya había sesión guardada.
        cargando -> PantallaCargandoSesion()

        // Antes de ofrecer "crear bebé", se espera a que termine el primer
        // intento de sincronizar: en un móvil nuevo con una cuenta YA
        // existente, el archivo local está vacío y se lee casi al
        // instante, pero el bebé real todavía puede estar bajando de
        // Supabase — sin este hueco, se ofrecería crear un bebé duplicado.
        datos.bebe == null && !primeraSincronizacionHecha ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        // Quien llega aquí recién logueado con una invitación pendiente NO
        // debe ver el formulario de alta: antes se enseñaba PantallaNuevoBebe
        // de fondo con el diálogo de "Te han invitado" flotando encima, y si
        // ese diálogo se descartaba (atrás, tocar fuera, o incluso solo el
        // frame que tarda en aparecer) la persona se quedaba mirando un
        // formulario para darse de alta su propio bebé, que no viene a
        // cuento cuando lo que va a hacer es unirse al de otra cuenta. Si
        // decide "Ahora no", invitacionPendiente pasa a null y esta rama
        // deja paso a PantallaNuevoBebe de verdad, como con cualquier cuenta
        // nueva sin invitación.
        datos.bebe == null && invitacionPendiente != null ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        // Sin pantalla de bienvenida/código de regalo intermedia: va directo
        // al alta del bebé, igual que iOS (VistaAlta se abre directamente).
        // Canjear un código sigue disponible desde Ajustes en todo momento.
        datos.bebe == null ->
            PantallaNuevoBebe(alGuardar = { })

        // Ya NO hay muro de pago a la entrada: se deja usar la app con
        // partes gratis y partes premium (ver PantallaHoy, PantallaDia,
        // Navegacion — Premium.activo() es el único sitio que decide qué
        // está desbloqueado). Se sigue esperando a que llegue el perfil
        // antes de mostrar nada: sin él, Premium.activo() no puede saber
        // si la cuenta tiene premium, y un hueco aquí dejaría ver
        // brevemente la app entera desbloqueada en cada arranque.
        datos.perfil == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        else -> Navegacion(
            perfil = datos.perfil,
            alCerrarSesion = {
                Repositorio.desactivarTiempoReal()
                ambito.launch { Supabase.client.auth.signOut() }
            }
        )
    }

    if (cargandoCompartido) {
        PantallaCargandoCompartido(alTerminar = { cargandoCompartido = false })
    }

    if (invitacionPendiente != null) {
        AlertDialog(
            onDismissRequest = { if (!aceptandoInvitacion) Compartir.descartarInvitacionPendiente() },
            title = { Text(L10n.t("Te han invitado a un bebé")) },
            text = {
                Column {
                    Text(L10n.t("Alguien quiere compartir el seguimiento de su bebé contigo. Vas a ver y poder anotar sus datos."))
                    errorInvitacion?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !aceptandoInvitacion,
                    onClick = {
                        val token = invitacionPendiente ?: return@TextButton
                        ambito.launch {
                            aceptandoInvitacion = true
                            errorInvitacion = null
                            try {
                                Compartir.aceptarInvitacion(token)
                                Compartir.descartarInvitacionPendiente()
                                cargandoCompartido = true
                            } catch (e: Exception) {
                                val mensaje = e.message ?: ""
                                when {
                                    // Ya se aceptó antes (típicamente: el enlace
                                    // reaparece solo al recrearse la Activity,
                                    // sin que el usuario haya tocado nada nuevo).
                                    // El acceso ya existe, así que no es un error
                                    // de verdad: se cierra sin más.
                                    "invitacion_ya_usada" in mensaje -> {
                                        Compartir.descartarInvitacionPendiente()
                                        cargandoCompartido = true
                                    }
                                    "invitacion_caducada" in mensaje ->
                                        errorInvitacion = L10n.t("Esta invitación ya ha caducado. Pide un enlace nuevo.")
                                    "no_puedes_aceptar_tu_propia_invitacion" in mensaje ->
                                        errorInvitacion = L10n.t("No puedes aceptar tu propia invitación.")
                                    "invitacion_invalida" in mensaje ->
                                        errorInvitacion = L10n.t("Este enlace de invitación ya no es válido.")
                                    else ->
                                        // Error inesperado (red, servidor, etc.): aquí
                                        // sí interesa el detalle técnico para depurar.
                                        errorInvitacion = "${L10n.t("No se pudo aceptar la invitación:")} $mensaje"
                                }
                            } finally {
                                aceptandoInvitacion = false
                            }
                        }
                    }
                ) { Text(if (aceptandoInvitacion) L10n.t("Un momento…") else L10n.t("Unirme")) }
            },
            dismissButton = {
                TextButton(
                    enabled = !aceptandoInvitacion,
                    onClick = { Compartir.descartarInvitacionPendiente() }
                ) { Text(L10n.t("Ahora no")) }
            }
        )
    }

    if (mostrarPreguntaNewsletter) {
        AlertDialog(
            // Sin onDismissRequest con efecto: descartar (tocar fuera, atrás)
            // cuenta como "no, gracias" — igual que cerrar cualquier otro
            // aviso de permiso opcional, y así no insiste en cada apertura.
            onDismissRequest = { Repositorio.actualizarNewsletter(false) },
            title = { Text(L10n.t("¿Quieres recibir nuestra newsletter?")) },
            text = {
                Text(L10n.t("Te avisamos por email de ofertas como el Black Friday, consejos y novedades de Nubi. Puedes darte de baja cuando quieras desde Ajustes."))
            },
            confirmButton = {
                TextButton(onClick = { Repositorio.actualizarNewsletter(true) }) {
                    Text(L10n.t("Sí, quiero recibirla"))
                }
            },
            dismissButton = {
                TextButton(onClick = { Repositorio.actualizarNewsletter(false) }) {
                    Text(L10n.t("No, gracias"))
                }
            }
        )
    }
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
 * Login. Google es la única forma de entrar: no depende de servidor de
 * correo propio ni de límites de envío, y vuelve a la app por el mismo
 * enlace app.nubi://login-callback que usaba antes el enlace mágico por
 * email (ya retirado de esta pantalla).
 */
@Composable
fun PantallaLogin() {
    var error by remember { mutableStateOf<String?>(null) }
    var entrandoConGoogle by remember { mutableStateOf(false) }
    // Se marca true justo cuando signInWith(Google) termina bien, para que
    // el DisposableEffect de abajo sepa distinguir "salió tras completar el
    // login" de "salió sin completarlo" (auth_abandoned) cuando esta
    // pantalla desaparece — pasa a ContenidoApp en cuanto sessionStatus
    // cambia a Authenticated (ver PantallaRaiz).
    var huboExito by remember { mutableStateOf(false) }
    // Evita registrar auth_abandoned dos veces para el mismo intento: una
    // vez por el aviso de 60s sin avanzar, y otra vez al salir de la
    // pantalla justo después.
    var yaRegistradoAbandono by remember { mutableStateOf(false) }
    val ambito = rememberCoroutineScope()
    // Si llegó un enlace de invitación antes de iniciar sesión (p. ej. el
    // destinatario no tenía la cuenta abierta todavía), Compartir.recibirEnlace
    // ya guardó el token — pero el diálogo para aceptarlo solo se muestra
    // dentro de ContenidoApp(), que no se monta sin sesión. Sin este aviso,
    // la persona ve una pantalla de login genérica y no entiende por qué
    // está aquí ni qué hacer a continuación.
    val invitacionPendiente by Compartir.invitacionPendiente.collectAsState()

    LaunchedEffect(Unit) {
        EventosFunnel.registrar(EventoNombre.AUTH_SCREEN_VIEWED)
    }
    // LaunchedEffect se cancela solo en cuanto este composable sale de
    // composición (login completado o se cierra la pantalla), así que no
    // hace falta cancelar el delay a mano.
    LaunchedEffect(Unit) {
        delay(60_000)
        if (!huboExito && !yaRegistradoAbandono && !entrandoConGoogle) {
            yaRegistradoAbandono = true
            EventosFunnel.registrar(EventoNombre.AUTH_ABANDONED, mapOf("motivo" to "inactividad_60s"))
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (!huboExito && !yaRegistradoAbandono) {
                EventosFunnel.registrar(EventoNombre.AUTH_ABANDONED, mapOf("motivo" to "salio_de_la_pantalla"))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Pantalla fuera de Navegacion (todavía no hay sesión), así que
            // nadie más le aparta la barra de estado ni la de navegación del
            // sistema — sin esto, el logo/texto legal pueden quedar debajo.
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(NubiGeometria.margen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icono solo (ya sin fondo blanco) + "Nubi" como texto aparte, en la
        // tipografía de titulares — igual que VistaLogin en iOS — en vez del
        // logo-completo.png de antes, cuyo "NUBI" iba en otra fuente dibujada.
        Image(
            painter = painterResource(R.drawable.nubi_logo_mark),
            contentDescription = null,
            modifier = Modifier.size(96.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Nubi",
            style = MaterialTheme.typography.headlineSmall,
            color = NubiTheme.colores.tinta
        )

        Spacer(Modifier.height(24.dp))

        Text(
            L10n.t("Empieza a entender mejor el día de tu bebé."),
            style = MaterialTheme.typography.headlineMedium,
            color = NubiTheme.colores.tinta,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            L10n.t("Sueño, siestas, tomas y pañales en un solo lugar."),
            style = MaterialTheme.typography.bodyMedium,
            color = NubiTheme.colores.tintaSuave,
            textAlign = TextAlign.Center
        )

        if (invitacionPendiente != null) {
            Spacer(Modifier.height(20.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = NubiTheme.colores.lila.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(NubiGeometria.radio),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        L10n.t("Te han invitado a un bebé"),
                        style = MaterialTheme.typography.labelLarge,
                        color = NubiTheme.colores.tinta
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        L10n.t("Inicia sesión para unirte y empezar a ver sus datos."),
                        style = MaterialTheme.typography.bodySmall,
                        color = NubiTheme.colores.tintaSuave
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        BotonGoogle(
            entrando = entrandoConGoogle,
            onClick = {
                ambito.launch {
                    entrandoConGoogle = true
                    EventosFunnel.registrar(EventoNombre.GOOGLE_TAPPED)
                    try {
                        // Sin "hl" la pantalla de consentimiento de Google se
                        // abre en el idioma del navegador/sistema, no en el
                        // elegido dentro de Nubi — es la pantalla del propio
                        // Google, no texto nuestro.
                        Supabase.client.auth.signInWith(Google) {
                            queryParams["hl"] = L10n.idiomaEfectivo.rawValue
                        }
                        error = null
                        huboExito = true
                        EventosFunnel.registrar(EventoNombre.AUTH_COMPLETED)
                    } catch (e: Exception) {
                        error = "${L10n.t("No se pudo entrar con Google:")} ${e.message}"
                        EventosFunnel.registrar(
                            EventoNombre.AUTH_FAILED,
                            mapOf("proveedor" to "google", "motivo" to (e.message ?: "desconocido"))
                        )
                    }
                    entrandoConGoogle = false
                }
            }
        )

        Spacer(Modifier.height(16.dp))
        Text(
            L10n.t("¿Ya usabas Nubi? Recuperaremos tus datos al continuar."),
            style = MaterialTheme.typography.bodySmall,
            color = NubiTheme.colores.tintaSuave,
            textAlign = TextAlign.Center
        )

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
        val colorTenue = NubiTheme.colores.tintaTenue
        val colorEnlace = NubiTheme.colores.tintaSuave
        val textoLegal = buildAnnotatedString {
            withStyle(SpanStyle(color = colorTenue)) {
                append(L10n.t("Al continuar, aceptas los"))
                append(" ")
            }
            withLink(
                LinkAnnotation.Url(
                    "https://appnubi.netlify.app/terminos",
                    TextLinkStyles(style = SpanStyle(color = colorEnlace, textDecoration = TextDecoration.Underline))
                )
            ) { append(L10n.t("Términos de uso")) }
            withStyle(SpanStyle(color = colorTenue)) {
                append(" ")
                append(L10n.t("y la"))
                append(" ")
            }
            withLink(
                LinkAnnotation.Url(
                    "https://appnubi.netlify.app/privacidad",
                    TextLinkStyles(style = SpanStyle(color = colorEnlace, textDecoration = TextDecoration.Underline))
                )
            ) { append(L10n.t("Política de privacidad")) }
            withStyle(SpanStyle(color = colorTenue)) { append(".") }
        }
        Text(
            textoLegal,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
