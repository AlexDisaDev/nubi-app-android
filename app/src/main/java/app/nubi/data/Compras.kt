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

/**
 * Compras dentro de la app (Google Play Billing) para la suscripción
 * Premium de Nubi.
 *
 * IMPORTANTE — esto NO decide si el usuario puede usar la app. Igual que
 * con el código de regalo, quien manda es el servidor: Perfil.puedeUsarApp()
 * lee "premium_hasta"/"premium_fuente" de Supabase (ver Modelos.kt). Este
 * archivo solo habla con Play para cobrar y reconocer la compra; falta
 * todavía un paso de BACKEND (pendiente, no hecho aquí): verificar el
 * purchaseToken contra la Google Play Developer API y, solo entonces,
 * escribir premium_hasta/premium_fuente en "perfiles". Hasta que exista ese
 * paso, una compra se reconoce (para no arriesgar el dinero del usuario a
 * un reembolso automático) pero todavía no desbloquea nada dentro de Nubi.
 *
 * IDs: PLACEHOLDERS — tienen que coincidir EXACTAMENTE (mayúsculas y todo)
 * con el ID del producto y los IDs de los planes base que crees en Play
 * Console → Monetizar con Play → Productos → Suscripciones. Si allí les
 * pones otro nombre, cámbialos también aquí.
 */
object Compras : PurchasesUpdatedListener {

    /** ID del producto de suscripción en Play Console. */
    const val ID_PRODUCTO = "nubi_premium"

    /** IDs de los planes base dentro de ese producto. */
    const val PLAN_MENSUAL = "mensual"
    const val PLAN_TRIMESTRAL = "trimestral"
    const val PLAN_ANUAL = "anual"

    private var iniciado = false
    private lateinit var cliente: BillingClient

    /** Detalles del producto ya consultados a Play. Null hasta que termine la consulta (o si falla). */
    var detalles: ProductDetails? = null
        private set

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
                // Si no es OK (p.ej. Play Store no disponible en el
                // dispositivo), "detalles" se queda en null y comprar() no
                // hace nada — TODO: avisar en la interfaz cuando haya
                // pantalla de suscripción.
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
     * PLAN_TRIMESTRAL o PLAN_ANUAL). No hace nada si todavía no se ha
     * consultado el producto o si Play no devolvió ese plan — llamar
     * siempre desde un botón que solo esté activo cuando detalles != null.
     */
    fun comprar(activity: Activity, planId: String) {
        val producto = detalles ?: return
        val oferta = producto.subscriptionOfferDetails
            ?.firstOrNull { it.basePlanId == planId }
            ?: return

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
     * Compras que Play ya tiene registradas pero que la app no ha procesado
     * todavía (p.ej. se compró, se cerró la app antes de confirmar, o se
     * reinstaló). Se consulta cada vez que arranca la conexión.
     */
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
        }
        // BillingResponseCode.USER_CANCELED: el usuario cerró el diálogo de
        // Play, no es un error que haya que mostrar.
        // TODO: mostrar/registrar cualquier otro código cuando haya
        // pantalla de suscripción en Ajustes.
    }

    private fun procesar(compra: Purchase) {
        if (compra.purchaseState != Purchase.PurchaseState.PURCHASED) return

        // TODO (backend pendiente, ver cabecera del archivo): mandar
        // compra.purchaseToken a Supabase para verificarlo server-side y
        // actualizar premium_hasta/premium_fuente en "perfiles".

        if (!compra.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(compra.purchaseToken)
                .build()
            cliente.acknowledgePurchase(params) {
                // TODO: reintentar si billingResult no es OK — de momento,
                // si falla aquí, la compra sigue pendiente de confirmar y
                // Play la reenviará en el próximo recuperarComprasPendientes().
            }
        }
    }
}
