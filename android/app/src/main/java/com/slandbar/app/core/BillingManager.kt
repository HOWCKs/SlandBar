package com.slandbar.app.core

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Integração real com o Google Play Billing (pagamento único, sem assinatura).
 *
 * Modelo de negócio: o app é gratuito com funcionalidades essenciais para sempre.
 * O Premium é um desbloqueio único que libera widgets extras e personalização
 * avançada. Em builds de debug o Premium fica liberado automaticamente.
 */
class BillingManager(
    context: Context,
    private val onPremiumChanged: (Boolean) -> Unit
) {
    companion object {
        const val PRODUCT_ID = "slandbar_premium"
    }

    private val _premium = MutableStateFlow(false)
    val premium: StateFlow<Boolean> = _premium

    private var product: ProductDetails? = null
    private var connected = false

    private val listener = PurchasesUpdatedListener { result: BillingResult, purchases: List<Purchase>? ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && !purchases.isNullOrEmpty()) {
            purchases.firstOrNull { it.products.contains(PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED }
                ?.let { acknowledge(it) }
        }
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(listener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    fun start() {
        if (connected) return
        billingClient.startConnection(object : BillingClient.StateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    connected = true
                    queryProduct()
                    queryPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                connected = false
            }
        })
    }

    fun endConnection() {
        if (connected) billingClient.endConnection()
        connected = false
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        billingClient.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                product = details.firstOrNull()
            }
        }
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { _, purchases ->
            val owned = purchases.any { it.products.contains(PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED }
            setPremium(owned)
        }
    }

    fun launchPurchase(activity: Activity) {
        val details = product ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        billingClient.launchBillingFlow(activity, params)
    }

    fun restore() = queryPurchases()

    private fun acknowledge(purchase: Purchase) {
        val params = com.android.billingclient.api.AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { _ -> setPremium(true) }
    }

    private fun setPremium(value: Boolean) {
        _premium.value = value
        onPremiumChanged(value)
    }
}
