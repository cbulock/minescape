package com.cbulock.minescape.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cbulock.minescape.data.*
import com.cbulock.minescape.game.*
import java.time.LocalDate

@Composable
internal fun ShareChallengeDialog(game: Game, onClose: () -> Unit) {
    val context = LocalContext.current
    val link = remember(game.id) { runCatching { ChallengeLink.encode(game) }.getOrNull() }
    var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onClose, title = { Text("Share this board") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (game.dailyDate != null) "Invite a friend to play the ${game.dailyDate} daily puzzle." else
                "Your friend gets the same mines and starting opening, with a fresh timer in practice. Your moves stay private.")
            if (game.dailyDate == null && game.openingCells.isEmpty()) Text("This older save has no stored opening. The link uses a fresh safe opening on the same board.")
            if (link == null) Text("Reveal a square before sharing this board.")
            if (error) Text("No sharing app is available. Try again after installing one.")
        }
    }, confirmButton = {
        TextButton(enabled = link != null, onClick = {
            val text = if (game.dailyDate != null) "Play this Minescape daily puzzle: $link" else "Play my Minescape board: $link"
            try {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                }, "Share board"))
                onClose()
            } catch (_: android.content.ActivityNotFoundException) { error = true }
        }) { Text("Share link") }
    }, dismissButton = { TextButton(onClick = onClose) { Text("Close") } })
}

@Composable
internal fun PasteChallengeDialog(onOpen: (String) -> Unit, onClose: () -> Unit) {
    var value by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text("Open a shared board") }, text = {
        OutlinedTextField(value, { value = it.take(ChallengeLink.MAX_LENGTH + 1) }, label = { Text("Challenge link") },
            modifier = Modifier.fillMaxWidth(), maxLines = 5,
            supportingText = { Text("Paste a Minescape board or daily puzzle link.") })
    }, confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { onOpen(value); onClose() }) { Text("Open link") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
internal fun IncomingChallenge(link: String, data: AppData, today: String, onOpen: () -> Unit, onClose: () -> Unit) {
    val parsed = remember(link, today) { runCatching { ChallengeLink.parse(link, LocalDate.parse(today)) } }
    val challenge = parsed.getOrNull()
    val dailyDate = (challenge as? SharedChallenge.Daily)?.date
    val official = dailyDate == today && data.dailyRecords[dailyDate] == null
    val resuming = dailyDate == today && data.dailyGame?.dailyDate == dailyDate && !data.dailyGame.finished
    val replaced = if (official) data.dailyGame else data.game
    val confirmation = !resuming && replaced?.finished == false
    if (challenge != null && !confirmation) {
        LaunchedEffect(link, data.game?.id, data.dailyGame?.id) { onOpen() }
    } else AlertDialog(onDismissRequest = onClose,
        title = { Text(if (challenge == null) "Cannot open challenge" else "Open shared board?") },
        text = { Text(if (challenge == null) parsed.exceptionOrNull()?.message ?: "Invalid challenge link." else
            if (official) "This replaces your unfinished daily attempt and archives it as abandoned. Open this daily puzzle?" else
                "This replaces your unfinished regular or practice game. Your records and today's daily attempt are preserved.") },
        confirmButton = { if (challenge != null) TextButton(onClick = onOpen) { Text("Open board") } },
        dismissButton = { TextButton(onClick = onClose) { Text(if (challenge == null) "Close" else "Cancel") } })
}
