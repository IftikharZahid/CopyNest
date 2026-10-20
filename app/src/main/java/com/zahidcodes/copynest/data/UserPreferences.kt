package com.zahidcodes.copynest.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

class UserPreferences(private val context: Context) {
    companion object {
        val AUTO_CAPTURE = booleanPreferencesKey("auto_capture")
        val THEME_MODE = stringPreferencesKey("theme_mode") // SYSTEM, LIGHT, DARK
        val PENDING_MANDATORY_UPDATE_VERSION = intPreferencesKey("pending_mandatory_update_version")
        val MIN_SUPPORTED_VERSION_CODE = intPreferencesKey("min_supported_version_code")
        val HAS_SEEDED_DEFAULT_CLIPS = booleanPreferencesKey("has_seeded_default_clips")
    }

    val autoCaptureFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_CAPTURE] ?: true
        }

    val themeModeFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[THEME_MODE] ?: "SYSTEM"
        }

    val pendingMandatoryUpdateVersionFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PENDING_MANDATORY_UPDATE_VERSION] ?: 0
        }

    val minSupportedVersionCodeFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[MIN_SUPPORTED_VERSION_CODE] ?: 0
        }

    suspend fun hasSeededDefaultClips(): Boolean {
        return try {
            context.dataStore.data.map { it[HAS_SEEDED_DEFAULT_CLIPS] ?: false }.first()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun setHasSeededDefaultClips(seeded: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HAS_SEEDED_DEFAULT_CLIPS] = seeded
        }
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

    suspend fun getPendingMandatoryUpdateVersion(): Int {
        return try {
            pendingMandatoryUpdateVersionFlow.first()
        } catch (_: Exception) {
            0
        }
    }

    suspend fun setPendingMandatoryUpdateVersion(versionCode: Int) {
        context.dataStore.edit { preferences ->
            preferences[PENDING_MANDATORY_UPDATE_VERSION] = versionCode
        }
    }

    suspend fun getMinSupportedVersionCode(): Int {
        return try {
            minSupportedVersionCodeFlow.first()
        } catch (_: Exception) {
            0
        }
    }

    suspend fun setMinSupportedVersionCode(versionCode: Int) {
        context.dataStore.edit { preferences ->
            preferences[MIN_SUPPORTED_VERSION_CODE] = versionCode
        }
    }
}

