package com.zahidcodes.copynest.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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

        val DRIVE_SYNC_ENABLED = booleanPreferencesKey("drive_sync_enabled")
        val DRIVE_ACCOUNT_EMAIL = stringPreferencesKey("drive_account_email")
        val DRIVE_FOLDER_ID = stringPreferencesKey("drive_folder_id")
        val DRIVE_LAST_SYNC_TIME = longPreferencesKey("drive_last_sync_time")
        val DRIVE_LAST_SYNC_SUMMARY = stringPreferencesKey("drive_last_sync_summary")
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

    val driveSyncEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[DRIVE_SYNC_ENABLED] ?: false
        }

    val driveAccountEmailFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[DRIVE_ACCOUNT_EMAIL] ?: ""
        }

    val driveFolderIdFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[DRIVE_FOLDER_ID] ?: ""
        }

    val driveLastSyncTimeFlow: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[DRIVE_LAST_SYNC_TIME] ?: 0L
        }

    val driveLastSyncSummaryFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[DRIVE_LAST_SYNC_SUMMARY] ?: ""
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

    suspend fun isDriveSyncEnabled(): Boolean {
        return try {
            driveSyncEnabledFlow.first()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun setDriveSyncConnection(enabled: Boolean, accountEmail: String, folderId: String = "") {
        context.dataStore.edit { preferences ->
            preferences[DRIVE_SYNC_ENABLED] = enabled
            preferences[DRIVE_ACCOUNT_EMAIL] = accountEmail
            if (folderId.isNotBlank() || !enabled) {
                preferences[DRIVE_FOLDER_ID] = folderId
            }
            if (!enabled) {
                preferences[DRIVE_LAST_SYNC_SUMMARY] = "Disconnected"
            }
        }
    }

    suspend fun getDriveAccountEmail(): String {
        return try {
            driveAccountEmailFlow.first()
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun getDriveFolderId(): String {
        return try {
            driveFolderIdFlow.first()
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun setDriveFolderId(folderId: String) {
        context.dataStore.edit { preferences ->
            preferences[DRIVE_FOLDER_ID] = folderId
        }
    }

    suspend fun updateDriveLastSync(timestamp: Long, summary: String) {
        context.dataStore.edit { preferences ->
            preferences[DRIVE_LAST_SYNC_TIME] = timestamp
            preferences[DRIVE_LAST_SYNC_SUMMARY] = summary
        }
    }
}
