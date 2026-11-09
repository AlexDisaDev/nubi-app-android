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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nubi.R
import app.nubi.data.Compras
import app.nubi.data.EventoNombre
import app.nubi.data.EventosFunnel
import app.nubi.data.Premium
import app.nubi.data.Repositorio
import app.nubi.data.ResenasAppStore
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
 * Cuánto encoger el paywall para que entre de una pantalla. Es el equivalente
 * del `ViewThatFits` de iOS (Paywall.swift): allí SwiftUI prueba 1 → 0,92 →
 * 0,84 y se queda con la que cabe; aquí no existe nada parecido, así que la
 * escala se decide por la altura disponible. El `verticalScroll` de arriba
 * sigue puesto como red de seguridad (letra del sistema enorme, pantallas
 * raras), pero en un móvil normal no debería llegar a hacer falta.
 */
private fun escalaPara(alturaDisponible: Dp): Float = when {
    alturaDisponible < 640.dp -> 0.82f
    alturaDisponible < 720.dp -> 0.88f
    alturaDisponible < 800.dp -> 0.94f
    else -> 1f
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

    // El feed de reseñas se pide aquí, no al arrancar la app: solo hace
    // falta cuando alguien está mirando el paywall.
    LaunchedEffect(Unit) { ResenasAppStore.cargar() }

    // El canje de código vive abajo, en la fila de enlaces del pie; al
    // tocarlo despliega el campo justo encima de la letra pequeña.
    var canjeando by remember { mutableStateOf(false) }

    // La escala se saca de la altura de la PANTALLA, no de las restricciones
    // del contenedor: este mismo contenido se pinta dentro de una hoja con
    // verticalScroll (HojasRegistro.kt: HojaPremiumBloqueado), y ahí las
    // restricciones de altura llegan infinitas, así que un BoxWithConstraints
    // devolvería siempre escala 1 y volvería el scroll que queremos evitar.
    val e = escalaPara(LocalConfiguration.current.screenHeightDp.dp)

    Box(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NubiGeometria.margen * e)
                .padding(top = 8.dp * e, bottom = 12.dp * e),
            verticalArrangement = Arrangement.spacedBy(16.dp * e)
        ) {
            CabeceraPaywall(e)
            VentajasPaywall(e)
            LineaTiempoPrueba(e)
            SelectorPlanesPremium(e)
            LetraPequenaPaywall(
                escala = e,
                canjeando = canjeando,
                onCanjear = { canjeando = true }
            )
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
private fun CabeceraPaywall(e: Float) {
    val colores = NubiTheme.colores
    val detalles = Compras.detalles
    val prueba = detalles?.let { textoPruebaDe(it, Compras.PLAN_ANUAL) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp * e)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.nubi_logo_splash),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(26.dp * e)
            )
            Spacer(Modifier.width(8.dp * e))
            Text(
                L10n.t("NUBI PREMIUM"),
                fontFamily = RobotoCondensedFamily,
                fontSize = 13.sp * e,
                letterSpacing = 1.4.sp,
                color = colores.indigo
            )
        }
        Text(
            L10n.t("Anticípate siempre a su próxima siesta"),
            fontFamily = RobotoCondensedFamily,
            fontSize = 29.sp * e,
            lineHeight = 33.sp * e,
            color = colores.tinta
        )
        // Igual que iOS: si el plan trae prueba, el subtítulo explica que
        // se cobra sola al terminar. Si esta cuenta ya no tiene prueba, no
        // se inventa una ni se rellena el hueco con otra frase: no sale
        // nada y el titular queda solo.
        if (prueba != null) {
            Text(
                L10n.subtituloConPrueba(prueba),
                fontFamily = RobotoCondensedFamily,
                fontSize = 14.sp * e,
                color = colores.tintaSuave
            )
        }
        FilaResena(e)
    }
}

/**
 * Estrellas + una reseña REAL (ver data/ResenasAppStore.kt). Si no se ha
 * podido traer ninguna, esta fila no existe: nada de testimonios inventados
 * ni de estrellas de adorno. Igual que `filaReseña` en Paywall.swift.
 */
@Composable
private fun FilaResena(e: Float) {
    val colores = NubiTheme.colores
    val resena = ResenasAppStore.destacada ?: return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp * e)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            repeat(resena.estrellas) {
                Icono(IconoNubi.ESTRELLA, tint = colores.mantequilla, tamano = 11.dp * e)
            }
        }
        Text(
            "«${resena.titulo}»",
            fontFamily = RobotoCondensedFamily,
            fontSize = 13.sp * e,
            color = colores.tintaSuave,
            maxLines = 1
        )
        Text(
            resena.autor,
            fontFamily = RobotoCondensedFamily,
            fontSize = 11.sp * e,
            color = colores.tintaTenue,
            maxLines = 1
        )
    }
}

/**
 * Tres ventajas, una línea cada una (antes cuatro: "Tus datos, protegidos"
 * se retira porque "Comparte el cuidado con pareja y familia" cubre ese
 * terreno de forma más concreta). Igual que `ventajas` en Paywall.swift.
 */
@Composable
private fun VentajasPaywall(e: Float) {
    val colores = NubiTheme.colores
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp * e)
    ) {
        FilaVentaja(IconoNubi.RELOJ, colores.cielo, L10n.t("Registra la rutina completa del bebé"), e)
        FilaVentaja(IconoNubi.REGLA, colores.rosa, L10n.t("Registro de peso y estatura"), e)
        FilaVentaja(IconoNubi.CORAZON, colores.lila, L10n.t("Comparte el cuidado con pareja y familia"), e)
    }
}

/**
 * Los tres momentos de la prueba gratuita, en una tarjeta encima de los
 * planes: hoy entras entero, el día 5 te avisamos y el día 7 empieza el
 * cobro. Quita la duda de "¿y cuándo me cobran?", que es lo que más frena
 * en un paywall con tarjeta por delante. Igual que `lineaTiempo` en
 * Paywall.swift.
 *
 * Solo sale si el plan anual TRAE prueba: a quien ya la gastó no se le
 * enseña un calendario que no va a tener.
 */
@Composable
private fun LineaTiempoPrueba(e: Float) {
    val colores = NubiTheme.colores
    val detalles = Compras.detalles ?: return
    if (textoPruebaDe(detalles, Compras.PLAN_ANUAL) == null) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NubiGeometria.radio * e))
            .background(colores.superficie)
            .padding(vertical = 13.dp * e, horizontal = 10.dp * e),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(4.dp * e)
    ) {
        HitoPrueba(L10n.t("Hoy"), L10n.t("Acceso completo"), true, e, Modifier.weight(1f))
        ConectorPrueba(e)
        HitoPrueba(L10n.t("Día 5"), L10n.t("Te avisamos"), false, e, Modifier.weight(1f))
        ConectorPrueba(e)
        HitoPrueba(L10n.t("Día 7"), L10n.t("Empieza tu plan"), false, e, Modifier.weight(1f))
    }
}

@Composable
private fun HitoPrueba(
    titulo: String,
    pie: String,
    activo: Boolean,
    e: Float,
    modifier: Modifier = Modifier
) {
    val colores = NubiTheme.colores
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp * e)
    ) {
        Box(
            modifier = Modifier
                .size(11.dp * e)
                .clip(CircleShape)
                .background(if (activo) colores.indigo else colores.lila)
        )
        Text(
            titulo,
            fontFamily = RobotoCondensedFamily,
            fontSize = 13.sp * e,
            color = colores.tinta
        )
        Text(
            pie,
            fontFamily = RobotoCondensedFamily,
            fontSize = 11.sp * e,
            color = colores.tintaSuave,
            textAlign = TextAlign.Center
        )
    }
}

/** La rayita entre dos hitos, centrada con el punto que tiene al lado. */
@Composable
private fun ConectorPrueba(e: Float) {
    val colores = NubiTheme.colores
    Box(
        modifier = Modifier
            .padding(top = (11.dp * e - 3.dp) / 2)
            .width(24.dp * e)
            .height(3.dp)
            .clip(RoundedCornerShape(50))
            .background(colores.lila.copy(alpha = 0.55f))
    )
}

@Composable
private fun FilaVentaja(icono: IconoNubi, color: Color, titulo: String, e: Float) {
    val colores = NubiTheme.colores
    Row(verticalAlignment = Alignment.CenterVertically) {
        BadgeIcono(icono, colorFondo = color, colorIcono = colores.tinta, tamano = 28.dp * e, tamanoIcono = 14.dp * e)
        Spacer(Modifier.width(12.dp * e))
        Text(
            titulo,
            fontFamily = RobotoCondensedFamily,
            fontSize = 15.sp * e,
            color = colores.tinta
        )
    }
}

/**
 * El pie del paywall: las condiciones centradas y, en UNA sola fila,
 * Términos · Privacidad · Restaurar · Canjear código. Las cuatro son
 * enlaces de texto del mismo tamaño, así que ninguna compite con el botón
 * de compra. Igual que `letraPequeña` en Paywall.swift.
 */
@Composable
private fun LetraPequenaPaywall(
    escala: Float,
    canjeando: Boolean,
    onCanjear: () -> Unit
) {
    val e = escala
    val colores = NubiTheme.colores
    val enlaces = LocalUriHandler.current
    val detalles = Compras.detalles
    val prueba = detalles?.let { textoPruebaDe(it, Compras.PLAN_ANUAL) }
    val precio = detalles?.let { precioFinalDe(it, Compras.PLAN_ANUAL) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp * e)
    ) {
        if (canjeando) {
            BloqueCanjearCodigo(alCanjear = { Repositorio.sincronizarEnSegundoPlano() })
        }

        Text(
            if (prueba != null && precio != null) L10n.condicionesConPrueba(prueba, precio)
            else L10n.t("Se renueva automáticamente. Cancela cuando quieras desde Google Play."),
            fontFamily = RobotoCondensedFamily,
            fontSize = 11.sp * e,
            color = colores.tintaTenue,
            textAlign = TextAlign.Center
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp * e)) {
            EnlacePie(L10n.t("Términos"), e) { enlaces.openUri("https://appnubi.netlify.app/terminos") }
            SeparadorPie(colores.tintaTenue, e)
            EnlacePie(L10n.t("Privacidad"), e) { enlaces.openUri("https://appnubi.netlify.app/privacidad") }
            SeparadorPie(colores.tintaTenue, e)
            EnlacePie(L10n.t("Restaurar"), e) {
                EventosFunnel.registrar(EventoNombre.RESTAURAR_COMPRA_PULSADO)
                Compras.refrescar()
            }
            SeparadorPie(colores.tintaTenue, e)
            EnlacePie(L10n.t("Canjear código"), e, onPulsar = onCanjear)
        }
    }
}

@Composable
private fun EnlacePie(texto: String, e: Float, onPulsar: () -> Unit) {
    Text(
        texto,
        fontFamily = RobotoCondensedFamily,
        fontSize = 12.sp * e,
        color = NubiTheme.colores.tintaSuave,
        modifier = Modifier.clickable { onPulsar() }
    )
}

@Composable
private fun SeparadorPie(color: Color, e: Float) {
    Text("·", fontFamily = RobotoCondensedFamily, fontSize = 12.sp * e, color = color)
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
fun SelectorPlanesPremium(e: Float = 1f) {
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
        verticalArrangement = Arrangement.spacedBy(16.dp * e)
    ) {
        // Bloque "planes": 8 dp entre tarjetas, como iOS. Los 6 dp de
        // arriba son el aire para la cinta de "MEJOR PRECIO", que
        // sobresale por encima de la primera tarjeta.
        Column(
            modifier = Modifier.padding(top = 6.dp * e),
            verticalArrangement = Arrangement.spacedBy(8.dp * e)
        ) {
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
                    e = e,
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
                e = e,
                onComprar = { activity?.let { Compras.comprar(it, seleccionado) } }
            )

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    fontFamily = RobotoCondensedFamily,
                    fontSize = 12.sp * e,
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
    e: Float,
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
    // El anual lleva la cinta de "MEJOR PRECIO": es el que sale más barato
    // por mes de los tres, así que no es una etiqueta de adorno.
    val mejorPrecio = planId == Compras.PLAN_ANUAL

    Box(Modifier.fillMaxWidth()) {
        Card(
            onClick = onSeleccionar,
            colors = CardDefaults.cardColors(containerColor = colores.superficie),
            elevation = CardDefaults.cardElevation(defaultElevation = if (activo) 10.dp else 3.dp),
            shape = RoundedCornerShape(NubiGeometria.radio * e),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 2.dp,
                    color = if (activo) colores.indigo else Color.Transparent,
                    shape = RoundedCornerShape(NubiGeometria.radio * e)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp * e, vertical = 13.dp * e),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CirculoSeleccion(activo, e)
                    Spacer(Modifier.width(12.dp * e))
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            etiqueta,
                            fontFamily = RobotoCondensedFamily,
                            fontSize = 16.sp * e,
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
                                fontSize = 12.sp * e,
                                color = colores.tintaSuave
                            )
                        }
                    }
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        textoPrecio,
                        fontFamily = RobotoCondensedFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 19.sp * e,
                        color = colores.tinta
                    )
                    if (ahorro != null) {
                        Text(
                            L10n.ahorras("$ahorro"),
                            fontFamily = RobotoCondensedFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp * e,
                            letterSpacing = 1.2.sp,
                            color = colores.tinta,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(colores.mantequilla)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // La cinta va montada sobre el borde de arriba, como en el diseño.
        // Al ir dentro del Box no cambia la altura de la tarjeta; el aire
        // de más lo pone la Column de planes con su padding superior.
        if (mejorPrecio) {
            Text(
                L10n.t("MEJOR PRECIO"),
                fontFamily = RobotoCondensedFamily,
                fontSize = 10.sp * e,
                letterSpacing = 1.1.sp,
                color = colores.superficie,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 30.dp * e, y = -10.dp * e)
                    .clip(RoundedCornerShape(50))
                    .background(colores.indigo)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

/** Círculo de selección tipo radio: borde siempre, relleno solo si está activo. */
@Composable
private fun CirculoSeleccion(activo: Boolean, e: Float) {
    val colores = NubiTheme.colores
    Box(
        modifier = Modifier
            .size(21.dp * e)
            .clip(CircleShape)
            .border(2.dp, if (activo) colores.indigo else colores.tintaTenue, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (activo) {
            Box(
                modifier = Modifier
                    .size(11.dp * e)
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
    e: Float,
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
        contentPadding = PaddingValues(vertical = 15.dp * e),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (verificando) {
            CircularProgressIndicator(modifier = Modifier.height(18.dp * e), color = colores.tinta)
        } else {
            Text(
                // Mismo texto que iOS: "Empezar prueba gratis", sin el
                // "de 7 días" (la duración ya sale arriba y en la letra
                // pequeña, y así no miente si algún día cambia la oferta).
                if (hayPrueba) L10n.t("Empezar prueba gratis") else L10n.t("Suscribirme"),
                fontFamily = RobotoCondensedFamily,
                fontSize = 17.sp * e
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
