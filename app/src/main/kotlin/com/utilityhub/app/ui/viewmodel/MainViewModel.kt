package com.utilityhub.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utilityhub.app.core.auth.AuthState
import com.utilityhub.app.data.AppSettings
import com.utilityhub.app.data.ThemeMode
import com.utilityhub.app.di.AppContainer
import com.utilityhub.logic.catalog.ToolCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val history = c.history.entries
    val entitlement = c.entitlement.snapshot
    val authState: StateFlow<AuthState> = c.auth.state
    val billingConfigured: Boolean get() = c.billing.isConfigured

    init {
        viewModelScope.launch {
            c.auth.state.collect {
                c.entitlement.refresh()
            }
        }
    }

    private val _exploreCategory = MutableStateFlow<ToolCategory?>(null)
    val exploreCategory: StateFlow<ToolCategory?> = _exploreCategory
    fun selectCategory(category: ToolCategory?) { _exploreCategory.value = category }

    private val _tempBytes = MutableStateFlow(0L)
    val tempBytes: StateFlow<Long> = _tempBytes

    fun refreshTempSize() { viewModelScope.launch { _tempBytes.value = withContext(Dispatchers.IO) { c.outputStore.sizeBytes() } } }
    fun clearTemp() { viewModelScope.launch { withContext(Dispatchers.IO) { c.outputStore.clearAll() }; _tempBytes.value = 0L } }
    fun clearHistory() { viewModelScope.launch { c.history.clear() } }
    fun setTheme(mode: ThemeMode) { viewModelScope.launch { c.settings.setTheme(mode) } }
    fun finishOnboarding() { viewModelScope.launch { c.settings.setOnboardingDone() } }
    fun finishPermissionsPrompt() { viewModelScope.launch { c.settings.setPermissionsPrompted() } }
    fun toggleFavorite(id: String) { viewModelScope.launch { c.settings.toggleFavorite(id) } }
}
