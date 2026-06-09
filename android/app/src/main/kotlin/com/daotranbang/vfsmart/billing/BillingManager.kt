package com.daotranbang.vfsmart.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps Google Play Billing (v7) for the single auto-renewing "premium" subscription.
 *
 * Lifecycle: process-scoped [Singleton]. Connection is established lazily on the first
 * [start] call (from [com.daotranbang.vfsmart.ui.MainActivity]) and re-established
 * automatically if the Play service disconnects. Entitlement is restored on every
 * connect via [queryPurchasesAsync], so the app never trusts a cached flag alone.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : PurchasesUpdatedListener, BillingClientStateListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isPremium = MutableStateFlow(false)
    /** True while the "premium" subscription is active (purchased + acknowledged). */
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    /** Loaded subscription product, or null until Play returns it. Drives price display. */
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    private var reconnectBackoffMs = INITIAL_RECONNECT_DELAY_MS

    /** Idempotent — safe to call from every Activity.onCreate. */
    fun start() {
        if (billingClient.connectionState == BillingClient.ConnectionState.CONNECTED ||
            billingClient.connectionState == BillingClient.ConnectionState.CONNECTING
        ) {
            return
        }
        billingClient.startConnection(this)
    }

    // region BillingClientStateListener

    override fun onBillingSetupFinished(result: BillingResult) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            Log.i(TAG, "Billing connected")
            reconnectBackoffMs = INITIAL_RECONNECT_DELAY_MS
            scope.launch {
                queryProductDetails()
                refreshPurchases()
            }
        } else {
            Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
        }
    }

    override fun onBillingServiceDisconnected() {
        Log.w(TAG, "Billing disconnected — retrying in ${reconnectBackoffMs}ms")
        scope.launch {
            delay(reconnectBackoffMs)
            reconnectBackoffMs = (reconnectBackoffMs * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
            start()
        }
    }

    // endregion

    private suspend fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_SUBSCRIPTION_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        val result = billingClient.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val details = result.productDetailsList?.firstOrNull()
            _productDetails.value = details
            if (details == null) {
                Log.w(TAG, "No product details for '$PREMIUM_SUBSCRIPTION_ID' — check Play Console")
            }
        } else {
            Log.w(TAG, "queryProductDetails failed: ${result.billingResult.debugMessage}")
        }
    }

    /** Re-reads owned subscriptions from Play and updates [isPremium]. */
    suspend fun refreshPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val result = billingClient.queryPurchasesAsync(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val active = result.purchasesList.any { purchase ->
                purchase.products.contains(PREMIUM_SUBSCRIPTION_ID) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            _isPremium.value = active
            // Acknowledge anything bought outside this session (e.g. restored on a new device).
            result.purchasesList.forEach { acknowledgeIfNeeded(it) }
        }
    }

    /**
     * Launches the Play subscription purchase sheet. Must be called from an [Activity].
     * Returns false if product details aren't loaded yet (caller should retry shortly).
     */
    fun launchPurchaseFlow(activity: Activity): Boolean {
        val details = _productDetails.value ?: run {
            Log.w(TAG, "launchPurchaseFlow before product loaded")
            return false
        }
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: run {
            Log.w(TAG, "Subscription has no offer token")
            return false
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()
        val result = billingClient.launchBillingFlow(activity, params)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    // region PurchasesUpdatedListener

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.i(TAG, "Purchase canceled by user")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // Out of sync — re-query the source of truth.
                scope.launch { refreshPurchases() }
            }
            else -> Log.w(TAG, "Purchase update failed: ${result.debugMessage}")
        }
    }

    // endregion

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(PREMIUM_SUBSCRIPTION_ID)) return
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            _isPremium.value = true
            scope.launch { acknowledgeIfNeeded(purchase) }
        }
    }

    /** Acknowledge within Play's 3-day window, or the purchase is auto-refunded. */
    private suspend fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        val result = billingClient.acknowledgePurchase(params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "acknowledgePurchase failed: ${result.debugMessage}")
        }
    }

    companion object {
        private const val TAG = "BillingManager"

        /** Play Console subscription product ID — rename to match your real product. */
        const val PREMIUM_SUBSCRIPTION_ID = "premium"

        private const val INITIAL_RECONNECT_DELAY_MS = 1_000L
        private const val MAX_RECONNECT_DELAY_MS = 60_000L
    }
}
