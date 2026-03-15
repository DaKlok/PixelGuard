package com.daklok.pixelguard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "applock_prefs")

class AppLockPreferences(private val context: Context) {

    companion object {
        private val LOCKED_APPS_KEY = stringSetPreferencesKey("locked_apps")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
        private val UNLOCK_METHOD_KEY = stringPreferencesKey("unlock_method")
        private val CUSTOM_PIN_KEY = stringPreferencesKey("custom_pin")
    }

    val lockedApps: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            preferences[LOCKED_APPS_KEY] ?: emptySet()
        }
        
    val themeMode: Flow<String> = context.dataStore.data
        .map { it[THEME_MODE_KEY] ?: "System" }
        
    val dynamicColor: Flow<Boolean> = context.dataStore.data
        .map { it[DYNAMIC_COLOR_KEY] ?: true }
        
    val unlockMethod: Flow<String> = context.dataStore.data
        .map { it[UNLOCK_METHOD_KEY] ?: "PIN" }
        
    val customPin: Flow<String?> = context.dataStore.data
        .map { it[CUSTOM_PIN_KEY] }

    suspend fun addLockedApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val currentApps = preferences[LOCKED_APPS_KEY] ?: emptySet()
            preferences[LOCKED_APPS_KEY] = currentApps + packageName
        }
    }

    suspend fun removeLockedApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val currentApps = preferences[LOCKED_APPS_KEY] ?: emptySet()
            preferences[LOCKED_APPS_KEY] = currentApps - packageName
        }
    }
    
    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[THEME_MODE_KEY] = mode }
    }
    
    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[DYNAMIC_COLOR_KEY] = enabled }
    }
    
    suspend fun setUnlockMethod(method: String) {
        context.dataStore.edit { it[UNLOCK_METHOD_KEY] = method }
    }
    
    suspend fun setCustomPin(pin: String) {
        context.dataStore.edit { it[CUSTOM_PIN_KEY] = pin }
    }
}
