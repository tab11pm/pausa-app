package com.tabek.mindfulpause.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    private fun sample() = mapOf(
        "settings" to preferencesOf(
            booleanPreferencesKey("breathing_enabled") to false,
            intPreferencesKey("reflected_count") to 42,
            stringPreferencesKey("message") to "Зачем я открываю приложение? 🌿",
            stringSetPreferencesKey("tracked_apps") to setOf("com.example.app"),
        ),
        "blocks" to preferencesOf(
            stringSetPreferencesKey("timed_blocks") to setOf("com.example.app|9223372036854775807"),
            stringSetPreferencesKey("daily_limits") to setOf("com.example.app|5"),
            stringSetPreferencesKey("open_counters") to setOf("com.example.app|1720000000000|3"),
        ),
        "events" to preferencesOf(
            stringSetPreferencesKey("pause_events") to setOf("1720000000000|com.example.app|REFLECTED"),
        ),
    )

    @Test fun `round trip preserves history settings and block formats`() {
        val restored = BackupCodec.decode(BackupCodec.encode(sample()))
        assertEquals(sample(), restored)
        assertEquals(42, restored.getValue("settings")[intPreferencesKey("reflected_count")])
        assertEquals(setOf("com.example.app|9223372036854775807"),
            restored.getValue("blocks")[stringSetPreferencesKey("timed_blocks")])
    }

    @Test fun `empty stores are valid backups`() {
        val empty = mapOf("settings" to emptyPreferences(), "blocks" to emptyPreferences(), "events" to emptyPreferences())
        assertEquals(empty, BackupCodec.decode(BackupCodec.encode(empty)))
    }

    @Test fun `rejects truncated corrupted and unrelated files`() {
        val bytes = BackupCodec.encode(sample())
        assertThrows(Exception::class.java) { BackupCodec.decode(bytes.copyOf(bytes.size / 2)) }
        val changed = bytes.copyOf().apply { this[size / 2] = (this[size / 2].toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { BackupCodec.decode(changed) }
        assertThrows(Exception::class.java) { BackupCodec.decode("not a backup".toByteArray()) }
    }

    @Test fun `rejects missing store and preference with wrong type`() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.encode(sample() - "events") }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.encode(sample() + ("settings" to preferencesOf(stringPreferencesKey("reflected_count") to "42")))
        }
    }

    @Test fun `rejects invalid blocking and event records`() {
        for ((store, key, value) in listOf(
            Triple("blocks", "daily_limits", "com.example.app|-1"),
            Triple("blocks", "open_counters", "com.example.app|yesterday|3"),
            Triple("events", "pause_events", "1720000000000|com.example.app|UNKNOWN"),
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                BackupCodec.encode(sample() + (store to preferencesOf(stringSetPreferencesKey(key) to setOf(value))))
            }
        }
    }

    @Test fun `rejects oversized input before parsing`() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(ByteArray(BackupCodec.MAX_BYTES + 1)) }
    }
}
