package com.camer.minesweeper.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.time.Instant

@Serializable data class Backup(val formatVersion: Int = 1, val exportedAt: String, val data: AppData)
data class BackupPreview(val exportedAt: String?, val data: AppData)

object Backups {
    const val MAX_BYTES = 16 * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun export(data: AppData): ByteArray = json.encodeToString(Backup.serializer(),
        Backup(exportedAt = Instant.now().toString(), data = data.validate())).encodeToByteArray().also {
            require(it.size <= MAX_BYTES) { "Backup is too large" }
        }
    fun preview(input: InputStream): BackupPreview {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (output.size() <= MAX_BYTES) {
            val count = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
            if (count < 0) break
            if (count > 0) output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        require(bytes.size <= MAX_BYTES) { "Backup exceeds the 16 MiB limit" }
        val text = bytes.decodeToString(throwOnInvalidSequence = true)
        val root = json.parseToJsonElement(text).jsonObject
        return if ("data" in root) {
            val backup = json.decodeFromString<Backup>(text)
            require(backup.formatVersion == 1) { "Unsupported backup version" }
            Instant.parse(backup.exportedAt)
            BackupPreview(backup.exportedAt, backup.data.migrate().validate())
        } else {
            val data = json.decodeFromString<AppData>(text)
            require(data.schemaVersion == 1) { "Choose a Minescape backup or version 1 save" }
            BackupPreview(null, data.migrate().validate())
        }
    }
}
