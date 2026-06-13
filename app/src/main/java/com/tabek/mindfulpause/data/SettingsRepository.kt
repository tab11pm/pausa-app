package com.tabek.mindfulpause.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** App-wide DataStore instance (one per process). */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Immutable snapshot of everything the pause screen needs to render itself.
 */
data class PauseConfig(
    val breathingEnabled: Boolean,
    val breathingSeconds: Int,
    val messageEnabled: Boolean,
    val message: String,
    val timerEnabled: Boolean,
    val timerSeconds: Int,
)

/**
 * Single source of truth for user settings. Backed by DataStore so the
 * accessibility service and the settings UI observe the same state and
 * stay in sync without any manual wiring.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val TRACKED_APPS = stringSetPreferencesKey("tracked_apps")
        val BREATHING_ENABLED = booleanPreferencesKey("breathing_enabled")
        val BREATHING_SECONDS = intPreferencesKey("breathing_seconds")
        val MESSAGE_ENABLED = booleanPreferencesKey("message_enabled")
        val MESSAGE = stringPreferencesKey("message")
        val TIMER_ENABLED = booleanPreferencesKey("timer_enabled")
        val TIMER_SECONDS = intPreferencesKey("timer_seconds")
        val REFLECTED_COUNT = intPreferencesKey("reflected_count")
        val CONTINUED_COUNT = intPreferencesKey("continued_count")
    }

    companion object {
        const val DEFAULT_BREATHING_SECONDS = 4
        const val DEFAULT_TIMER_SECONDS = 10
        const val DEFAULT_MESSAGE = "Ты осознанно сюда заходишь?"
    }

    val trackedApps: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.TRACKED_APPS] ?: emptySet() }

    val pauseConfig: Flow<PauseConfig> = context.dataStore.data.map { p ->
        PauseConfig(
            breathingEnabled = p[Keys.BREATHING_ENABLED] ?: true,
            breathingSeconds = p[Keys.BREATHING_SECONDS] ?: DEFAULT_BREATHING_SECONDS,
            messageEnabled = p[Keys.MESSAGE_ENABLED] ?: true,
            message = p[Keys.MESSAGE] ?: DEFAULT_MESSAGE,
            timerEnabled = p[Keys.TIMER_ENABLED] ?: false,
            timerSeconds = p[Keys.TIMER_SECONDS] ?: DEFAULT_TIMER_SECONDS,
        )
    }

    /** How many times the user chose "Выйти" — i.e. reconsidered. */
    val reflectedCount: Flow<Int> =
        context.dataStore.data.map { it[Keys.REFLECTED_COUNT] ?: 0 }

    /** How many times the user chose "Продолжить". */
    val continuedCount: Flow<Int> =
        context.dataStore.data.map { it[Keys.CONTINUED_COUNT] ?: 0 }

    suspend fun setAppTracked(packageName: String, tracked: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.TRACKED_APPS]?.toMutableSet() ?: mutableSetOf()
            if (tracked) current.add(packageName) else current.remove(packageName)
            prefs[Keys.TRACKED_APPS] = current
        }
    }

    suspend fun setBreathing(enabled: Boolean, seconds: Int? = null) {
        context.dataStore.edit {
            it[Keys.BREATHING_ENABLED] = enabled
            if (seconds != null) it[Keys.BREATHING_SECONDS] = seconds
        }
    }

    suspend fun setMessageEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MESSAGE_ENABLED] = enabled }
    }

    suspend fun setMessage(text: String) {
        context.dataStore.edit { it[Keys.MESSAGE] = text }
    }

    suspend fun setTimer(enabled: Boolean, seconds: Int? = null) {
        context.dataStore.edit {
            it[Keys.TIMER_ENABLED] = enabled
            if (seconds != null) it[Keys.TIMER_SECONDS] = seconds
        }
    }

    suspend fun incrementReflected() {
        context.dataStore.edit { it[Keys.REFLECTED_COUNT] = (it[Keys.REFLECTED_COUNT] ?: 0) + 1 }
    }

    suspend fun incrementContinued() {
        context.dataStore.edit { it[Keys.CONTINUED_COUNT] = (it[Keys.CONTINUED_COUNT] ?: 0) + 1 }
    }
}
