package com.utilityhub.logic.entitlement

import java.util.UUID
import javax.crypto.Mac
import javax.crypto.SecretKey

/** Central, configurable policy. Change here to support promotions, other limits, etc. */
data class EntitlementPolicy(
    val freeOperations: Int = 5,
    val maxBatchItemsPerOperation: Int = 50,
    val trackedFinalizedIds: Int = 64,
)

data class UsageState(val consumed: Int = 0, val finalizedIds: List<String> = emptyList())

/** Blocking storage; implementations must be fast (small preferences file). */
interface UsageStorage {
    fun load(): UsageState
    fun save(state: UsageState)
}

/**
 * Source of truth for paid access. The default has no billing, so it is always false.
 * A future implementation must verify purchases (Play Billing / server) - never a plain local flag.
 */
interface PaidAccessProvider {
    fun hasPaidAccess(): Boolean
}

object NoPaidAccess : PaidAccessProvider {
    override fun hasPaidAccess(): Boolean = false
}

class Reservation internal constructor(val id: String, internal val metered: Boolean)

enum class DenyReason { FREE_ALLOWANCE_EXHAUSTED }

sealed interface ReserveResult {
    data class Granted(val reservation: Reservation) : ReserveResult
    data class Denied(val reason: DenyReason) : ReserveResult
}

data class EntitlementSnapshot(val freeTotal: Int, val freeRemaining: Int, val hasPaidAccess: Boolean) {
    val canStart: Boolean get() = hasPaidAccess || freeRemaining > 0
}

/**
 * Atomic reserve -> finalize/release accounting.
 *  - reserve():         checks consumed + in-flight < allowance, so parallel starts cannot bypass the limit.
 *  - finalizeSuccess(): consumes exactly one use, idempotent per reservation id.
 *  - release():         used for cancel / failure; consumes nothing.
 * In-flight reservations live in memory only: if the process dies mid-operation nothing is consumed.
 */
class EntitlementLedger(
    private val policy: EntitlementPolicy,
    private val storage: UsageStorage,
    private val access: PaidAccessProvider = NoPaidAccess,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val lock = Any()
    private val inFlight = HashSet<String>()

    fun policy(): EntitlementPolicy = policy

    fun snapshot(): EntitlementSnapshot = synchronized(lock) {
        val consumed = storage.load().consumed
        EntitlementSnapshot(policy.freeOperations, (policy.freeOperations - consumed).coerceAtLeast(0), access.hasPaidAccess())
    }

    fun reserve(): ReserveResult = synchronized(lock) {
        if (access.hasPaidAccess()) return ReserveResult.Granted(Reservation(newId(), metered = false))
        val consumed = storage.load().consumed
        if (consumed + inFlight.size >= policy.freeOperations) {
            return ReserveResult.Denied(DenyReason.FREE_ALLOWANCE_EXHAUSTED)
        }
        val reservation = Reservation(newId(), metered = true)
        inFlight += reservation.id
        ReserveResult.Granted(reservation)
    }

    /** @return true only if this call actually consumed a free use. */
    fun finalizeSuccess(reservation: Reservation): Boolean = synchronized(lock) {
        if (!reservation.metered) return false
        if (!inFlight.remove(reservation.id)) return false
        val state = storage.load()
        if (reservation.id in state.finalizedIds) return false
        storage.save(
            UsageState(
                consumed = (state.consumed + 1).coerceAtMost(policy.freeOperations),
                finalizedIds = (state.finalizedIds + reservation.id).takeLast(policy.trackedFinalizedIds),
            ),
        )
        true
    }

    fun release(reservation: Reservation) {
        synchronized(lock) { inFlight.remove(reservation.id) }
    }
}

/** Signs/verifies the persisted counter so casual edits of the preferences file are detected. Not tamper-proof. */
interface StateSigner {
    fun sign(payload: String): String
    fun verify(payload: String, tag: String): Boolean
}

class HmacSigner(private val key: SecretKey) : StateSigner {
    override fun sign(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    override fun verify(payload: String, tag: String): Boolean =
        java.security.MessageDigest.isEqual(sign(payload).toByteArray(Charsets.UTF_8), tag.toByteArray(Charsets.UTF_8))
}

sealed interface DecodeResult {
    data class Ok(val state: UsageState) : DecodeResult
    data object Missing : DecodeResult
    data object Tampered : DecodeResult
}

object UsageCodec {
    private const val VERSION = "v1"

    fun encode(state: UsageState, signer: StateSigner): String {
        val payload = "$VERSION;${state.consumed};${state.finalizedIds.joinToString(",")}"
        return "$payload;${signer.sign(payload)}"
    }

    fun decode(raw: String?, signer: StateSigner): DecodeResult {
        if (raw.isNullOrBlank()) return DecodeResult.Missing
        val parts = raw.split(';')
        if (parts.size != 4 || parts[0] != VERSION) return DecodeResult.Tampered
        val payload = parts.take(3).joinToString(";")
        if (!signer.verify(payload, parts[3])) return DecodeResult.Tampered
        val consumed = parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: return DecodeResult.Tampered
        return DecodeResult.Ok(UsageState(consumed, parts[2].split(',').filter { it.isNotBlank() }))
    }
}
