package com.cbulock.minescape

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import com.cbulock.minescape.data.ThemeMode
import com.cbulock.minescape.ui.MinescapeApp
import com.cbulock.minescape.ui.MinescapeTheme

class MainActivity : ComponentActivity() {
    private val model: GameViewModel by viewModels { ViewModelProvider.AndroidViewModelFactory(application) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val data by model.data.collectAsStateWithLifecycle()
            MinescapeTheme(data?.preferences?.theme ?: ThemeMode.SYSTEM) {
                val light = MaterialTheme.colorScheme.background.luminance() > .5f
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = light
                        isAppearanceLightNavigationBars = light
                    }
                }
                MinescapeApp(model)
            }
        }
    }
}
