package com.camer.minesweeper

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.camer.minesweeper.data.AppData
import com.camer.minesweeper.data.AppDataSerializer

class MinesweeperApplication : Application() {
    // Exactly one store per application instance, also isolating simulated installs in tests.
    val gameStore: DataStore<AppData> by lazy {
        DataStoreFactory.create(
            serializer = AppDataSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { AppData(recovered = true) },
            produceFile = { dataStoreFile("minesweeper.json") },
        )
    }
}
