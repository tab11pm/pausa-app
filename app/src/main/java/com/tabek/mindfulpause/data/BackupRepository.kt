package com.tabek.mindfulpause.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class BackupRepository(private val stores: Map<String, DataStore<Preferences>>) {
    private val mutex = Mutex()

    suspend fun export(): ByteArray = mutex.withLock {
        BackupCodec.encode(stores.mapValues { (_, store) -> store.data.first() })
    }

    suspend fun restore(bytes: ByteArray) = mutex.withLock {
        // Parse and validate ALL stores before changing any live data.
        val replacement = BackupCodec.decode(bytes)
        require(replacement.keys == stores.keys)
        val original = stores.mapValues { (_, store) -> store.data.first() }
        // A screen rotation/cancellation must not interrupt the three writes halfway.
        withContext(NonCancellable) {
            val changed = mutableListOf<String>()
            try {
                for ((name, store) in stores) {
                    store.updateData { replacement.getValue(name) }
                    changed.add(name)
                }
            } catch (failure: Exception) {
                // DataStore transactions are per file; roll back completed files on I/O failure.
                for (name in changed.asReversed()) {
                    try {
                        stores.getValue(name).updateData { original.getValue(name) }
                    } catch (rollbackFailure: Exception) {
                        failure.addSuppressed(rollbackFailure)
                    }
                }
                throw failure
            }
        }
    }
}
