package com.utilityhub.logic

import com.utilityhub.logic.entitlement.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.spec.SecretKeySpec

private class MemoryStorage(var state: UsageState = UsageState()) : UsageStorage {
    override fun load() = state
    override fun save(state: UsageState) { this.state = state }
}

class EntitlementTest {
    private val policy = EntitlementPolicy()

    private fun granted(r: ReserveResult) = (r as ReserveResult.Granted).reservation

    @Test fun `new installation has five free operations`() {
        val ledger = EntitlementLedger(policy, MemoryStorage())
        val s = ledger.snapshot()
        assertEquals(5, s.freeRemaining)
        assertTrue(s.canStart)
        assertFalse(s.hasPaidAccess)
    }

    @Test fun `five successful operations exhaust the allowance and the sixth is denied`() {
        val ledger = EntitlementLedger(policy, MemoryStorage())
        repeat(5) { assertTrue(ledger.finalizeSuccess(granted(ledger.reserve()))) }
        assertEquals(0, ledger.snapshot().freeRemaining)
        assertEquals(ReserveResult.Denied(DenyReason.FREE_ALLOWANCE_EXHAUSTED), ledger.reserve())
    }

    @Test fun `failed or cancelled operations do not consume a use`() {
        val ledger = EntitlementLedger(policy, MemoryStorage())
        repeat(20) { ledger.release(granted(ledger.reserve())) }
        assertEquals(5, ledger.snapshot().freeRemaining)
    }

    @Test fun `duplicate completion callbacks consume only once`() {
        val ledger = EntitlementLedger(policy, MemoryStorage())
        val r = granted(ledger.reserve())
        assertTrue(ledger.finalizeSuccess(r))
        assertFalse(ledger.finalizeSuccess(r))
        assertFalse(ledger.finalizeSuccess(r))
        assertEquals(4, ledger.snapshot().freeRemaining)
    }

    @Test fun `release after finalize and finalize after release are harmless`() {
        val ledger = EntitlementLedger(policy, MemoryStorage())
        val a = granted(ledger.reserve()); ledger.release(a); assertFalse(ledger.finalizeSuccess(a))
        val b = granted(ledger.reserve()); assertTrue(ledger.finalizeSuccess(b)); ledger.release(b)
        assertEquals(4, ledger.snapshot().freeRemaining)
    }

    @Test fun `in-flight reservations count against the allowance`() {
        val ledger = EntitlementLedger(policy, MemoryStorage())
        val held = (1..5).map { granted(ledger.reserve()) }
        assertTrue(ledger.reserve() is ReserveResult.Denied)
        ledger.release(held[0])
        assertTrue(ledger.reserve() is ReserveResult.Granted)
    }

    @Test fun `concurrent starts cannot exceed the allowance`() {
        val ledger = EntitlementLedger(policy, MemoryStorage())
        val pool = Executors.newFixedThreadPool(16)
        val start = CountDownLatch(1)
        val granted = AtomicInteger()
        val tasks = (1..100).map {
            pool.submit { start.await(); if (ledger.reserve() is ReserveResult.Granted) granted.incrementAndGet() }
        }
        start.countDown(); tasks.forEach { it.get() }; pool.shutdown()
        assertEquals(5, granted.get())
    }

    @Test fun `process death mid-operation consumes nothing`() {
        val storage = MemoryStorage()
        val first = EntitlementLedger(policy, storage)
        granted(first.reserve())                         // never finalized: process "dies"
        val second = EntitlementLedger(policy, storage)  // restart
        assertEquals(5, second.snapshot().freeRemaining)
    }

    @Test fun `verified paid access is never metered`() {
        val ledger = EntitlementLedger(policy, MemoryStorage(), object : PaidAccessProvider { override fun hasPaidAccess() = true })
        repeat(20) { assertFalse(ledger.finalizeSuccess(granted(ledger.reserve()))) }
        assertTrue(ledger.snapshot().canStart)
    }

    @Test fun `default configuration has no paid access so nothing can fake payment`() {
        assertFalse(NoPaidAccess.hasPaidAccess())
    }

    private val signer = HmacSigner(SecretKeySpec("test-key-test-key-test-key-1234".toByteArray(), "HmacSHA256"))

    @Test fun `signed state round trips`() {
        val state = UsageState(3, listOf("a", "b"))
        val decoded = UsageCodec.decode(UsageCodec.encode(state, signer), signer)
        assertEquals(DecodeResult.Ok(state), decoded)
    }

    @Test fun `edited counter is detected as tampered`() {
        val encoded = UsageCodec.encode(UsageState(4, listOf("x")), signer)
        assertEquals(DecodeResult.Tampered, UsageCodec.decode(encoded.replace("v1;4;", "v1;0;"), signer))
        assertEquals(DecodeResult.Tampered, UsageCodec.decode("garbage", signer))
        assertEquals(DecodeResult.Missing, UsageCodec.decode(null, signer))
    }
}
