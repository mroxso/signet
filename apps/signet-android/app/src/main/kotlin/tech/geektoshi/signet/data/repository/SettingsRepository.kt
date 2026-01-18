package tech.geektoshi.signet.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val DAEMON_URL = stringPreferencesKey("daemon_url")
        val DEFAULT_TRUST_LEVEL = stringPreferencesKey("default_trust_level")
        val BATTERY_PROMPT_SHOWN = booleanPreferencesKey("battery_prompt_shown")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val LOCK_TIMEOUT_MINUTES = intPreferencesKey("lock_timeout_minutes")
        val LAST_ACTIVITY_TIMESTAMP = longPreferencesKey("last_activity_timestamp")
    }

    val daemonUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[Keys.DAEMON_URL] ?: ""
    }

    val defaultTrustLevel: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[Keys.DEFAULT_TRUST_LEVEL] ?: "reasonable"
    }

    val batteryPromptShown: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.BATTERY_PROMPT_SHOWN] ?: false
    }

    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.APP_LOCK_ENABLED] ?: false
    }

    val lockTimeoutMinutes: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[Keys.LOCK_TIMEOUT_MINUTES] ?: 1
    }

    val lastActivityTimestamp: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[Keys.LAST_ACTIVITY_TIMESTAMP] ?: 0L
    }

    suspend fun setDaemonUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.DAEMON_URL] = url
        }
    }

    suspend fun setDefaultTrustLevel(level: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.DEFAULT_TRUST_LEVEL] = level
        }
    }

    suspend fun setBatteryPromptShown(shown: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.BATTERY_PROMPT_SHOWN] = shown
        }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.APP_LOCK_ENABLED] = enabled
        }
    }

    suspend fun setLockTimeoutMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.LOCK_TIMEOUT_MINUTES] = minutes
        }
    }

    suspend fun setLastActivityTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[Keys.LAST_ACTIVITY_TIMESTAMP] = timestamp
        }
    }
}
