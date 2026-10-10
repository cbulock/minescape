package com.camer.minesweeper.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.camer.minesweeper.data.Preferences
import com.camer.minesweeper.data.ThemeMode

@Composable
internal fun SettingsScreen(
    preferences: Preferences,
    onPreferences: (Preferences) -> Unit,
    onClose: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val largeText = LocalDensity.current.fontScale > 1.3f
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            Box(Modifier.safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Settings", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onClose) { Text("Done") }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("settings-scroll")
                        .padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        SettingsSection("Appearance") {
                            SettingsCard {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        maxItemsInEachRow = if (largeText) 1 else 3) {
                                        ThemeMode.entries.forEach { mode ->
                                            FilterChip(selected = preferences.theme == mode,
                                                onClick = { onPreferences(preferences.copy(theme = mode)) },
                                                modifier = Modifier.weight(1f).semantics {
                                                    if (mode == ThemeMode.SYSTEM) contentDescription = "Use device theme"
                                                }, label = { Text(when (mode) {
                                                    ThemeMode.SYSTEM -> "Device"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark"
                                                }) })
                                        }
                                    }
                                    SettingsDescription(when (preferences.theme) {
                                        ThemeMode.SYSTEM -> "Follows your device’s light or dark theme."
                                        ThemeMode.LIGHT -> "Always use a light background."
                                        ThemeMode.DARK -> "Always use a dark background."
                                    })
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        SettingsDescription("Colors follow your device’s wallpaper palette.")
                                    }
                                }
                            }
                        }
                        SettingsSection("Controls") {
                            SettingsCard {
                                SettingsToggle("Haptic feedback", "Feel a vibration when you change a square’s marker.",
                                    preferences.haptics, { onPreferences(preferences.copy(haptics = it)) })
                                SettingsDivider()
                                SettingsToggle("Question marks", "Cycle flag → question mark → clear. Question marks don’t count as flags.",
                                    preferences.questionMarks, { onPreferences(preferences.copy(questionMarks = it)) })
                                SettingsDivider()
                                SettingsToggle("Tap a number to chord", "Reveal its covered neighbors when the number of nearby flags matches. Incorrect flags can uncover a mine.",
                                    preferences.tapToChord, { onPreferences(preferences.copy(tapToChord = it)) }, "Tap to chord")
                            }
                            SettingsCard {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Hold duration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    SettingsDescription("How long to hold a square to change its marker.")
                                    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(null, 300L, 500L, 800L, 1200L).forEach { duration ->
                                            FilterChip(selected = preferences.holdMillis == duration,
                                                onClick = { onPreferences(preferences.copy(holdMillis = duration)) },
                                                label = { Text(duration?.let { "$it ms" } ?: "Device default") })
                                        }
                                    }
                                    SettingsDescription("A hold never reveals a square. Moving between squares cancels the move.")
                                }
                            }
                            SettingsDescription("Turning off question marks keeps existing marks; hold one to clear it. With tap-to-chord off, you can still use the cell’s accessibility action.")
                        }
                        SettingsSection("Backup and restore") {
                            Surface(shape = RoundedCornerShape(20.dp), color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Keep your progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("Your games save automatically on this device. Export a backup to keep a copy of your games, settings, and records.", style = MaterialTheme.typography.bodyMedium)
                                    FilledTonalButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Export progress") }
                                    OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Import progress") }
                                    Text("Importing replaces current progress. You’ll review the backup before confirming.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Text("Changes save automatically.", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(content = content)
    }
}

@Composable
private fun SettingsToggle(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit,
                           accessibilityLabel: String = title) {
    Column(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
        .semantics { contentDescription = accessibilityLabel }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Switch(checked = checked, onCheckedChange = null)
        }
        SettingsDescription(description)
    }
}

@Composable
private fun SettingsDescription(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
}
