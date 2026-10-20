package com.tabek.mindfulpause.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.CRC32

/** Versioned portable copy of the three independent preference stores. */
internal object BackupCodec {
    const val MAX_BYTES = 16 * 1024 * 1024
    private const val MAGIC = "MindfulPause"
    private const val VERSION = 1
    private const val BOOLEAN = 1
    private const val INT = 2
    private const val STRING = 3
    private const val STRING_SET = 4
    private val schema = mapOf(
        "settings" to mapOf(
            "tracked_apps" to STRING_SET,
            "breathing_enabled" to BOOLEAN, "breathing_seconds" to INT,
            "message_enabled" to BOOLEAN, "message" to STRING,
            "timer_enabled" to BOOLEAN, "timer_seconds" to INT,
            "reminder_enabled" to BOOLEAN, "reminder_minutes" to INT,
            "reflected_count" to INT, "continued_count" to INT,
        ),
        "blocks" to mapOf("timed_blocks" to STRING_SET, "daily_limits" to STRING_SET, "open_counters" to STRING_SET),
        "events" to mapOf("pause_events" to STRING_SET),
    )

    fun encode(stores: Map<String, Preferences>): ByteArray {
        require(stores.keys == schema.keys) { "Missing backup store" }
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).apply {
            writeUTF(MAGIC)
            writeInt(VERSION)
            for ((store, fields) in schema) {
                val entries = stores.getValue(store).asMap()
                require(entries.size <= fields.size)
                writeInt(entries.size)
                for ((key, value) in entries) {
                    val type = fields[key.name] ?: error("Unknown preference: ${key.name}")
                    validate(key.name, type, value)
                    writeUTF(key.name)
                    writeByte(type)
                    when (type) {
                        BOOLEAN -> writeBoolean(value as Boolean)
                        INT -> writeInt(value as Int)
                        STRING -> writeText(value as String)
                        STRING_SET -> {
                            val set = value as Set<*>
                            require(set.size <= 100_000)
                            writeInt(set.size)
                            set.forEach { writeText(it as String) }
                        }
                    }
                }
            }
            flush()
        }
        val payload = buffer.toByteArray()
        require(payload.size <= MAX_BYTES - 8) { "Backup too large" }
        DataOutputStream(buffer).writeLong(CRC32().apply { update(payload) }.value)
        return buffer.toByteArray()
    }

    fun decode(bytes: ByteArray): Map<String, Preferences> {
        require(bytes.size in 8..MAX_BYTES) { "Invalid backup size" }
        val payloadSize = bytes.size - 8
        val checksum = DataInputStream(ByteArrayInputStream(bytes, payloadSize, 8)).readLong()
        require(checksum == CRC32().apply { update(bytes, 0, payloadSize) }.value) { "Damaged backup" }
        val input = DataInputStream(ByteArrayInputStream(bytes, 0, payloadSize))
        require(input.readUTF() == MAGIC && input.readInt() == VERSION) { "Unsupported backup" }
        val stores = schema.mapValues { (_, fields) ->
            val preferences = mutablePreferencesOf()
            val count = input.readInt()
            require(count in 0..fields.size)
            val seen = mutableSetOf<String>()
            repeat(count) {
                val name = input.readUTF()
                val type = input.readUnsignedByte()
                require(seen.add(name) && fields[name] == type) { "Invalid preference" }
                val value: Any = when (type) {
                    BOOLEAN -> input.readUnsignedByte().also { require(it in 0..1) } == 1
                    INT -> input.readInt()
                    STRING -> input.readText()
                    STRING_SET -> {
                        val size = input.readInt()
                        require(size in 0..100_000)
                        buildSet { repeat(size) { require(add(input.readText())) } }
                    }
                    else -> error("Unsupported type")
                }
                validate(name, type, value)
                when (type) {
                    BOOLEAN -> preferences[booleanPreferencesKey(name)] = value as Boolean
                    INT -> preferences[intPreferencesKey(name)] = value as Int
                    STRING -> preferences[stringPreferencesKey(name)] = value as String
                    STRING_SET -> {
                        @Suppress("UNCHECKED_CAST")
                        preferences[stringSetPreferencesKey(name)] = value as Set<String>
                    }
                }
            }
            preferences.toPreferences()
        }
        require(input.available() == 0) { "Unexpected backup content" }
        return stores
    }

    private fun DataOutputStream.writeText(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readText(): String {
        val size = readInt()
        require(size in 0..available())
        val bytes = ByteArray(size)
        readFully(bytes)
        return Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    }

    private fun validate(name: String, type: Int, value: Any) {
        require(when (type) {
            BOOLEAN -> value is Boolean
            INT -> value is Int && when (name) {
                "reminder_minutes" -> value in ReminderConfig.INTERVALS
                else -> value >= 0
            }
            STRING -> value is String
            STRING_SET -> value is Set<*> && value.all { it is String && validRecord(name, it) }
            else -> false
        }) { "Invalid preference: $name" }
    }

    private fun validRecord(name: String, value: String): Boolean {
        if (name == "tracked_apps") return value.isNotBlank() && '|' !in value
        val parts = value.split('|')
        if (parts.firstOrNull().isNullOrBlank()) return false
        return when (name) {
            "timed_blocks" -> parts.size == 2 && parts[1].toLongOrNull()?.let { it >= 0 } == true
            "daily_limits" -> parts.size == 2 && parts[1].toIntOrNull()?.let { it > 0 } == true
            "open_counters" -> parts.size == 3 && parts[1].toLongOrNull()?.let { it >= 0 } == true &&
                parts[2].toIntOrNull()?.let { it >= 0 } == true
            "pause_events" -> parts.size == 3 && parts[0].toLongOrNull()?.let { it >= 0 } == true &&
                parts[1].isNotBlank() && parts[2] in setOf("REFLECTED", "CONTINUED")
            else -> false
        }
    }
}
