package com.utilityhub.app.data

import com.utilityhub.logic.entitlement.EntitlementLedger
import com.utilityhub.logic.entitlement.EntitlementPolicy
import com.utilityhub.logic.entitlement.EntitlementSnapshot
import com.utilityhub.logic.entitlement.Reservation
import com.utilityhub.logic.entitlement.ReserveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * The single entitlement entry point used by every metered tool (see FileToolViewModel).
 * reserve() before work, finalizeSuccess() only after a validated result, release() on cancel/failure.
 */
class ToolEntitlementManager(private val ledger: EntitlementLedger) {
    private val _snapshot = MutableStateFlow<EntitlementSnapshot?>(null)
    val snapshot: StateFlow<EntitlementSnapshot?> = _snapshot
    val policy: EntitlementPolicy get() = ledger.policy()

    suspend fun refresh() = withContext(Dispatchers.IO) { _snapshot.value = ledger.snapshot() }

    suspend fun reserve(): ReserveResult = withContext(Dispatchers.IO) { ledger.reserve() }

    suspend fun finalizeSuccess(reservation: Reservation): Boolean = withContext(Dispatchers.IO) {
        ledger.finalizeSuccess(reservation).also { _snapshot.value = ledger.snapshot() }
    }

    fun release(reservation: Reservation) = ledger.release(reservation)
}
