package app.nubi.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nubi.data.Compras
import app.nubi.data.Repositorio
import app.nubi.i18n.L10n
import app.nubi.ui.theme.NubiGeometria
import app.nubi.ui.theme.NubiTheme
import com.android.billingclient.api.ProductDetails

/**
 * Muro de pago "duro", tal cual como iOS: bloquea toda la app hasta que
 * haya una suscripción vigente (Perfil.tieneAccesoPagado()). Se muestra
 * desde MainActivity.ContenidoApp() en cuanto hay un bebé creado y no hay
 * acceso de pago — ver esa función para el porqué no se usa
 * Perfil.puedeUsarApp() aquí (esa sí deja pasar durante fechaCortePrueba,
 * que ya no es el mecanismo de prueba de Android).
 *
 * El trial de 7 días vive en Play Billing (con tarjeta), no aquí: cada
 * plan ya trae su oferta de prueba configurada en Play Console, así que
 * el botón de cada plan es simplemente "suscribirse" — Play se encarga
 * de cobrar 0 € los primeros 7 días y el precio normal después.
 */
@Composable
fun PantallaPaywall() {
    val colores = NubiTheme.colores
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NubiGeometria.margen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BadgeIcono(IconoNubi.ESTRELLA, colorFondo = colores.lila, colorIcono = colores.tinta, tamano = 60.dp, tamanoIcono = 28.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            L10n.t("Prueba Nubi Premium gratis 7 días"),
            style = MaterialTheme.typography.headlineSmall,
            color = colores.tinta,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            L10n.t("Después del séptimo día se cobra el plan que elijas. Cancela cuando quieras desde Google Play."),
            style = MaterialTheme.typography.bodyMedium,
            color = colores.tintaSuave,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        SelectorPlanesPremium()

        Spacer(Modifier.height(20.dp))
        BloqueCanjearCodigo(alCanjear = { Repositorio.sincronizarEnSegundoPlano() })
    }
}

/**
 * Los tres botones de plan, reutilizados aquí y en HojaPremiumBloqueado
 * (HojasRegistro.kt) — el muro de pago inicial y el aviso que sale si de
 * todas formas se llega a tocar un registro sin acceso vigente usan
 * exactamente los mismos botones, por si Play no ha confirmado la compra
 * todavía cuando se llega a ese segundo sitio.
 */
@Composable
fun SelectorPlanesPremium() {
    val colores = NubiTheme.colores
    val detalles = Compras.detalles
    val verificando by Compras.verificando.collectAsState()
    val error by Compras.ultimoError.collectAsState()
    val activity = LocalContext.current as? Activity

    if (detalles == null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Text(
                L10n.t("Cargando los planes…"),
                style = MaterialTheme.typography.bodySmall,
                color = colores.tintaTenue
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        listOf(
            Compras.PLAN_ANUAL to L10n.t("Anual"),
            Compras.PLAN_TRIMESTRAL to L10n.t("Trimestral"),
            Compras.PLAN_MENSUAL to L10n.t("Mensual"),
        ).forEach { (planId, etiqueta) ->
            TarjetaPlan(
                detalles = detalles,
                planId = planId,
                etiqueta = etiqueta,
                habilitado = activity != null && !verificando,
                onElegir = { activity?.let { Compras.comprar(it, planId) } }
            )
            Spacer(Modifier.height(10.dp))
        }

        if (verificando) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(L10n.t("Comprobando tu compra…"), style = MaterialTheme.typography.bodySmall, color = colores.tintaSuave)
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { Compras.refrescar() }, modifier = Modifier.fillMaxWidth()) {
            Text(L10n.t("¿Ya pagaste? Restaurar compra"))
        }
    }
}

@Composable
private fun TarjetaPlan(
    detalles: ProductDetails,
    planId: String,
    etiqueta: String,
    habilitado: Boolean,
    onElegir: () -> Unit
) {
    val colores = NubiTheme.colores
    val oferta = detalles.subscriptionOfferDetails?.firstOrNull { it.basePlanId == planId }
    val fases = oferta?.pricingPhases?.pricingPhaseList.orEmpty()
    if (oferta == null || fases.isEmpty()) return

    val faseFinal = fases.last()
    val hayPrueba = fases.size > 1 && fases.first().priceAmountMicros == 0L
    val textoPrecio = "${faseFinal.formattedPrice}${sufijoPeriodo(faseFinal.billingPeriod)}"
    val textoPrueba = if (hayPrueba) {
        "${L10n.t("gratis")} ${duracionTexto(fases.first().billingPeriod)}, ${L10n.t("luego")} $textoPrecio"
    } else {
        textoPrecio
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = colores.superficie),
        shape = RoundedCornerShape(NubiGeometria.radio),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(etiqueta, style = MaterialTheme.typography.titleMedium, color = colores.tinta, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(textoPrueba, style = MaterialTheme.typography.bodySmall, color = colores.tintaSuave)
            }
            Button(onClick = onElegir, enabled = habilitado) {
                Text(if (hayPrueba) L10n.t("Empezar prueba") else L10n.t("Elegir"))
            }
        }
    }
}

/** "P7D"/"P1W" -> "7 días", "P1M" -> "1 mes"… para el texto de la prueba. */
private fun duracionTexto(iso: String): String = when (iso) {
    "P3D" -> L10n.t("3 días")
    "P7D", "P1W" -> L10n.t("7 días")
    "P14D", "P2W" -> L10n.t("14 días")
    "P1M" -> L10n.t("1 mes")
    "P3M" -> L10n.t("3 meses")
    "P6M" -> L10n.t("6 meses")
    "P1Y" -> L10n.t("1 año")
    else -> iso
}

/** "P1M" -> "/mes", "P3M" -> "/3 meses"… para el precio tras la prueba. */
private fun sufijoPeriodo(iso: String): String = when (iso) {
    "P1M" -> "/${L10n.t("mes")}"
    "P3M" -> "/3 ${L10n.t("meses")}"
    "P6M" -> "/6 ${L10n.t("meses")}"
    "P1Y" -> "/${L10n.t("año")}"
    else -> ""
}
