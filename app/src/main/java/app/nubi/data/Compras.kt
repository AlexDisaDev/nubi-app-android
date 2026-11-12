package app.nubi.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.Period
import java.time.ZoneId

/**
 * Compras dentro de la app (Google Play Billing) para la suscripción
 * Premium de Nubi — producto "Nubi Pro" (ID_PRODUCTO) con tres planes
 * base (mensual/trimestral/anual), cada uno con su oferta de 7 días de
 * prueba gratis (con tarjeta, "tal cual como iOS" — ver backend/README.md
 * y el Edge Function de abajo).
 *
 * IMPORTANTE — esto NO decide por sí solo si el usuario puede usar la
 * app. Una compra en Play solo es un indicio: la verdad la dice el
 * servidor. Tras cada compra (o al recuperar compras pendientes), se
 * llama al Edge Function "verificar-compra-android", que vuelve a
 * consultar la compra directamente a Google (con una cuenta de servicio,
 * no con nada que mande este móvil) y SOLO entonces actualiza
 * premium_hasta/premium_fuente en "perfiles" — ver
 * backend/005_premium_suscripcion_android.sql. El gate real de la app
 * (PantallaPaywall, en ui/) se basa en ese premium_hasta, nunca en lo
 * que este objeto crea que pasó.
 *
 * IDs: tienen que coincidir EXACTAMENTE con el producto "Nubi Pro" ya
 * creado en Play Console → Monetizar con Play → Productos → Suscripciones
 * (creado el 28 oct 2026). Si alguna vez renombras el producto o los
 * planes base ahí, cámbialos también aquí.
 */
object Compras : PurchasesUpdatedListener {

    private const val TAG = "Compras"

    /** ID del producto de suscripción en Play Console ("Nubi Pro"). */
    const val ID_PRODUCTO = "nubi_pro"

    /** IDs de los planes base dentro de ese producto. */
    const val PLAN_MENSUAL = "plan-mensual"
    const val PLAN_TRIMESTRAL = "plan-trimestral"
    const val PLAN_ANUAL = "plan-anual"

    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var iniciado = false
    private lateinit var cliente: BillingClient
    private var contextoApp: Context? = null

    /** Último plan pedido con comprar(), para mandarlo como respaldo al
     *  verificar (el Edge Function usa sobre todo lo que diga Google, no
     *  esto — ver comentario en el propio Edge Function). */
    private var planSolicitado: String? = null

    /** Detalles del producto ya consultados a Play. Null hasta que termine la consulta (o si falla). */
    var detalles: ProductDetails? = null
        private set

    private val _verificando = MutableStateFlow(false)
    /** True mientras se está comprobando una compra contra el servidor
     *  (para que la pantalla de pago pueda mostrar "comprobando..."). */
    val verificando: StateFlow<Boolean> = _verificando.asStateFlow()

    private val _ultimoError = MutableStateFlow<String?>(null)
    /** Último error de compra/verificación, en texto plano para mostrar
     *  en la pantalla de pago. Se limpia solo al intentar otra compra. */
    val ultimoError: StateFlow<String?> = _ultimoError.asStateFlow()

    /**
     * Arranca la conexión con Play Billing. Se llama una sola vez al
     * arrancar la app (ver NubiApplication), igual que Repositorio.iniciar().
     */
    fun iniciar(contexto: Context) {
        if (iniciado) return
        iniciado = true
        contextoApp = contexto.applicationContext

        cliente = BillingClient.newBuilder(contexto.applicationContext)
            .setListener(this)
            // Play exige llamar a esto aunque Nubi solo venda suscripciones
            // (no hay "productos únicos" por ahora).
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .enableAutoServiceReconnection()
            .build()

        cliente.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(resultado: BillingResult) {
                if (resultado.responseCode == BillingResponseCode.OK) {
                    consultarProducto()
                    recuperarComprasPendientes()
                } else {
                    // Si falla AQUÍ, consultarProducto() nunca llega a
                    // llamarse y detalles se queda en null sin que el otro
                    // registro (en consultarProducto) se entere — por eso
                    // hace falta su propio log/error, si no esto también se
                    // queda como un "Cargando los planes…" mudo para siempre.
                    Log.e(TAG, "onBillingSetupFinished falló: ${resultado.responseCode} ${resultado.debugMessage}")
                    _ultimoError.value = "No se pudo conectar con Google Play (${resultado.responseCode}): " +
                        resultado.debugMessage.ifBlank { "sin mensaje" }
                }
            }

            override fun onBillingServiceDisconnected() {
                // enableAutoServiceReconnection() ya reintenta solo la conexión.
            }
        })
    }

    private fun consultarProducto() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(ID_PRODUCTO)
                        .setProductType(ProductType.SUBS)
                        .build()
                )
            )
            .build()

        cliente.queryProductDetailsAsync(params) { resultado, resultadoConsulta ->
            if (resultado.responseCode == BillingResponseCode.OK) {
                val encontrado = resultadoConsulta.productDetailsList.firstOrNull()
                detalles = encontrado
                if (encontrado == null) {
                    // Play respondió OK pero no devolvió "$ID_PRODUCTO": el
                    // botón de comprar se queda en "Cargando los planes…"
                    // para siempre porque detalles nunca deja de ser null.
                    // Causas típicas de esto, de más a menos probable:
                    //  1) La suscripción (o su base plan) no está "Activa"
                    //     en Play Console → Monetizar con Play → Productos.
                    //  2) La app se instaló con `adb install`/botón Run de
                    //     Android Studio en vez de desde una pista de
                    //     prueba de Play (interna/cerrada) — Billing solo
                    //     reconoce builds instaladas desde Play.
                    //  3) La cuenta probadora añadida en "Pruebas de
                    //     licencia" todavía no ha propagado (puede tardar
                    //     unas horas) o no es la cuenta de Google activa en
                    //     el dispositivo.
                    //  4) El ID del producto/los planes aquí no coinciden
                    //     EXACTAMENTE con los de Play Console.
                    Log.w(
                        TAG,
                        "queryProductDetailsAsync OK pero sin '$ID_PRODUCTO' en la respuesta " +
                            "(revisa: suscripción activa en Play Console, build instalada desde una " +
                            "pista de prueba de Play, y que la licencia de prueba ya haya propagado)"
                    )
                    _ultimoError.value = "Play no devolvió el plan \"$ID_PRODUCTO\". Comprueba que la " +
                        "suscripción esté Activa en Play Console y que la app esté instalada desde una " +
                        "pista de prueba de Play (no desde Android Studio/adb)."
                }
            } else {
                Log.e(TAG, "queryProductDetailsAsync falló: ${resultado.responseCode} ${resultado.debugMessage}")
                _ultimoError.value = "Error de Play al consultar planes (${resultado.responseCode}): " +
                    resultado.debugMessage.ifBlank { "sin mensaje" }
            }
        }
    }

    /**
     * Lanza el flujo de compra de Play para un plan (PLAN_MENSUAL,
     * PLAN_TRIMESTRAL o PLAN_ANUAL), con su oferta de prueba gratis si la
     * tiene. No hace nada si todavía no se ha consultado el producto o si
     * Play no devolvió ese plan — llamar siempre desde un botón que solo
     * esté activo cuando detalles != null.
     */
    fun comprar(activity: Activity, planId: String) {
        _ultimoError.value = null
        val producto = detalles ?: return
        val oferta = producto.subscriptionOfferDetails
            ?.firstOrNull { it.basePlanId == planId }
            ?: return

        planSolicitado = planId

        val parametros = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(producto)
                        .setOfferToken(oferta.offerToken)
                        .build()
                )
            )
            .build()

        cliente.launchBillingFlow(activity, parametros)
    }

    /**
     * Vuelve a mirar si Play tiene alguna compra que el servidor no haya
     * verificado todavía (p. ej. se compró y se cerró la app antes de
     * terminar, o se reinstaló en otro móvil). Pensado para un botón
     * "Restaurar compra" en la pantalla de pago.
     */
    fun refrescar() {
        if (!iniciado) return
        recuperarComprasPendientes()
    }

    /**
     * Vuelve a pedirle a Play la lista de planes (consultarProducto()).
     * Para el botón "Reintentar" que aparece en PantallaPaywall cuando
     * detalles se quedó en null con un error — no toca compras, solo la
     * consulta de producto/planes.
     */
    fun reintentarConsultaPlanes() {
        if (!iniciado) return
        _ultimoError.value = null
        consultarProducto()
    }

    private fun recuperarComprasPendientes() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(ProductType.SUBS)
            .build()
        cliente.queryPurchasesAsync(params) { resultado, compras ->
            if (resultado.responseCode == BillingResponseCode.OK) {
                compras.forEach(::procesar)
            }
        }
    }

    override fun onPurchasesUpdated(resultado: BillingResult, compras: MutableList<Purchase>?) {
        if (resultado.responseCode == BillingResponseCode.OK && compras != null) {
            compras.forEach(::procesar)
        } else if (resultado.responseCode == BillingResponseCode.USER_CANCELED) {
            // El usuario cerró el diálogo de Play: no es un error que haya
            // que mostrar como tal, pero sí es una caída del embudo real
            // (empezó el flujo de compra y no llegó a terminarlo).
            EventosFunnel.registrar(
                EventoNombre.COMPRA_FALLIDA,
                mapOf("plan_id" to (planSolicitado ?: ""), "motivo" to "cancelado_por_usuario")
            )
        } else {
            _ultimoError.value = resultado.debugMessage.ifBlank { "Error de Play (${resultado.responseCode})" }
            EventosFunnel.registrar(
                EventoNombre.COMPRA_FALLIDA,
                mapOf(
                    "plan_id" to (planSolicitado ?: ""),
                    "motivo" to "error_play_${resultado.responseCode}",
                    "mensaje_error" to resultado.debugMessage
                )
            )
        }
    }

    private fun procesar(compra: Purchase) {
        if (compra.purchaseState != Purchase.PurchaseState.PURCHASED) return

        // Red de seguridad ante fallos de red: esto reconoce la compra
        // ante Play aunque la verificación de servidor de abajo falle o
        // tarde, para no arriesgarse al reembolso automático de Google a
        // los 3 días. El Edge Function también intenta reconocerla por su
        // cuenta — que uno de los dos lo consiga es suficiente.
        if (!compra.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(compra.purchaseToken)
                .build()
            cliente.acknowledgePurchase(params) { /* la red de seguridad de verdad es el Edge Function */ }
        }

        // Si esta compra es de un plan con prueba gratis, se guarda cuándo
        // acaba (solo para poder avisar "te quedan 3 días" — ver
        // NotificadorPrueba.kt — nunca para decidir acceso, eso lo sigue
        // diciendo premium_hasta del servidor más abajo). Solo se puede
        // saber con certeza aquí, cuando planSolicitado todavía es el plan
        // recién comprado en ESTA sesión: si esta misma compra vuelve a
        // pasar por procesar() desde recuperarComprasPendientes() en un
        // arranque posterior (planSolicitado ya es null entonces), se deja
        // tal cual lo que ya hubiera guardado — ver guardarFinPrueba().
        if (planSolicitado != null) {
            guardarFinPrueba(calcularFinPrueba(compra, planSolicitado))
        }

        // La fuente de verdad del acceso es el servidor: aquí solo se le
        // avisa de la compra para que la verifique contra Google y active
        // premium_hasta. Hasta que responda, el usuario sigue viéndose
        // como bloqueado (no hay desbloqueo optimista en el cliente).
        val productoId = compra.products.firstOrNull() ?: return
        ambito.launch {
            _verificando.value = true
            try {
                verificarEnServidor(compra, productoId, planSolicitado)
                // El servidor ya confirmó la compra contra Google (no es
                // solo que el cliente lo crea) — aquí es donde de verdad
                // se sabe que el trial/la compra se activó.
                EventosFunnel.registrar(
                    EventoNombre.TRIAL_CONFIRMADO,
                    mapOf("plan_id" to (planSolicitado ?: ""), "producto_id" to productoId)
                )
                // Trae el perfil actualizado (premium_hasta) desde
                // Supabase; la pantalla de pago observa Repositorio, así
                // que en cuanto llegue se quita sola el muro de pago.
                Repositorio.sincronizarEnSegundoPlano()
                // Y se reprograma el aviso de "te quedan 3 días" con la
                // fecha que se acaba de guardar arriba (si la hay).
                Repositorio.reprogramarAvisos()
            } catch (e: Exception) {
                _ultimoError.value = e.message ?: "No se pudo verificar la compra"
                EventosFunnel.registrar(
                    EventoNombre.COMPRA_FALLIDA,
                    mapOf(
                        "plan_id" to (planSolicitado ?: ""),
                        "motivo" to "verificacion_servidor",
                        "mensaje_error" to (e.message ?: "")
                    )
                )
            } finally {
                _verificando.value = false
            }
        }
    }

    @Serializable
    private data class CuerpoVerificarCompra(
        val purchaseToken: String,
        val productId: String,
        val basePlanId: String?,
    )

    @Serializable
    private data class RespuestaVerificarCompra(
        val ok: Boolean = false,
        val estado: String? = null,
        val tieneAcceso: Boolean = false,
        // El servidor también manda "premiumHasta" (ISO 8601); aquí no se
        // usa, pero sin declararlo el decodificador fallaba con
        // "Encountered an unknown key 'premiumHasta'" aunque la compra
        // se hubiera verificado bien.
        val premiumHasta: String? = null,
        val error: String? = null,
    )

    private suspend fun verificarEnServidor(compra: Purchase, productId: String, planId: String?) {
        // Compras.iniciar() se llama desde NubiApplication.onCreate(), al
        // mismo tiempo que Repositorio.iniciar() — y Play Billing puede
        // conectarse (onBillingSetupFinished) ANTES de que el plugin Auth
        // de Supabase termine de restaurar la sesión persistida del disco.
        // Sin este await, currentAccessTokenOrNull() podía devolver null
        // en ese ratito aunque el usuario SÍ tuviera sesión iniciada —
        // justo lo que pasaba al reintentar una compra nada más abrir la
        // app ("Sin sesión de Supabase" con una cuenta que sí la tenía).
        // awaitInitialization() espera a que el plugin termine ese primer
        // paso (la misma señal que ya usa MainActivity con sessionStatus)
        // antes de mirar si hay token.
        Supabase.client.auth.awaitInitialization()
        val accessToken = Supabase.client.auth.currentAccessTokenOrNull()
            ?: throw IllegalStateException("Sin sesión de Supabase")

        val cuerpo = CuerpoVerificarCompra(
            purchaseToken = compra.purchaseToken,
            productId = productId,
            basePlanId = planId,
        )

        val respuesta = Supabase.client.functions.invoke("verificar-compra-android") {
            contentType(ContentType.Application.Json)
            bearerAuth(accessToken)
            setBody(Json.encodeToString(CuerpoVerificarCompra.serializer(), cuerpo))
        }

        // ignoreUnknownKeys: que un campo nuevo del servidor nunca vuelva a
        // romper la lectura de una compra ya verificada.
        val resultado = Json { ignoreUnknownKeys = true }.decodeFromString(
            RespuestaVerificarCompra.serializer(),
            respuesta.bodyAsText()
        )
        if (!resultado.ok) {
            throw IllegalStateException(resultado.error ?: "El servidor no pudo verificar la compra")
        }
    }

    // --------------------------------------------- aviso de "3 días" (ver NotificadorPrueba.kt)

    /**
     * ¿Esta compra, para el plan "planId", tiene oferta de prueba gratis?
     * Si la tiene, cuándo acaba — contado desde compra.purchaseTime (el
     * instante real en que Play registró la compra), no desde "ahora": así
     * da la misma fecha sin importar cuándo se llame esto (al comprar, o al
     * recuperar la compra en un arranque posterior).
     */
    private fun calcularFinPrueba(compra: Purchase, planId: String?): Instant? {
        val oferta = detalles?.subscriptionOfferDetails?.firstOrNull { it.basePlanId == planId } ?: return null
        val fases = oferta.pricingPhases.pricingPhaseList
        val hayPrueba = fases.size > 1 && fases.first().priceAmountMicros == 0L
        if (!hayPrueba) return null
        val duracionPrueba = try {
            Period.parse(fases.first().billingPeriod)
        } catch (_: Exception) {
            return null
        }
        return Instant.ofEpochMilli(compra.purchaseTime)
            .atZone(ZoneId.systemDefault())
            .plus(duracionPrueba)
            .toInstant()
    }

    private const val PREFS_PRUEBA = "compras_prueba"
    private const val CLAVE_FIN_PRUEBA = "fin_prueba"

    private fun guardarFinPrueba(instante: Instant?) {
        val editor = contextoApp?.getSharedPreferences(PREFS_PRUEBA, Context.MODE_PRIVATE)?.edit() ?: return
        if (instante == null) editor.remove(CLAVE_FIN_PRUEBA) else editor.putLong(CLAVE_FIN_PRUEBA, instante.toEpochMilli())
        editor.apply()
    }

    /** Fecha de fin de la prueba gratis en curso, o null si no hay ninguna
     *  guardada (nunca hubo prueba, o ya se guardó null al comprar sin
     *  prueba). La usa NotificadorPrueba para programar el aviso. */
    fun vencePruebaAndroid(): Instant? {
        val millis = contextoApp?.getSharedPreferences(PREFS_PRUEBA, Context.MODE_PRIVATE)
            ?.getLong(CLAVE_FIN_PRUEBA, -1L) ?: -1L
        return if (millis <= 0) null else Instant.ofEpochMilli(millis)
    }
}
