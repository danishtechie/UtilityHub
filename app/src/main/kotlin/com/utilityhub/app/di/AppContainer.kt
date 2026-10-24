package com.utilityhub.app.di

import android.app.Application
import androidx.room.Room
import com.google.firebase.FirebaseApp
import com.utilityhub.app.core.FileAccess
import com.utilityhub.app.core.OutputStore
import com.utilityhub.app.core.auth.AuthProvider
import com.utilityhub.app.core.auth.AuthState
import com.utilityhub.app.core.auth.FirebaseAuthProvider
import com.utilityhub.app.core.auth.LocalAuthProvider
import com.utilityhub.app.core.billing.BillingGateway
import com.utilityhub.app.core.billing.UnconfiguredBillingGateway
import com.utilityhub.app.data.HistoryRepository
import com.utilityhub.app.data.SettingsRepository
import com.utilityhub.app.data.SharedPrefsUsageStorage
import com.utilityhub.app.data.ToolEntitlementManager
import com.utilityhub.app.data.db.AppDatabase
import com.utilityhub.logic.entitlement.EntitlementLedger
import com.utilityhub.logic.entitlement.EntitlementPolicy
import com.utilityhub.logic.entitlement.PaidAccessProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FreeForAllPaidAccessProvider : PaidAccessProvider {
    override fun hasPaidAccess(): Boolean = true
}

class AppContainer(val app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val fileAccess = FileAccess(app)
    val outputStore = OutputStore(app)
    val settings = SettingsRepository(app)
    val history = HistoryRepository(app)
    val billing: BillingGateway = UnconfiguredBillingGateway()
    val auth: AuthProvider = if (FirebaseApp.getApps(app).isNotEmpty()) {
        FirebaseAuthProvider()
    } else {
        LocalAuthProvider(app)
    }
    val db: AppDatabase by lazy { Room.databaseBuilder(app, AppDatabase::class.java, "utilityhub.db").build() }
    val policy = EntitlementPolicy()
    val paidAccessProvider = FreeForAllPaidAccessProvider()
    val usageStorage = SharedPrefsUsageStorage(app, policy) {
        (auth.state.value as? AuthState.SignedIn)?.uid ?: "guest"
    }
    val entitlement = ToolEntitlementManager(
        EntitlementLedger(policy, usageStorage, paidAccessProvider),
    )
}
