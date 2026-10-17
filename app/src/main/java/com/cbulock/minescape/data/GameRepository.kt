package com.cbulock.minescape.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.cbulock.minescape.MinescapeApplication
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

object AppDataSerializer : Serializer<AppData> {
    override val defaultValue = AppData()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    override suspend fun readFrom(input: InputStream): AppData = try {
        json.decodeFromString<AppData>(input.readBytes().decodeToString()).migrate().validate()
    } catch (error: SerializationException) {
        throw CorruptionException("Saved game is unreadable", error)
    } catch (error: RuntimeException) {
        throw CorruptionException("Saved game is invalid", error)
    }
    override suspend fun writeTo(t: AppData, output: OutputStream) {
        output.write(json.encodeToString(AppData.serializer(), t).encodeToByteArray())
    }
}

class GameRepository(context: Context) {
    private val store = (context.applicationContext as MinescapeApplication).gameStore
    val data = store.data
    suspend fun update(transform: (AppData) -> AppData): AppData = store.updateData(transform)
}
