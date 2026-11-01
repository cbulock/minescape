package com.cbulock.minescape.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cbulock.minescape.game.Hint

@Composable
internal fun HintPanel(hint: Hint, answer: Boolean, onAnswer: () -> Unit, onApply: () -> Unit, onClose: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().heightIn(max = 210.dp).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (answer) if (hint.mine) "This square is a mine" else "This square is safe" else "Look at the outlined squares",
                style = MaterialTheme.typography.titleSmall)
            Text(if (answer) hint.explanation else if (hint.clueIndices.isEmpty())
                "Use the total mine count to reason about the outlined covered squares." else
                "Compare the outlined clues with the covered squares beside them. Can you find a certain move?",
                style = MaterialTheme.typography.bodySmall)
            Text("Solid: clues · Dashed: covered squares${if (answer) " · Double outline: answer" else ""}",
                style = MaterialTheme.typography.labelSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!answer) FilledTonalButton(onClick = onAnswer) { Text("Show answer") }
                else FilledTonalButton(onClick = onApply) { Text(if (hint.mine) "Flag square" else "Reveal square") }
                TextButton(onClick = onClose) { Text("Close hint") }
            }
        }
    }
}
