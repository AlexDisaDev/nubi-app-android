package app.nubi.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nubi.data.Compras
import app.nubi.data.EventoNombre
import app.nubi.data.EventosFunnel
import app.nubi.data.Repositorio
import app.nubi.i18n.L10n
import app.nubi.ui.theme.NubiGeometria
import app.nubi.ui.theme.NubiTheme
import com.android.billingclient.api.ProductDetails
import kotlin.math.roundToInt

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
 * elegir un plan solo selecciona la tarjeta — el único botón que dispara
 * la compra es el de abajo de todo (ver [BotonAccion]), igual que en iOS
 * (Paywall.swift: tarjetaPlan selecciona, accion compra).
 */
@Composable
fun PantallaPaywall() {
    val colores = NubiTheme.colores

    // Una vez por composición (no en cada recomposición al escribir/tocar
    // algo): marca el paso más arriba del embudo, "vio el paywall".
    LaunchedEffect(Unit) {
        EventosFunnel.registrar(EventoNombre.PAYWALL_MOSTRADO)
    }

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
        Spacer(Modifier.height(24.dp))

        Ventajas()

        Spacer(Modifier.height(24.dp))

        SelectorPlanesPremium()

        Spacer(Modifier.height(20.dp))
        BloqueCanjearCodigo(alCanjear = { Repositorio.sincronizarEnSegundoPlano() })
    }
}

/**
 * Las 3 ventajas más fuertes, recortadas a una línea cada una.
 *
 * Son las mismas 3 (texto y colores) que ya tiene iOS de sus 5 en
 * Paywall.swift (ventanas que aprenden, curvas de peso y estatura, datos
 * protegidos) — no son nuevo copy inventado aquí, solo la selección más
 * corta para que se lea antes de decidir, no después.
 */
@Composable
private fun Ventajas() {
    val colores = NubiTheme.colores
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        FilaVentaja(
            IconoNubi.NUBE, colores.menta,
            L10n.t("Ventanas que aprenden de tu bebé"),
            L10n.t("El ritmo real del tuyo, afinado cada día, no una tabla por edad.")
        )
        FilaVentaja(
            IconoNubi.REGLA, colores.melocoton,
            L10n.t("Curvas de peso y estatura"),
            L10n.t("La evolución entre revisión y revisión, no solo el último número.")
        )
        FilaVentaja(
            IconoNubi.CANDADO, colores.lila,
            L10n.t("Tus datos, protegidos"),
            L10n.t("Nunca se comparten ni se usan para entrenar nada.")
        )
    }
}

@Composable
private fun FilaVentaja(icono: IconoNubi, color: androidx.compose.ui.graphics.Color, titulo: String, texto: String) {
    val colores = NubiTheme.colores
    Row(verticalAlignment = Alignment.Top) {
        BadgeIcono(icono, colorFondo = color, colorIcono = colores.tinta, tamano = 34.dp, tamanoIcono = 16.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colores.tinta)
            Spacer(Modifier.height(2.dp))
            Text(texto, style = MaterialTheme.typography.bodySmall, color = colores.tintaSuave)
        }
    }
}

/**
 * Las tarjetas de plan, reutilizadas aquí y en HojaPremiumBloqueado
 * (HojasRegistro.kt). Solo SELECCIONAN (radio + marcan plan_seleccionado);
 * la compra la dispara el único CTA de abajo ([BotonAccion]) — igual que
 * tarjetaPlan()/accion en Paywall.swift.
 *
 * Antes había un botón de compra POR tarjeta, y "elegir plan" y "empezar
 * prueba" eran el mismo tap: plan_seleccionado y trial_iniciado se
 * registraban en el mismo instante, así que esos dos pasos del embudo
 * salían siempre con el mismo número y no servían para comparar con iOS
 * (donde sí son dos momentos distintos). Con la selección separada del
 * CTA, vuelven a ser dos eventos de verdad.
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

    // El anual preseleccionado por defecto: es el plan de más valor y el
    // que normalmente trae insignia de ahorro — igual que en iOS, donde
    // `seleccionado` arranca ya con un producto en vez de vacío.
    var seleccionado by remember { mutableStateOf(Compras.PLAN_ANUAL) }

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
                activo = seleccionado == planId,
                onSeleccionar = {
                    seleccionado = planId
                    EventosFunnel.registrar(EventoNombre.PLAN_SELECCIONADO, mapOf("plan_id" to planId))
                }
            )
            Spacer(Modifier.height(10.dp))
        }

        BotonAccion(
            detalles = detalles,
            planId = seleccionado,
            habilitado = activity != null && !verificando,
            verificando = verificando,
            onComprar = { activity?.let { Compras.comprar(it, seleccionado) } }
        )

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
        TextButton(
            onClick = {
                EventosFunnel.registrar(EventoNombre.RESTAURAR_COMPRA_PULSADO)
                Compras.refrescar()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(L10n.t("¿Ya pagaste? Restaurar compra"))
        }
    }
}

@Composable
private fun TarjetaPlan(
    detalles: ProductDetails,
    planId: String,
    etiqueta: String,
    activo: Boolean,
    onSeleccionar: () -> Unit
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
    val precioMensualizado = precioMensualizadoTexto(detalles, planId, faseFinal.billingPeriod)
    val ahorro = ahorroPorcentaje(detalles, planId)

    Card(
        onClick = onSeleccionar,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                CirculoSeleccion(activo)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(etiqueta, style = MaterialTheme.typography.titleMedium, color = colores.tinta, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    // Si hay prueba gratis, eso es lo que más importa ver
                    // aquí ("gratis 7 días, luego X"); si no, el "sale a
                    // X/mes" normalizado, para comparar planes de un vistazo.
                    Text(
                        if (hayPrueba) textoPrueba else (precioMensualizado ?: textoPrecio),
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.tintaSuave
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    textoPrecio,
                    style = MaterialTheme.typography.titleMedium,
                    color = colores.tinta,
                    fontWeight = FontWeight.SemiBold
                )
                if (ahorro != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        L10n.t("Ahorras") + " $ahorro%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colores.tinta,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(colores.mantequilla)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

/** Círculo de selección tipo radio: borde siempre, relleno solo si está activo. */
@Composable
private fun CirculoSeleccion(activo: Boolean) {
    val colores = NubiTheme.colores
    Box(
        modifier = Modifier
            .width(22.dp)
            .height(22.dp)
            .clip(CircleShape)
            .border(2.dp, if (activo) colores.indigo else colores.tintaTenue, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (activo) {
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(colores.indigo)
            )
        }
    }
}

/**
 * El único botón que dispara la compra. Antes cada tarjeta tenía el suyo
 * ("Empezar prueba"/"Elegir"); ahora hay uno solo, igual que `accion` en
 * Paywall.swift: elegir plan (arriba) y confirmar (aquí) son dos pasos
 * distintos y dos eventos distintos del embudo.
 */
@Composable
private fun BotonAccion(
    detalles: ProductDetails,
    planId: String,
    habilitado: Boolean,
    verificando: Boolean,
    onComprar: () -> Unit
) {
    val oferta = detalles.subscriptionOfferDetails?.firstOrNull { it.basePlanId == planId }
    val hayPrueba = (oferta?.pricingPhases?.pricingPhaseList?.size ?: 0) > 1 &&
        oferta?.pricingPhases?.pricingPhaseList?.firstOrNull()?.priceAmountMicros == 0L

    Spacer(Modifier.height(6.dp))
    Button(
        onClick = {
            // plan_seleccionado ya se registró al tocar la tarjeta
            // (TarjetaPlan.onSeleccionar) — aquí solo va trial_iniciado,
            // que es el paso siguiente del embudo, no el mismo.
            if (hayPrueba) {
                EventosFunnel.registrar(EventoNombre.TRIAL_INICIADO, mapOf("plan_id" to planId))
            }
            onComprar()
        },
        enabled = habilitado,
        colors = ButtonDefaults.buttonColors(containerColor = NubiTheme.colores.indigo),
        shape = RoundedCornerShape(NubiGeometria.radio),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        if (verificando) {
            CircularProgressIndicator(modifier = Modifier.height(18.dp))
        } else {
            Text(
                if (hayPrueba) L10n.t("Empezar prueba gratis de 7 días") else L10n.t("Suscribirme"),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * "sale a X/mes" para planes de más de un mes — mismo criterio que
 * `precioMensualizado()` en Suscripcion.swift (iOS): solo se muestra si
 * dura más de 1 mes, para no decir "sale a X/mes" del propio mensual.
 */
private fun precioMensualizadoTexto(detalles: ProductDetails, planId: String, periodo: String): String? {
    val meses = mesesDe(periodo) ?: return null
    if (meses <= 1) return null
    val oferta = detalles.subscriptionOfferDetails?.firstOrNull { it.basePlanId == planId } ?: return null
    val faseFinal = oferta.pricingPhases.pricingPhaseList.lastOrNull() ?: return null
    if (faseFinal.priceAmountMicros <= 0) return null
    val porMesMicros = faseFinal.priceAmountMicros / meses
    val formateado = formatearMicros(porMesMicros, faseFinal.formattedPrice, faseFinal.priceAmountMicros)
    return "${L10n.t("sale a")} $formateado/${L10n.t("mes")}"
}

/**
 * % de ahorro de este plan contra el mensual, calculado de verdad a
 * partir de los precios reales que devuelve Play Billing — no es un
 * número fijo escrito a mano. Misma fórmula y mismos umbrales que
 * `ahorroAnual()`/`ahorroTrimestral()` en Suscripcion.swift (iOS):
 * (coste del mensual × N meses − precio del plan) / ese coste × 100,
 * redondeado, y solo se muestra si supera el umbral (10% anual, 5%
 * trimestral) — un ahorro menor no merece el ruido visual de la insignia.
 */
private fun ahorroPorcentaje(detalles: ProductDetails, planId: String): Int? {
    val (meses, umbral) = when (planId) {
        Compras.PLAN_ANUAL -> 12 to 10
        Compras.PLAN_TRIMESTRAL -> 3 to 5
        else -> return null
    }
    val ofertaPlan = detalles.subscriptionOfferDetails?.firstOrNull { it.basePlanId == planId } ?: return null
    val faseFinalPlan = ofertaPlan.pricingPhases.pricingPhaseList.lastOrNull() ?: return null
    val ofertaMensual = detalles.subscriptionOfferDetails?.firstOrNull { it.basePlanId == Compras.PLAN_MENSUAL } ?: return null
    val faseFinalMensual = ofertaMensual.pricingPhases.pricingPhaseList.lastOrNull() ?: return null
    if (faseFinalMensual.priceAmountMicros <= 0) return null

    val costeNMeses = faseFinalMensual.priceAmountMicros * meses
    if (costeNMeses <= faseFinalPlan.priceAmountMicros) return null

    val ratio = (costeNMeses - faseFinalPlan.priceAmountMicros).toDouble() / costeNMeses.toDouble() * 100
    val pct = ratio.roundToInt()
    return if (pct >= umbral) pct else null
}

/** "P1M" -> 1, "P3M" -> 3, "P1Y" -> 12… para los cálculos de arriba. */
private fun mesesDe(iso: String): Int? = when (iso) {
    "P1M" -> 1
    "P3M" -> 3
    "P6M" -> 6
    "P1Y" -> 12
    else -> null
}

/**
 * Formatea un precio en micros reutilizando el símbolo/formato de moneda
 * que ya trae Play en `formattedPrice`, sin tener que volver a resolver
 * el Locale: se calcula la proporción numérica sobre el texto formateado
 * original en vez de reconstruir el string de cero.
 */
private fun formatearMicros(micros: Long, formattedOriginal: String, microsOriginal: Long): String {
    if (microsOriginal <= 0) return formattedOriginal
    // Separa el símbolo/moneda de la cifra: se sustituye solo la parte
    // numérica, buscando el primer grupo de dígitos (con separadores).
    val regex = Regex("[0-9][0-9.,]*")
    val match = regex.find(formattedOriginal) ?: return formattedOriginal
    val valor = (micros.toDouble() / 1_000_000.0)
    val cifra = String.format(java.util.Locale.getDefault(), "%.2f", valor)
    return formattedOriginal.replaceRange(match.range, cifra)
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
