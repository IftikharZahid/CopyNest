package com.zahidcodes.copynets.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

class UserPreferences(private val context: Context) {
    companion object {
        val AUTO_CAPTURE = booleanPreferencesKey("auto_capture")
        val THEME_MODE = stringPreferencesKey("theme_mode") // SYSTEM, LIGHT, DARK
    }

    val autoCaptureFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_CAPTURE] ?: true
        }

    val themeModeFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[THEME_MODE] ?: "SYSTEM"
        }

    suspend fun setAutoCapture(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_CAPTURE] = enabled
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }
}
