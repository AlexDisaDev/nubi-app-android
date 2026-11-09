package app.nubi.ui

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nubi.R
import app.nubi.data.Compras
import app.nubi.data.EventoNombre
import app.nubi.data.EventosFunnel
import app.nubi.data.Premium
import app.nubi.data.Repositorio
import app.nubi.i18n.L10n
import app.nubi.ui.theme.NubiGeometria
import app.nubi.ui.theme.NubiTheme
import app.nubi.ui.theme.RobotoCondensedFamily
import com.android.billingclient.api.ProductDetails
import kotlin.math.roundToInt

/**
 * El paywall como PANTALLA entera. Desde que Nubi es freemium, la app ya
 * no se bloquea de entrada, así que hoy el paywall se ve casi siempre
 * como hoja a pantalla completa al tocar algo bloqueado — ver
 * HojaPremiumBloqueado() en HojasRegistro.kt. Las dos pintan lo mismo:
 * [ContenidoPaywall].
 *
 * El trial de 7 días vive en Play Billing (con tarjeta), no aquí: cada
 * plan ya trae su oferta de prueba configurada en Play Console, así que
 * elegir un plan solo selecciona la tarjeta — el único botón que dispara
 * la compra es el de abajo (ver [BotonAccion]), igual que en iOS.
 */
@Composable
fun PantallaPaywall() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ContenidoPaywall()
    }
}

/**
 * COPIA EXACTA del paywall de iOS (Paywall.swift). Mismo orden, mismos
 * tamaños, mismos colores y mismos textos, para que las dos apps se vean
 * iguales:
 *
 *   logo 44 · "Nubi Premium" serif 32 · subtítulo 15
 *   4 ventajas de una línea (insignia 30 + texto 15)
 *   planes (tarjeta con radio, precio y ahorro) + botón de compra en cápsula lila
 *   "Canjear código de regalo", justo debajo del botón
 *   letra pequeña con condiciones y enlaces legales (Términos / Privacidad)
 *   "Restaurar compras", al final del todo
 *
 * Todo alineado a la izquierda y con 20 dp entre bloques, como la
 * VStack(alignment: .leading, spacing: 20) de iOS.
 *
 * Lo único que NO puede ser idéntico, y es a propósito:
 *   - Las fuentes. iOS usa New York (serif) y SF Rounded, que no existen
 *     en Android; aquí son la serif del sistema y la de por defecto (ver
 *     Type.kt). Se parecerá, pero las letras no son las mismas.
 *   - La letra pequeña dice "Google Play" donde iOS dice "los Ajustes de
 *     tu iPhone": es donde se cancela de verdad en cada sitio.
 */
@Composable
fun ContenidoPaywall(onCerrar: (() -> Unit)? = null) {
    val colores = NubiTheme.colores

    // Una vez por composición: marca el paso más arriba del embudo.
    LaunchedEffect(Unit) {
        EventosFunnel.registrar(EventoNombre.PAYWALL_MOSTRADO)
    }

    // Se cierra solo en cuanto el premium se activa de verdad — tanto si
    // viene de comprar (Compras verifica contra el servidor y actualiza
    // premium_hasta en el perfil) como de canjear un código de regalo (la
    // función de Supabase hace lo mismo). No hace falta enganchar un
    // callback a cada camino: basta con mirar el mismo perfil del que ya
    // depende el resto de la app, y cerrar cuando pasa de no-premium a
    // premium mientras este paywall está abierto — igual que iOS cierra
    // el suyo (cerrar()) tanto tras comprar como tras canjear.
    val datos by Repositorio.local.datos.collectAsState()
    val premiumActivo = Premium.activo(datos.perfil)
    val eraPremiumAlAbrir = remember { premiumActivo }
    LaunchedEffect(premiumActivo) {
        if (!eraPremiumAlAbrir && premiumActivo) {
            onCerrar?.invoke()
        }
    }

    Box(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NubiGeometria.margen)
                .padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            CabeceraPaywall()
            VentajasPaywall()
            SelectorPlanesPremium()
            EnlaceCodigoRegalo()
            LetraPequenaPaywall()
            EnlaceRestaurarCompras()
        }

        if (onCerrar != null) {
            BotonCerrarPaywall(onCerrar, Modifier.align(Alignment.TopEnd))
        }
    }
}

/**
 * Logo pequeño + "NUBI PREMIUM" en una línea, arriba del todo; debajo, el
 * titular grande que vende la función, y luego el subtítulo. Igual que
 * `cabecera` en Paywall.swift (antes el logo solo ocupaba 44 dp él solo y
 * "Nubi Premium" era el titular grande).
 */
@Composable
private fun CabeceraPaywall() {
    val colores = NubiTheme.colores
    val detalles = Compras.detalles
    val prueba = detalles?.let { textoPruebaDe(it, Compras.PLAN_ANUAL) }

    Column(
        modifier = Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.nubi_logo_splash),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(28.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                L10n.t("NUBI PREMIUM"),
                fontFamily = RobotoCondensedFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 1.4.sp,
                color = colores.indigo
            )
        }
        Text(
            L10n.t("Anticípate siempre a su próxima siesta"),
            fontFamily = RobotoCondensedFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 30.sp,
            color = colores.tinta
        )
        Text(
            // Igual que iOS: si el plan trae prueba, el subtítulo lo dice;
            // si no, dice qué sigue siendo gratis. Nada de inventarse una
            // prueba que esta cuenta ya no tiene.
            if (prueba != null) L10n.subtituloConPrueba(prueba)
            else L10n.t("Hoy, Crece y salud siguen siendo gratis. Premium desbloquea todas funciones."),
            fontFamily = RobotoCondensedFamily,
            fontSize = 15.sp,
            color = colores.tintaSuave
        )
    }
}

/**
 * Tres ventajas, una línea cada una (antes cuatro: "Tus datos, protegidos"
 * se retira porque "Comparte el cuidado con pareja y familia" cubre ese
 * terreno de forma más concreta). Igual que `ventajas` en Paywall.swift.
 */
@Composable
private fun VentajasPaywall() {
    val colores = NubiTheme.colores
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FilaVentaja(IconoNubi.RELOJ, colores.cielo, L10n.t("Registra la rutina completa del bebé"))
        FilaVentaja(IconoNubi.REGLA, colores.rosa, L10n.t("Registro de peso y estatura"))
        FilaVentaja(IconoNubi.CORAZON, colores.lila, L10n.t("Comparte el cuidado con pareja y familia"))
    }
}

@Composable
private fun FilaVentaja(icono: IconoNubi, color: Color, titulo: String) {
    val colores = NubiTheme.colores
    Row(verticalAlignment = Alignment.CenterVertically) {
        BadgeIcono(icono, colorFondo = color, colorIcono = colores.tinta, tamano = 30.dp, tamanoIcono = 15.dp)
        Spacer(Modifier.width(14.dp))
        Text(
            titulo,
            fontFamily = RobotoCondensedFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = colores.tinta
        )
    }
}

/** Condiciones + enlaces legales. Igual que `letraPequeña`. */
@Composable
private fun LetraPequenaPaywall() {
    val colores = NubiTheme.colores
    val enlaces = LocalUriHandler.current
    val detalles = Compras.detalles
    val prueba = detalles?.let { textoPruebaDe(it, Compras.PLAN_ANUAL) }
    val precio = detalles?.let { precioFinalDe(it, Compras.PLAN_ANUAL) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (prueba != null && precio != null) L10n.condicionesConPrueba(prueba, precio)
            else L10n.t("Se renueva automáticamente. Puedes cancelar cuando quieras desde Google Play."),
            fontFamily = RobotoCondensedFamily,
            fontSize = 11.sp,
            color = colores.tintaTenue
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                L10n.t("Términos"),
                fontFamily = RobotoCondensedFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colores.tintaSuave,
                modifier = Modifier.clickable { enlaces.openUri("https://appnubi.netlify.app/terminos") }
            )
            Text(
                L10n.t("Privacidad"),
                fontFamily = RobotoCondensedFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colores.tintaSuave,
                modifier = Modifier.clickable { enlaces.openUri("https://appnubi.netlify.app/privacidad") }
            )
        }
    }
}

/**
 * El enlace para canjear un código de regalo, justo debajo del botón de
 * compra — ahora también en iOS (Paywall.swift: bloqueCodigoRegalo), con
 * el mismo texto ("Canjear código de regalo", el mismo que ya se usaba en
 * Ajustes) y el mismo sitio. Antes vivía al final de la pantalla junto
 * con "Regalar Nubi a alguien" (ya quitado); ahora va pegado al CTA
 * porque es la otra forma de tener acceso, y conviene que se vea antes
 * de llegar a la letra pequeña.
 */
@Composable
private fun EnlaceCodigoRegalo() {
    val colores = NubiTheme.colores
    var canjeando by remember { mutableStateOf(false) }

    if (canjeando) {
        BloqueCanjearCodigo(alCanjear = { Repositorio.sincronizarEnSegundoPlano() })
    } else {
        Text(
            L10n.t("Canjear código de regalo"),
            fontFamily = RobotoCondensedFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colores.tintaSuave,
            modifier = Modifier.clickable { canjeando = true }
        )
    }
}

/**
 * "Restaurar compras", al final del todo, debajo de Términos y
 * Privacidad: antes vivía junto al botón de compra, pero es una acción de
 * mantenimiento, no parte de decidir qué plan comprar.
 */
@Composable
private fun EnlaceRestaurarCompras() {
    val colores = NubiTheme.colores
    Text(
        L10n.t("Restaurar compras"),
        fontFamily = RobotoCondensedFamily,
        fontSize = 13.sp,
        color = colores.tintaSuave,
        modifier = Modifier.clickable {
            EventosFunnel.registrar(EventoNombre.RESTAURAR_COMPRA_PULSADO)
            Compras.refrescar()
        }
    )
}

/** La X de cerrar de arriba a la derecha, igual que `botonCerrar`. */
@Composable
private fun BotonCerrarPaywall(onCerrar: () -> Unit, modifier: Modifier = Modifier) {
    val colores = NubiTheme.colores
    Box(
        modifier = modifier
            .padding(NubiGeometria.margen)
            .size(32.dp)
            .clip(CircleShape)
            .background(colores.superficie)
            .clickable { onCerrar() },
        contentAlignment = Alignment.Center
    ) {
        Icono(IconoNubi.EQUIS, tint = colores.tintaSuave, tamano = 12.dp)
    }
}

/**
 * Las tarjetas de plan y el botón de compra. Solo SELECCIONAN (radio +
 * marcan plan_seleccionado); la compra la dispara el único CTA de abajo
 * ([BotonAccion]) — igual que tarjetaPlan()/accion en Paywall.swift.
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
                fontFamily = RobotoCondensedFamily,
                color = colores.tintaTenue
            )
        }
        return
    }

    // El anual preseleccionado: es el de más valor y el que trae insignia
    // de ahorro — igual que en iOS, donde `seleccionado` arranca con un
    // producto en vez de vacío.
    var seleccionado by remember { mutableStateOf(Compras.PLAN_ANUAL) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Bloque "planes": 10 dp entre tarjetas, como iOS.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            }
        }

        // Bloque "accion": botón de compra (+ error si lo hay), 12 dp entre ellos.
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BotonAccion(
                detalles = detalles,
                planId = seleccionado,
                habilitado = activity != null && !verificando,
                verificando = verificando,
                onComprar = { activity?.let { Compras.comprar(it, seleccionado) } }
            )

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    fontFamily = RobotoCondensedFamily,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Una tarjeta de plan. Clon de `tarjetaPlan` en Paywall.swift. */
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
    val textoPrecio = "${faseFinal.formattedPrice}${sufijoPeriodo(faseFinal.billingPeriod)}"
    val textoOferta = textoOfertaDe(detalles, planId, faseFinal.billingPeriod)
    val ahorro = ahorroPorcentaje(detalles, planId)

    Card(
        onClick = onSeleccionar,
        colors = CardDefaults.cardColors(containerColor = colores.superficie),
        elevation = CardDefaults.cardElevation(defaultElevation = if (activo) 10.dp else 3.dp),
        shape = RoundedCornerShape(NubiGeometria.radio),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = if (activo) colores.indigo else Color.Transparent,
                shape = RoundedCornerShape(NubiGeometria.radio)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NubiGeometria.margen),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CirculoSeleccion(activo)
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        etiqueta,
                        fontFamily = RobotoCondensedFamily,
                        fontSize = 16.sp,
                        color = colores.tinta,
                        fontWeight = FontWeight.SemiBold
                    )
                    // "7 días gratis, luego X€/mes" si ese plan aún tiene
                    // prueba; si no, el antiguo "Sale a X/mes" (y nada en
                    // el mensual sin prueba, que ya se paga mes a mes).
                    if (textoOferta != null) {
                        Text(
                            textoOferta,
                            fontFamily = RobotoCondensedFamily,
                            fontSize = 12.sp,
                            color = colores.tintaSuave
                        )
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    textoPrecio,
                    fontFamily = RobotoCondensedFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp,
                    color = colores.tinta
                )
                if (ahorro != null) {
                    Text(
                        L10n.ahorras("$ahorro"),
                        fontFamily = RobotoCondensedFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
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
            .size(22.dp)
            .clip(CircleShape)
            .border(2.dp, if (activo) colores.indigo else colores.tintaTenue, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (activo) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(colores.indigo)
            )
        }
    }
}

/** "7 días gratis" del plan, o null si ese plan no trae prueba. */
private fun textoPruebaDe(detalles: ProductDetails, planId: String): String? {
    val fases = detalles.subscriptionOfferDetails
        ?.firstOrNull { it.basePlanId == planId }
        ?.pricingPhases?.pricingPhaseList.orEmpty()
    if (fases.size <= 1 || fases.first().priceAmountMicros != 0L) return null
    return "${duracionTexto(fases.first().billingPeriod)} ${L10n.t("gratis")}"
}

/** El precio que se cobra cuando acaba la prueba, ya formateado. */
private fun precioFinalDe(detalles: ProductDetails, planId: String): String? {
    val faseFinal = detalles.subscriptionOfferDetails
        ?.firstOrNull { it.basePlanId == planId }
        ?.pricingPhases?.pricingPhaseList?.lastOrNull() ?: return null
    return "${faseFinal.formattedPrice}${sufijoPeriodo(faseFinal.billingPeriod)}"
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

    val colores = NubiTheme.colores

    // Cápsula lila con el texto en tinta, como en iOS: allí es
    // `.background(Theme.lila, in: Capsule())` con 17 pt de aire arriba y
    // abajo. Antes aquí era un botón índigo de esquinas redondeadas y 52
    // de alto, que es lo que hacía que los dos paywalls no se parecieran.
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
        colors = ButtonDefaults.buttonColors(
            containerColor = colores.lila,
            contentColor = colores.tinta
        ),
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(vertical = 17.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (verificando) {
            CircularProgressIndicator(modifier = Modifier.height(18.dp), color = colores.tinta)
        } else {
            Text(
                // Mismo texto que iOS: "Empezar prueba gratis", sin el
                // "de 7 días" (la duración ya sale arriba y en la letra
                // pequeña, y así no miente si algún día cambia la oferta).
                if (hayPrueba) L10n.t("Empezar prueba gratis") else L10n.t("Suscribirme"),
                fontFamily = RobotoCondensedFamily,
                fontSize = 16.sp,
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
 * Precio por mes de CUALQUIER plan, prueba incluida: el mensual es él
 * mismo, el trimestral y el anual se dividen entre sus meses. A diferencia
 * de `precioMensualizadoTexto`, no añade "sale a"/"mes" ni se oculta para
 * el plan mensual — lo usa `textoOfertaDe` para componer "7 días gratis,
 * luego 3,33 €/mes". Igual que `precioPorMes` en Suscripcion.swift (iOS).
 */
private fun precioPorMesFormateado(detalles: ProductDetails, planId: String, periodo: String): String? {
    val meses = mesesDe(periodo) ?: return null
    val oferta = detalles.subscriptionOfferDetails?.firstOrNull { it.basePlanId == planId } ?: return null
    val faseFinal = oferta.pricingPhases.pricingPhaseList.lastOrNull() ?: return null
    if (faseFinal.priceAmountMicros <= 0) return null
    if (meses <= 1) return faseFinal.formattedPrice
    val porMesMicros = faseFinal.priceAmountMicros / meses
    return formatearMicros(porMesMicros, faseFinal.formattedPrice, faseFinal.priceAmountMicros)
}

/**
 * Texto bajo el nombre del plan: "7 días gratis, luego 3,33 €/mes" si ese
 * plan todavía tiene prueba disponible; si no, el antiguo "Sale a X/mes"
 * (y nada en el mensual sin prueba, que ya se paga mes a mes). Igual que
 * `textoOferta` en Paywall.swift.
 */
private fun textoOfertaDe(detalles: ProductDetails, planId: String, periodo: String): String? {
    val prueba = textoPruebaDe(detalles, planId)
    if (prueba != null) {
        val porMes = precioPorMesFormateado(detalles, planId, periodo) ?: return null
        return L10n.pruebaLuegoPorMes(prueba, porMes)
    }
    return precioMensualizadoTexto(detalles, planId, periodo)
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
