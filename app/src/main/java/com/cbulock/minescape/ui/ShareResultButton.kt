package com.cbulock.minescape.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.cbulock.minescape.R
import com.cbulock.minescape.data.DailyRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

@Composable
fun ShareResultButton(record: DailyRecord, modifier: Modifier = Modifier, outlined: Boolean = false, prominent: Boolean = false) {
    val strings = textResources()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preparing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var preview by remember(record) { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(preview, record, strings) {
        bitmap = null
        if (preview) {
            preparing = true
            try { bitmap = withContext(Dispatchers.IO) { DailyShare.card(record, strings.resources) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true; preview = false }
            finally { preparing = false }
        }
    }
    DisposableEffect(bitmap) {
        val retained = bitmap
        onDispose { retained?.recycle() }
    }
    val showPreview: () -> Unit = { failed = false; preview = true }
    val share: () -> Unit = {
        preparing = true
        scope.launch {
            try {
                val intent = withContext(Dispatchers.IO) { DailyShare.intent(context.createConfigurationContext(strings.resources.configuration), record) }
                context.startActivity(Intent.createChooser(intent, strings(R.string.share_daily_result)))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { preparing = false }
        }
    }
    val label = if (preparing) strings(R.string.preparing) else if (prominent) strings(R.string.share_daily_result) else strings(R.string.share_result)
    if (prominent) Button(enabled = !preparing, onClick = showPreview, modifier = modifier, shape = RoundedCornerShape(14.dp)) {
        Text(label)
    } else if (outlined) OutlinedButton(enabled = !preparing, onClick = showPreview, modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Text(label)
    } else TextButton(enabled = !preparing, onClick = showPreview, modifier = modifier) {
        Text(label)
    }
    if (preview) {
        AlertDialog(
            onDismissRequest = { if (!preparing) preview = false },
            title = { Text(strings(R.string.daily_result)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    bitmap?.let {
                        Image(it.asImageBitmap(), contentDescription = DailyShare.text(record, strings.resources),
                            modifier = Modifier.fillMaxWidth().aspectRatio(1.5f))
                    } ?: CircularProgressIndicator()
                    Text(strings(R.string.share_your_result_without_revealing_the_board))
                }
            },
            confirmButton = { TextButton(onClick = share, enabled = bitmap != null && !preparing) {
                Text(if (preparing) strings(R.string.preparing) else strings(R.string.share_results))
            } },
            dismissButton = { TextButton(onClick = { preview = false }, enabled = !preparing) { Text(strings(R.string.close)) } },
        )
    }
    if (failed) AlertDialog(onDismissRequest = { failed = false }, title = { Text(strings(R.string.could_not_share_result)) },
        text = { Text(strings(R.string.the_share_card_could_not_be_opened_try_again)) },
        confirmButton = { TextButton(onClick = { failed = false }) { Text(strings(R.string.close)) } })
}
