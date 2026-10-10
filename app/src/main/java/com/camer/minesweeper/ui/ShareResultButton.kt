package com.camer.minesweeper.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.camer.minesweeper.data.DailyRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.util.Locale

@Composable
fun ShareResultButton(record: DailyRecord, modifier: Modifier = Modifier, outlined: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preparing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var preview by remember(record) { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0] ?: Locale.US
    var bitmap by remember(record, locale) { mutableStateOf<Bitmap?>(null) }
    val showPreview: () -> Unit = {
        preparing = true
        scope.launch {
            try {
                bitmap = withContext(Dispatchers.IO) { DailyShare.card(record, locale) }
                preview = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { preparing = false }
        }
    }
    val share: () -> Unit = {
        preparing = true
        scope.launch {
            try {
                val intent = withContext(Dispatchers.IO) { DailyShare.intent(context, record) }
                context.startActivity(Intent.createChooser(intent, "Share daily result"))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { preparing = false }
        }
    }
    if (outlined) OutlinedButton(enabled = !preparing, onClick = showPreview, modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Text(if (preparing) "Preparing…" else "Share result")
    } else TextButton(enabled = !preparing, onClick = showPreview, modifier = modifier) {
        Text(if (preparing) "Preparing…" else "Share result")
    }
    if (preview) {
        AlertDialog(
            onDismissRequest = { if (!preparing) preview = false },
            title = { Text("Daily result") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    bitmap?.let {
                        Image(it.asImageBitmap(), contentDescription = DailyShare.text(record, locale),
                            modifier = Modifier.fillMaxWidth().aspectRatio(1.5f))
                    } ?: CircularProgressIndicator()
                    Text("Share your result without revealing the board.")
                }
            },
            confirmButton = { TextButton(onClick = share, enabled = bitmap != null && !preparing) {
                Text(if (preparing) "Preparing…" else "Share image")
            } },
            dismissButton = { TextButton(onClick = { preview = false }, enabled = !preparing) { Text("Close") } },
        )
    }
    if (failed) AlertDialog(onDismissRequest = { failed = false }, title = { Text("Could not share result") },
        text = { Text("The share card could not be opened. Try again after checking storage and available sharing apps.") },
        confirmButton = { TextButton(onClick = { failed = false }) { Text("Close") } })
}
