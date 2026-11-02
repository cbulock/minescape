package com.cbulock.minescape.ui

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
import com.cbulock.minescape.R
import com.cbulock.minescape.data.Preferences
import com.cbulock.minescape.data.ThemeMode

@Composable
internal fun SettingsScreen(
    preferences: Preferences,
    onPreferences: (Preferences) -> Unit,
    onClose: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val strings = textResources()
    val colors = MaterialTheme.colorScheme
    val largeText = LocalDensity.current.fontScale > 1.3f
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            Box(Modifier.safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        maxItemsInEachRow = if (largeText) 1 else 2,
                        horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.CenterVertically) {
                        Text(strings(R.string.settings), (if (largeText) Modifier.fillMaxWidth() else Modifier.weight(1f)).semantics { heading() },
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onClose) { Text(strings(R.string.done)) }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("settings-scroll")
                        .padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        SettingsSection(strings(R.string.appearance)) {
                            SettingsCard {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(strings(R.string.theme), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        maxItemsInEachRow = if (largeText) 1 else 3) {
                                        ThemeMode.entries.forEach { mode ->
                                            FilterChip(selected = preferences.theme == mode,
                                                onClick = { onPreferences(preferences.copy(theme = mode)) },
                                                modifier = Modifier.weight(1f).semantics {
                                                    if (mode == ThemeMode.SYSTEM) contentDescription = strings(R.string.use_device_theme)
                                                }, label = { Text(when (mode) {
                                                    ThemeMode.SYSTEM -> strings(R.string.device); ThemeMode.LIGHT -> strings(R.string.light); ThemeMode.DARK -> strings(R.string.dark)
                                                }) })
                                        }
                                    }
                                    SettingsDescription(when (preferences.theme) {
                                        ThemeMode.SYSTEM -> strings(R.string.follows_your_device_s_light_or_dark_theme)
                                        ThemeMode.LIGHT -> strings(R.string.always_use_a_light_background)
                                        ThemeMode.DARK -> strings(R.string.always_use_a_dark_background)
                                    })
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        SettingsDescription(strings(R.string.colors_follow_your_device_s_wallpaper_palette))
                                    }
                                }
                            }
                        }
                        SettingsSection(strings(R.string.controls)) {
                            SettingsCard {
                                SettingsToggle(strings(R.string.haptic_feedback), strings(R.string.feel_a_vibration_when_you_change_a_square_s),
                                    preferences.haptics, { onPreferences(preferences.copy(haptics = it)) })
                                SettingsDivider()
                                SettingsToggle(strings(R.string.question_marks), strings(R.string.cycle_flag_question_mark_clear_question_marks_don_t),
                                    preferences.questionMarks, { onPreferences(preferences.copy(questionMarks = it)) })
                                SettingsDivider()
                                SettingsToggle(strings(R.string.tap_a_number_to_chord), strings(R.string.reveal_its_covered_neighbors_when_the_number_of_nearby),
                                    preferences.tapToChord, { onPreferences(preferences.copy(tapToChord = it)) }, strings(R.string.tap_to_chord))
                            }
                            SettingsCard {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(strings(R.string.hold_duration), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    SettingsDescription(strings(R.string.how_long_to_hold_a_square_to_change_its))
                                    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(null, 300L, 500L, 800L, 1200L).forEach { duration ->
                                            FilterChip(selected = preferences.holdMillis == duration,
                                                onClick = { onPreferences(preferences.copy(holdMillis = duration)) },
                                                label = { Text(duration?.let { strings(R.string.ms, it) } ?: strings(R.string.device_default)) })
                                        }
                                    }
                                    SettingsDescription(strings(R.string.a_hold_never_reveals_a_square_moving_between_squares))
                                }
                            }
                            SettingsDescription(strings(R.string.turning_off_question_marks_keeps_existing_marks_hold_one))
                        }
                        SettingsSection(strings(R.string.backup_and_restore)) {
                            Surface(shape = RoundedCornerShape(20.dp), color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(strings(R.string.keep_your_progress), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(strings(R.string.your_games_save_automatically_on_this_device_export_a), style = MaterialTheme.typography.bodyMedium)
                                    FilledTonalButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text(strings(R.string.export_progress)) }
                                    OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text(strings(R.string.import_progress)) }
                                    Text(strings(R.string.importing_replaces_current_progress_you_ll_review_the_backup), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Text(strings(R.string.changes_save_automatically), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
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
