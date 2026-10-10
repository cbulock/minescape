package com.camer.minesweeper.ui

import android.content.Intent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.camer.minesweeper.data.DailyRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ShareResultButton(record: DailyRecord, modifier: Modifier = Modifier, outlined: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preparing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val share: () -> Unit = {
        preparing = true
        scope.launch {
            try {
                val intent = withContext(Dispatchers.IO) { DailyShare.intent(context, record) }
                context.startActivity(Intent.createChooser(intent, "Share daily result"))
            } catch (_: Exception) { failed = true }
            finally { preparing = false }
        }
    }
    if (outlined) OutlinedButton(enabled = !preparing, onClick = share, modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Text(if (preparing) "Preparing…" else "Share result")
    } else TextButton(enabled = !preparing, onClick = share, modifier = modifier) {
        Text(if (preparing) "Preparing…" else "Share result")
    }
    if (failed) AlertDialog(onDismissRequest = { failed = false }, title = { Text("Could not share result") },
        text = { Text("The share card could not be opened. Try again after checking storage and available sharing apps.") },
        confirmButton = { TextButton(onClick = { failed = false }) { Text("Close") } })
}
