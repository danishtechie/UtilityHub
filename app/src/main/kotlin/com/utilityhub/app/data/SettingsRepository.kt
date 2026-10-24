package com.utilityhub.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val onboardingDone: Boolean = false,
    val permissionsPrompted: Boolean = false,
    val favorites: Set<String> = emptySet(),
    val loaded: Boolean = false,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val onboardingKey = booleanPreferencesKey("onboarding_done")
    private val permissionsKey = booleanPreferencesKey("permissions_prompted")
    private val favoritesKey = stringSetPreferencesKey("favorites")

    val settings: Flow<AppSettings> = context.dataStore.data.map { p: Preferences ->
        AppSettings(
            theme = p[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            onboardingDone = p[onboardingKey] ?: false,
            permissionsPrompted = p[permissionsKey] ?: false,
            favorites = p[favoritesKey] ?: emptySet(),
            loaded = true,
        )
    }

    suspend fun setTheme(mode: ThemeMode) { context.dataStore.edit { it[themeKey] = mode.name } }
    suspend fun setOnboardingDone() { context.dataStore.edit { it[onboardingKey] = true } }
    suspend fun setPermissionsPrompted() { context.dataStore.edit { it[permissionsKey] = true } }
    suspend fun toggleFavorite(toolId: String) {
        context.dataStore.edit { p ->
            val current = p[favoritesKey] ?: emptySet()
            p[favoritesKey] = if (toolId in current) current - toolId else current + toolId
        }
    }
}
