package com.cbulock.minescape.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cbulock.minescape.R
import com.cbulock.minescape.game.Hint

@Composable
internal fun HintPanel(hint: Hint, answer: Boolean, onAnswer: () -> Unit, onApply: () -> Unit, onClose: () -> Unit) {
    val strings = textResources()
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().heightIn(max = 210.dp).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (answer) if (hint.mine) strings(R.string.hint_square_mine) else strings(R.string.hint_square_safe) else strings(R.string.hint_look_outlined),
                style = MaterialTheme.typography.titleSmall)
            Text(if (answer) strings.hint(hint) else if (hint.clueIndices.isEmpty())
                strings(R.string.hint_total_guidance) else strings(R.string.hint_clue_guidance),
                style = MaterialTheme.typography.bodySmall)
            Text(strings(R.string.hint_legend) + if (answer) strings(R.string.hint_legend_answer) else "",
                style = MaterialTheme.typography.labelSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!answer) FilledTonalButton(onClick = onAnswer) { Text(strings(R.string.show_answer)) }
                else FilledTonalButton(onClick = onApply) { Text(strings(if (hint.mine) R.string.flag_square else R.string.reveal_square)) }
                TextButton(onClick = onClose) { Text(strings(R.string.close_hint)) }
            }
        }
    }
}
