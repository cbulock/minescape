package com.cbulock.minescape

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.cbulock.minescape.data.AppData
import com.cbulock.minescape.data.AppDataSerializer

class MinescapeApplication : Application() {
    // Exactly one store per application instance, also isolating simulated installs in tests.
    val gameStore: DataStore<AppData> by lazy {
        DataStoreFactory.create(
            serializer = AppDataSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { AppData(recovered = true) },
            // Keep the existing filename so app updates retain saved games and preferences.
            produceFile = { dataStoreFile("minesweeper.json") },
        )
    }
}
