package com.camer.minesweeper.ui

import android.content.Intent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.camer.minesweeper.data.DailyRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ShareResultButton(record: DailyRecord) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preparing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    TextButton(enabled = !preparing, onClick = {
        preparing = true
        scope.launch {
            try {
                val intent = withContext(Dispatchers.IO) { DailyShare.intent(context, record) }
                context.startActivity(Intent.createChooser(intent, "Share daily result"))
            } catch (_: Exception) { failed = true }
            finally { preparing = false }
        }
    }) { Text(if (preparing) "Preparing…" else "Share result") }
    if (failed) AlertDialog(onDismissRequest = { failed = false }, title = { Text("Could not share result") },
        text = { Text("The share card could not be opened. Try again after checking storage and available sharing apps.") },
        confirmButton = { TextButton(onClick = { failed = false }) { Text("Close") } })
}
