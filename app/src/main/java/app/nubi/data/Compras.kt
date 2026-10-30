package app.nubi.data

import android.app.Activity
import android.content.Context
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

    /** ID del producto de suscripción en Play Console ("Nubi Pro"). */
    const val ID_PRODUCTO = "nubi_pro"

    /** IDs de los planes base dentro de ese producto. */
    const val PLAN_MENSUAL = "plan-mensual"
    const val PLAN_TRIMESTRAL = "plan-trimestral"
    const val PLAN_ANUAL = "plan-anual"

    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var iniciado = false
    private lateinit var cliente: BillingClient

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
                detalles = resultadoConsulta.productDetailsList.firstOrNull()
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
            // que mostrar como tal.
        } else {
            _ultimoError.value = resultado.debugMessage.ifBlank { "Error de Play (${resultado.responseCode})" }
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

        // La fuente de verdad del acceso es el servidor: aquí solo se le
        // avisa de la compra para que la verifique contra Google y active
        // premium_hasta. Hasta que responda, el usuario sigue viéndose
        // como bloqueado (no hay desbloqueo optimista en el cliente).
        val productoId = compra.products.firstOrNull() ?: return
        ambito.launch {
            _verificando.value = true
            try {
                verificarEnServidor(compra, productoId, planSolicitado)
                // Trae el perfil actualizado (premium_hasta) desde
                // Supabase; la pantalla de pago observa Repositorio, así
                // que en cuanto llegue se quita sola el muro de pago.
                Repositorio.sincronizarEnSegundoPlano()
            } catch (e: Exception) {
                _ultimoError.value = e.message ?: "No se pudo verificar la compra"
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
        val error: String? = null,
    )

    private suspend fun verificarEnServidor(compra: Purchase, productId: String, planId: String?) {
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

        val resultado = Json.decodeFromString(
            RespuestaVerificarCompra.serializer(),
            respuesta.bodyAsText()
        )
        if (!resultado.ok) {
            throw IllegalStateException(resultado.error ?: "El servidor no pudo verificar la compra")
        }
    }
}
