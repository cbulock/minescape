package com.cbulock.minescape.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cbulock.minescape.R
import com.cbulock.minescape.data.Preferences
import com.cbulock.minescape.game.*

@Composable
fun TutorialScreen(preferences: Preferences, inputEnabled: Boolean, onClose: () -> Unit) {
    val strings = textResources()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var flagMode by rememberSaveable { mutableStateOf(false) }
    var viewport by remember { mutableStateOf(Viewport(centerColumn = 2.5f, centerRow = 2.5f)) }
    val game = remember(step, flagMode, viewport) { Tutorial.game(step).copy(flagMode = flagMode, viewport = viewport) }
    val board: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            Board(game, onCell = { intent ->
                if (inputEnabled) {
                    val next = Tutorial.advance(step, intent)
                    if (next != step) { step = next; flagMode = false }
                }
            }, onViewport = { viewport = it }, modifier = Modifier.weight(1f), inputEnabled = inputEnabled && step < 3,
                hintIndex = Tutorial.targets.getOrNull(step), holdMillis = preferences.holdMillis, tagPrefix = "tutorial-", controls = {
                    FilledTonalIconToggleButton(checked = flagMode, onCheckedChange = { flagMode = it }, enabled = inputEnabled && step == 1,
                        modifier = Modifier.width(64.dp).semantics { contentDescription = strings(R.string.tutorial_flag_mode) }) { Text(strings(R.string.flag), style = MaterialTheme.typography.labelMedium) }
                })
            if (step == 3) OutlinedButton(onClick = { step = 0; flagMode = false; viewport = Viewport(centerColumn = 2.5f, centerRow = 2.5f) }, modifier = Modifier.fillMaxWidth()) { Text(strings(R.string.start_lesson_again)) }
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(strings(R.string.learn_to_play), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onClose) { Text(strings(R.string.done)) }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    if (maxWidth > maxHeight) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TutorialInstructions(step, preferences, Modifier.weight(.8f).fillMaxHeight().verticalScroll(rememberScrollState()))
                        board(Modifier.weight(1f).fillMaxHeight())
                    } else Column(Modifier.fillMaxSize()) {
                        TutorialInstructions(step, preferences, Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()))
                        Spacer(Modifier.height(8.dp))
                        board(Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialInstructions(step: Int, preferences: Preferences, modifier: Modifier) {
    val strings = textResources()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (step < 3) strings(R.string.step_of_3, step + 1) else strings(R.string.you_re_ready_to_play), style = MaterialTheme.typography.titleMedium)
        Text(when (step) {
            0 -> strings(R.string.tap_the_highlighted_square_to_reveal_it)
            1 -> strings(R.string.hold_the_highlighted_square_to_flag_the_mine_the)
            2 -> strings(R.string.tap_the_highlighted_1_to_open_its_neighbors_its)
            else -> strings(R.string.you_revealed_every_safe_square_flags_mark_mines_matching)
        })
        if (step == 2 && !preferences.tapToChord) Text(strings(R.string.tap_to_chord_is_enabled_for_this_lesson_your), style = MaterialTheme.typography.bodySmall)
    }
}
