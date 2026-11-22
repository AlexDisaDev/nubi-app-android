package app.nubi.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nubi.data.Compartir
import app.nubi.data.Repositorio
import app.nubi.i18n.L10n
import app.nubi.ui.theme.NubiGeometria
import app.nubi.ui.theme.NubiTheme
import kotlinx.coroutines.launch

/**
 * Tarjeta de Ajustes para invitar a la pareja a ver y anotar el bebé —
 * puerto de TarjetaCompartir (HojaCompartirNubi.swift), pero por Supabase
 * en vez de CKShare (ver Compartir.kt y backend/016_compartir_bebe.sql):
 * de momento Android <-> Android, pensado para extenderse después a
 * iPhone <-> Android sin cambiar este esquema.
 */
@Composable
fun TarjetaCompartir(
    bebeId: String,
    nombreBebe: String,
    /** false: sin la Card alrededor, para ir dentro de la tarjeta "Bebé" de Ajustes. */
    conTarjeta: Boolean = true
) {
    val colores = NubiTheme.colores
    val ambito = rememberCoroutineScope()
    var cargando by remember { mutableStateOf(true) }
    var soyInvitado by remember { mutableStateOf(false) }
    var personasInvitadas by remember { mutableStateOf(0) }
    var mostrarHoja by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf(0) }

    LaunchedEffect(bebeId, version) {
        cargando = true
        soyInvitado = Compartir.soyInvitadoDe(bebeId)
        personasInvitadas = if (soyInvitado) 0 else Compartir.contarInvitados(bebeId)
        cargando = false
    }

    val subtitulo = when {
        soyInvitado -> "${L10n.t("Estás viendo y anotando los datos compartidos de")} $nombreBebe."
        else -> L10n.t("Crea un enlace con la marca Nubi y compártelo por WhatsApp, Mail o donde quieras")
    }

    val contenido: @Composable () -> Unit = {
        Column(if (conTarjeta) Modifier.padding(16.dp) else Modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BadgeIcono(
                    IconoNubi.CORAZON,
                    colorFondo = colores.rosa,
                    colorIcono = colores.tinta,
                    tamano = 34.dp,
                    tamanoIcono = 16.dp
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        L10n.t("Compartir con tu pareja"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = colores.tintaSuave)
                }
                if (cargando) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (soyInvitado) {
                TextButton(onClick = {
                    ambito.launch {
                        Compartir.dejarDeVerCompartido(bebeId)
                        Repositorio.salirDeCompartido()
                        version++
                    }
                }) {
                    Text(L10n.t("Dejar de ver datos compartidos"), color = colores.melocoton)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(NubiGeometria.radioChico))
                        .background(colores.lienzoAlto)
                        .clickable { mostrarHoja = true }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            L10n.t("Invitar a mi pareja"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (personasInvitadas > 0) {
                            Text(
                                textoPersonas(personasInvitadas, invitado = false),
                                style = MaterialTheme.typography.bodySmall,
                                color = colores.tintaTenue
                            )
                        }
                    }
                    Icono(IconoNubi.CHEVRON, tint = colores.tintaTenue, tamano = 14.dp)
                }
            }
        }
    }

    if (conTarjeta) {
        Card(
            shape = RoundedCornerShape(NubiGeometria.radioChico),
            colors = CardDefaults.cardColors(containerColor = colores.superficie),
            modifier = Modifier.fillMaxWidth()
        ) { contenido() }
    } else {
        contenido()
    }

    if (mostrarHoja) {
        HojaCompartir(bebeId = bebeId, nombreBebe = nombreBebe) {
            mostrarHoja = false
            version++
        }
    }
}

/** "1 persona invitada" / "N personas invitadas" (o "tiene(n) acceso" dentro de la hoja). */
private fun textoPersonas(n: Int, invitado: Boolean): String =
    if (n == 1) {
        if (invitado) "1 ${L10n.t("persona tiene acceso")}" else "1 ${L10n.t("persona invitada")}"
    } else {
        if (invitado) "$n ${L10n.t("personas tienen acceso")}" else "$n ${L10n.t("personas invitadas")}"
    }

/**
 * Hoja de invitación — puerto de HojaCompartirNubi.swift. Crea el enlace
 * con crear_invitacion_bebe (Compartir.kt) y construye la misma URL bonita
 * con marca Nubi que usa iOS (appnubi.netlify.app/invitar), solo que con
 * "token" en vez de "u": invitar.html ya sabe leer cualquiera de los dos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaCompartir(bebeId: String, nombreBebe: String, alCerrar: () -> Unit) {
    val colores = NubiTheme.colores
    val contexto = LocalContext.current
    val ambito = rememberCoroutineScope()
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // El nombre se recuerda de la última invitación: así, normalmente,
    // invitar es abrir la hoja y pulsar "Compartir enlace".
    val preferencias = remember { contexto.getSharedPreferences("nubi_compartir", Context.MODE_PRIVATE) }
    var nombre by remember { mutableStateOf(preferencias.getString("nombre", "") ?: "") }
    var creando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var personasInvitadas by remember { mutableStateOf(0) }

    LaunchedEffect(bebeId) {
        personasInvitadas = Compartir.contarInvitados(bebeId)
    }

    ModalBottomSheet(onDismissRequest = alCerrar, sheetState = estadoHoja) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NubiGeometria.margen)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    L10n.t("Invitar a tu pareja"),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = alCerrar) { Text(L10n.t("Listo")) }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                L10n.t("Crea un enlace con la marca Nubi y compártelo por WhatsApp, Mail, Messages… por donde quieras."),
                style = MaterialTheme.typography.bodySmall,
                color = colores.tintaSuave
            )
            Spacer(Modifier.height(16.dp))

            // Un solo botón: cada vez que se pulsa crea un enlace nuevo (sirve
            // para una sola persona y caduca a los 7 días si nadie lo usa) y
            // abre directamente el menú de compartir del móvil. Los enlaces
            // anteriores sin usar NO se anulan: si se invita a dos personas
            // seguidas, el primero tiene que seguir valiendo.
            Card(
                shape = RoundedCornerShape(NubiGeometria.radioChico),
                colors = CardDefaults.cardColors(containerColor = colores.lienzoAlto),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        L10n.t("Tu nombre (opcional)"),
                        style = MaterialTheme.typography.labelMedium,
                        color = colores.tintaSuave
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            ambito.launch {
                                creando = true
                                error = null
                                try {
                                    val de = nombre.trim()
                                    preferencias.edit().putString("nombre", de).apply()
                                    val token = Compartir.crearInvitacion(bebeId)
                                    val url = construirEnlaceInvitacion(token, de, nombreBebe)
                                    compartirTexto(contexto, textoCompartir(de, nombreBebe, url))
                                } catch (e: Exception) {
                                    error = "${L10n.t("No se pudo crear el enlace:")} ${e.message}"
                                } finally {
                                    creando = false
                                }
                            }
                        },
                        enabled = !creando,
                        colors = ButtonDefaultsRosa(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (creando) L10n.t("Creando…") else L10n.t("Compartir enlace"))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        L10n.t("Cada vez que lo pulses se crea un enlace nuevo, para una sola persona. Caduca a los 7 días si nadie lo usa."),
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.tintaTenue
                    )
                }
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = colores.coral)
            }

            if (personasInvitadas > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "${textoPersonas(personasInvitadas, invitado = true)} ${L10n.t("a estos datos.")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.tintaTenue
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        ambito.launch {
                            Compartir.dejarDeCompartir(bebeId)
                            alCerrar()
                        }
                    }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icono(IconoNubi.PAPELERA, tint = colores.indigo, tamano = 14.dp)
                Spacer(Modifier.width(7.dp))
                Text(L10n.t("Dejar de compartir"), style = MaterialTheme.typography.bodyMedium, color = colores.indigo)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ButtonDefaultsRosa() = ButtonDefaults.buttonColors(
    containerColor = NubiTheme.colores.rosa,
    contentColor = NubiTheme.colores.tinta
)

/** Mismo formato que Sincronizador.enlaceInvitacion() en iOS, con "token" en vez de "u". */
private fun construirEnlaceInvitacion(token: String, de: String, bebe: String): String {
    val builder = Uri.Builder()
        .scheme("https")
        .authority("appnubi.netlify.app")
        .path("/invitar")
        .appendQueryParameter("bebe", bebe)
        .appendQueryParameter("token", token)
    if (de.isNotEmpty()) builder.appendQueryParameter("de", de)
    return builder.build().toString()
}

private fun textoCompartir(de: String, bebe: String, url: String): String {
    val quien = de.ifEmpty { L10n.t("Alguien especial") }
    return "🌙 $quien ${L10n.t("te invita a Nubi para compartir el sueño y los cuidados de")} $bebe. ${L10n.t("Toca el enlace para unirte:")} $url"
}

private fun compartirTexto(contexto: Context, texto: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, texto)
    }
    contexto.startActivity(Intent.createChooser(intent, null))
}
