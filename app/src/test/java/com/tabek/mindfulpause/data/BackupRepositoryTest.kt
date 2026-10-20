package com.tabek.mindfulpause.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `restore replaces all stores and notifies existing collectors`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val stores = listOf("settings", "blocks", "events").associateWith { name ->
                PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("$name.preferences_pb") }
            }
            val repo = BackupRepository(stores)
            val count = intPreferencesKey("reflected_count")
            val log = stringSetPreferencesKey("pause_events")
            val limits = stringSetPreferencesKey("daily_limits")
            stores.getValue("settings").edit { it[count] = 42 }
            stores.getValue("events").edit { it[log] = setOf("1720000000000|com.example|REFLECTED") }
            stores.getValue("blocks").edit { it[limits] = setOf("com.example|5") }
            val backup = repo.export()
            stores.getValue("settings").edit {
                it[count] = 100
                it[stringPreferencesKey("message")] = "Новое сообщение"
            }
            stores.getValue("events").edit { it.clear() }
            stores.getValue("blocks").edit { it.clear() }
            repo.restore(backup)
            assertEquals(42, stores.getValue("settings").data.first()[count])
            assertFalse(stores.getValue("settings").data.first().contains(stringPreferencesKey("message")))
            assertEquals(setOf("1720000000000|com.example|REFLECTED"), stores.getValue("events").data.first()[log])
            assertEquals(setOf("com.example|5"), stores.getValue("blocks").data.first()[limits])

            try {
                repo.restore(backup.copyOf(20))
                fail("Damaged copy must be rejected")
            } catch (_: Exception) {
                assertEquals(42, stores.getValue("settings").data.first()[count])
                assertEquals(setOf("com.example|5"), stores.getValue("blocks").data.first()[limits])
                assertEquals(setOf("1720000000000|com.example|REFLECTED"), stores.getValue("events").data.first()[log])
            }
        } finally {
            scope.cancel()
        }
    }
}
