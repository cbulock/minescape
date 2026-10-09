package com.camer.minesweeper.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.camer.minesweeper.data.ThemeMode

val Violet = Color(0xFF6554D9)
val Coral = Color(0xFFFF7969)
val Mint = Color(0xFFB8E9D7)
val Ink = Color(0xFF282640)

private val Light = lightColorScheme(
    primary = Violet, onPrimary = Color.White, primaryContainer = Color(0xFFEAE6FF), onPrimaryContainer = Ink,
    secondary = Color(0xFF176952), secondaryContainer = Mint, onSecondaryContainer = Color(0xFF123E31),
    tertiary = Color(0xFFAB4235), tertiaryContainer = Color(0xFFFFDAD4),
    background = Color(0xFFF7F8FC), onBackground = Ink,
    surface = Color(0xFFF7F8FC), onSurface = Ink, surfaceContainer = Color.White,
    surfaceVariant = Color(0xFFE9EAF3), onSurfaceVariant = Color(0xFF616075), outlineVariant = Color(0xFFDCDCE9),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFC1B5FF), onPrimary = Color(0xFF2C205F), primaryContainer = Color(0xFF433477),
    secondary = Mint, secondaryContainer = Color(0xFF264C40), onSecondaryContainer = Mint,
    tertiary = Coral, tertiaryContainer = Color(0xFF60372F),
    background = Color(0xFF181824), onBackground = Color(0xFFF0EEFF),
    surface = Color(0xFF181824), onSurface = Color(0xFFF0EEFF), surfaceContainer = Color(0xFF232331),
    surfaceVariant = Color(0xFF333345), onSurfaceVariant = Color(0xFFC2BED5), outlineVariant = Color(0xFF464359),
)

@Composable
fun MinesweeperTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
