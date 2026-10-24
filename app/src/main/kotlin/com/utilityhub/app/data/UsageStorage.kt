package com.utilityhub.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.utilityhub.logic.entitlement.DecodeResult
import com.utilityhub.logic.entitlement.EntitlementPolicy
import com.utilityhub.logic.entitlement.HmacSigner
import com.utilityhub.logic.entitlement.StateSigner
import com.utilityhub.logic.entitlement.UsageCodec
import com.utilityhub.logic.entitlement.UsageState
import com.utilityhub.logic.entitlement.UsageStorage
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Persists the free-operation counter per user account locally.
 * Each account receives its own independent 5 free operations allowance.
 */
class SharedPrefsUsageStorage(
    context: Context,
    private val policy: EntitlementPolicy,
    private val userIdSupplier: () -> String = { "" },
) : UsageStorage {
    private val prefs = context.getSharedPreferences("usage_v2", Context.MODE_PRIVATE)
    private val signer: StateSigner = createSigner()

    private fun userKey(): String {
        val uid = userIdSupplier().trim()
        return if (uid.isNotBlank()) "state_$uid" else "state_guest"
    }

    override fun load(): UsageState = when (val r = UsageCodec.decode(prefs.getString(userKey(), null), signer)) {
        is DecodeResult.Ok -> r.state
        DecodeResult.Missing -> UsageState()
        DecodeResult.Tampered -> UsageState(consumed = policy.freeOperations)
    }

    override fun save(state: UsageState) {
        prefs.edit().putString(userKey(), UsageCodec.encode(state, signer)).commit()
    }

    private fun createSigner(): StateSigner = try {
        HmacSigner(keystoreKey())
    } catch (e: Exception) {
        PassthroughSigner
    }

    private fun keystoreKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN).build())
        return generator.generateKey()
    }

    private object PassthroughSigner : StateSigner {
        override fun sign(payload: String) = "nosig"
        override fun verify(payload: String, tag: String) = tag == "nosig"
    }

    private companion object {
        const val ALIAS = "utilityhub_usage_hmac"
    }
}
