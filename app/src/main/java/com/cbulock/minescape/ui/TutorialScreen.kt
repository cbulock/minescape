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
import com.cbulock.minescape.data.Preferences
import com.cbulock.minescape.game.*

@Composable
fun TutorialScreen(preferences: Preferences, inputEnabled: Boolean, onClose: () -> Unit) {
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
                        modifier = Modifier.width(64.dp).semantics { contentDescription = "Tutorial flag mode" }) { Text("Flag", style = MaterialTheme.typography.labelMedium) }
                })
            if (step == 3) OutlinedButton(onClick = { step = 0; flagMode = false; viewport = Viewport(centerColumn = 2.5f, centerRow = 2.5f) }, modifier = Modifier.fillMaxWidth()) { Text("Start lesson again") }
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Learn to play", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onClose) { Text("Done") }
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (step < 3) "Step ${step + 1} of 3" else "You're ready to play", style = MaterialTheme.typography.titleMedium)
        Text(when (step) {
            0 -> "Tap the highlighted square to reveal it."
            1 -> "Hold the highlighted square to flag the mine. The 1 touches one mine, including diagonals. You can also turn on Flag mode and tap."
            2 -> "Tap the highlighted 1 to open its neighbors. Its one mine is flagged. This is chording; incorrect flags can uncover a mine."
            else -> "You revealed every safe square. Flags mark mines; matching flags let you chord a number. Practice games also offer hints and undo."
        })
        if (step == 2 && !preferences.tapToChord) Text("Tap-to-chord is enabled for this lesson. Your game setting stays off.", style = MaterialTheme.typography.bodySmall)
    }
}
