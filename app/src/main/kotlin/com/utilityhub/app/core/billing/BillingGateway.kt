package com.utilityhub.app.core.billing

import android.app.Activity
import com.utilityhub.logic.entitlement.PaidAccessProvider

/** Real, configured product shown on the paywall. Never invent prices - they must come from Play Billing. */
data class ProductInfo(val id: String, val title: String, val formattedPrice: String, val description: String, val renewalTerms: String?)

sealed interface RestoreResult {
    data object NotConfigured : RestoreResult
    data class Restored(val count: Int) : RestoreResult
    data class Failed(val message: String) : RestoreResult
}

/**
 * Integration boundary for Google Play Billing (not implemented on purpose).
 * Until a Play Console app with real products exists this stays [UnconfiguredBillingGateway]:
 * no purchase can be started and no payment can appear to succeed.
 */
interface BillingGateway {
    val isConfigured: Boolean
    suspend fun loadProducts(): List<ProductInfo>
    suspend fun launchPurchase(activity: Activity, productId: String)
    suspend fun restorePurchases(): RestoreResult
    /** Verified entitlement derived from Play purchase state (and later a server). Never a local boolean. */
    val access: PaidAccessProvider
}

class UnconfiguredBillingGateway : BillingGateway {
    override val isConfigured = false
    override suspend fun loadProducts(): List<ProductInfo> = emptyList()
    override suspend fun launchPurchase(activity: Activity, productId: String) =
        throw UnsupportedOperationException("Billing is not configured.")
    override suspend fun restorePurchases(): RestoreResult = RestoreResult.NotConfigured
    override val access: PaidAccessProvider = com.utilityhub.logic.entitlement.NoPaidAccess
}
