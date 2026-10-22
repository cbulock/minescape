package com.cbulock.minescape.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cbulock.minescape.GameViewModel
import com.cbulock.minescape.R
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import com.cbulock.minescape.data.*
import com.cbulock.minescape.game.*

@Composable
fun MinescapeApp(model: GameViewModel) {
    val data by model.data.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val hint by model.hint.collectAsStateWithLifecycle()
    val hintMessage by model.hintMessage.collectAsStateWithLifecycle()
    val preview by model.preview.collectAsStateWithLifecycle()
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        pickerOpen = false; uri?.let(model::exportBackup)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pickerOpen = false; uri?.let(model::importBackup)
    }
    var pages by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val page = pages.lastOrNull()
    val homeVisible = data?.activeGame == null || "home" in pages || "new" in pages
    fun openPage(destination: String) {
        if (destination == "new" && homeVisible) return
        if (destination != page && (destination != "new" || data?.activeGame != null)) pages = pages + destination
    }
    fun closePage() { pages = pages.dropLast(1) }
    fun showGame() { pages = emptyList() }
    fun showHome() { pages = listOf("home") }
    var menuOpen by remember { mutableStateOf(false) }
    var replacing by remember { mutableStateOf<StartRequest?>(null) }
    val owner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED); if (!resumed) model.onBackground() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); model.setActive(false) }
    }
    val paused = menuOpen || page != null || replacing != null || data?.recovered == true || busy != null || hintMessage != null || preview != null || pickerOpen || error != null
    LaunchedEffect(resumed, paused) { model.setActive(resumed && !paused) }
    // Close overlays first; the board returns to home, where Android owns Back.
    BackHandler((page == "new" || (!homeVisible && page == null) || error != null) &&
        (page == null || page == "new" || page == "home") &&
        !menuOpen && replacing == null && busy == null && hintMessage == null && preview == null &&
        data?.recovered != true && !pickerOpen) {
        if (error != null) model.dismissError()
        else if (page == "new") closePage()
        else showHome()
    }
    fun launch(request: StartRequest) {
        when {
            request.practiceDate != null -> model.startDailyPractice(request.practiceDate)
            request.dailyDate != null -> model.startDaily(request.dailyDate)
            request.config != null -> model.start(request.config, request.generation, request.practice)
        }
        showGame()
    }
    fun choose(request: StartRequest) {
        val replacingGame = if (request.dailyDate != null) data?.dailyGame else data?.game
        if (replacingGame?.finished == false && (request.dailyDate == null || replacingGame.dailyDate != request.dailyDate)) replacing = request
        else launch(request)
    }
    fun daily(date: String) {
        val record = data?.dailyRecords?.get(date)
        if (record != null && record.outcome != DailyOutcome.IN_PROGRESS) choose(StartRequest(practiceDate = date))
        else choose(StartRequest(dailyDate = date))
    }
    val chooseConfig: (BoardConfig) -> Unit = { config -> choose(StartRequest(config = config,
        generation = data?.preferences?.generation ?: GenerationMode.CLASSIC, practice = data?.preferences?.practice ?: false)) }
    val replay: () -> Unit = {
        val g = data?.activeGame
        if (g?.dailyDate != null) choose(StartRequest(practiceDate = g.dailyDate))
        else { model.replay(); showGame() }
    }
    val menu: @Composable () -> Unit = {
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.semantics { contentDescription = "Open menu" }) { Text("•••", fontWeight = FontWeight.Bold) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                listOf("New game" to "new", "Statistics" to "stats", "Settings" to "settings", "How to play" to "help", "Daily challenges" to "daily").forEach { (label, destination) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { menuOpen = false; openPage(destination) })
                }
                if (data?.game != null && data?.activeSlot == ActiveSlot.DAILY)
                    DropdownMenuItem(text = { Text("Resume regular game") }, onClick = { menuOpen = false; model.switchSlot(ActiveSlot.REGULAR); showGame() })
                if (data?.dailyGame?.finished == false && data?.activeSlot == ActiveSlot.REGULAR)
                    DropdownMenuItem(text = { Text("Resume daily challenge") }, onClick = { menuOpen = false; model.switchSlot(ActiveSlot.DAILY); showGame() })
            }
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (homeVisible) {
                Row(Modifier.align(Alignment.CenterHorizontally).widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (LocalDensity.current.fontScale <= 1.3f) {
                        Surface(shape = RoundedCornerShape(12.dp), color = Violet) {
                            Image(
                                painter = painterResource(R.drawable.minescape_icon_art),
                                contentDescription = null,
                                modifier = Modifier.size(44.dp).padding(2.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    menu()
                }
            }
            error?.let { TextButton(onClick = model::dismissError) { Text(it, color = MaterialTheme.colorScheme.error) } }
            val loaded = data
            val resultPresentation = loaded?.activeGame?.let { rememberResultPresentation(it, resumed && !paused) }
            if (loaded == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (homeVisible) {
                HomeScreen(onChoose = chooseConfig, currentGame = loaded.activeGame, onResume = ::showGame,
                    preferences = loaded.preferences, onPreferences = model::preferences, onDaily = { daily(model.today()) },
                    onLearn = { openPage("tutorial") }, onStatistics = { openPage("stats") })
            } else {
                GameScreen(loaded.activeGame!!, model, loaded, onNew = { openPage("new") }, onReplay = replay,
                    onHome = ::showHome,
                    inputEnabled = resumed && !paused, hintIndex = hint?.index, menu = menu, resultPresentation = resultPresentation!!)
            }
        }
    }
    when (page) {
        "stats" -> data?.let { StatisticsScreen(it, ::closePage) }
        "daily" -> data?.let { DailyChallengesScreen(it, model.today(), ::daily,
            onResume = { model.switchSlot(ActiveSlot.DAILY); showGame() }, onClose = ::closePage) }
        "settings" -> data?.preferences?.let { preferences ->
            SettingsScreen(preferences, model::preferences, ::closePage,
                onExport = { pickerOpen = true; exporter.launch("minescape-${model.today()}.json") },
                onImport = { pickerOpen = true; importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
        }
        "help" -> InfoDialog("How to play", ::closePage) {
            Button(onClick = { openPage("tutorial") }, modifier = Modifier.fillMaxWidth()) { Text("Learn with a guided board") }
            Text("Reveal every safe square to win. A number tells you how many mines touch that square, including diagonally.")
            Text("Tap to reveal. Hold a covered square to flag it, or turn on Flag mode to place flags with taps. Tap a flag in Flag mode, or hold it, to remove it.")
            Text("Tap a revealed number when you have placed the matching number of flags around it to open its other neighbors. Incorrect flags can uncover a mine!")
            Text("A hold flags once and never reveals. Moving into another cell cancels the move. When squares are too small to tap reliably, your first touch zooms in; lift and touch again to play.")
            Text("Pinch to zoom and drag to explore. Fit board shows the entire field. Your first reveal always opens a safe area; later moves may require a guess.")
            Text("No-guess preset boards are verified solvable by deduction. Practice offers hints and undo, without changing normal records. Daily challenges use a fixed opening and count only the first attempt.")
            Text("Your game saves automatically. The clock pauses while menus are open or the app is in the background.")
            Text("Optional question marks are available in Settings. They mark uncertainty and never count toward a chord. Finished regular boards can be retried in practice with the same mine layout.")
        }
        "tutorial" -> TutorialScreen(data?.preferences ?: Preferences(), resumed, onClose = ::closePage)
    }
    replacing?.let { request ->
        AlertDialog(onDismissRequest = { replacing = null }, title = { Text("Replace current game?") },
            text = { Text(if (request.dailyDate != null) "This archives your unfinished daily attempt as abandoned. It won't count as a normal loss." else "This replaces your unfinished regular or practice game. It won't count as a loss.") },
            confirmButton = { TextButton(onClick = { launch(request); replacing = null }) { Text("Start new game") } },
            dismissButton = { TextButton(onClick = { replacing = null; showGame() }) { Text("Keep playing") } })
    }
    busy?.let { message ->
        ProgressDialog(message, model::cancelCalculation)
    }
    hintMessage?.let { message ->
        val target = hint
        val columns = data?.activeGame?.config?.columns ?: 1
        AlertDialog(onDismissRequest = model::dismissHint,
            title = { Text(target?.let { "${if (it.mine) "Mine" else "Safe square"} · row ${it.index / columns + 1}, column ${it.index % columns + 1}" } ?: "No deduction available") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(message) } },
            confirmButton = {
                if (target != null) TextButton(onClick = model::applyHint) { Text(if (target.mine) "Flag square" else "Reveal square") }
                else if (data?.activeGame?.status == GameStatus.READY) TextButton(onClick = model::useClassic) { Text("Use classic") }
            }, dismissButton = { TextButton(onClick = model::dismissHint) { Text("Close") } })
    }
    preview?.let { backup ->
        AlertDialog(onDismissRequest = model::dismissPreview, title = { Text("Replace progress with backup?") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This replaces both saved games, settings, statistics, and history.")
                Text("Backup date: ${backup.exportedAt?.let { DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC).format(Instant.parse(it)) } ?: "Legacy save; date unavailable"}")
                Text("Regular game: ${backup.data.game?.config?.difficulty?.title ?: "None"}")
                Text("Daily game: ${backup.data.dailyGame?.dailyDate ?: "None"}")
                Text("${(backup.data.statistics.values + backup.data.noGuessStatistics.values).sumOf { it.wins }} wins · ${(backup.data.statistics.values + backup.data.noGuessStatistics.values).sumOf { it.losses }} losses · ${backup.data.dailyRecords.size} daily attempts")
            } }, confirmButton = { TextButton(onClick = { model.confirmImport(); showGame() }) { Text("Replace progress") } },
            dismissButton = { TextButton(onClick = model::dismissPreview) { Text("Cancel") } })
    }
    if (data?.recovered == true) AlertDialog(onDismissRequest = { model.dismissRecovery() }, title = { Text("Saved data reset") },
        text = { Text("Your saved data could not be read. It has been reset so you can play again.") },
        confirmButton = { TextButton(onClick = { model.dismissRecovery() }) { Text("Got it") } })
}

@Composable
internal fun CustomBoard(onChoose: (BoardConfig) -> Unit, remembered: BoardConfig) {
    var columns by rememberSaveable { mutableStateOf(remembered.columns.toString()) }
    var rows by rememberSaveable { mutableStateOf(remembered.rows.toString()) }
    var mines by rememberSaveable { mutableStateOf(remembered.mines.toString()) }
    val c = columns.toIntOrNull() ?: 0
    val r = rows.toIntOrNull() ?: 0
    val m = mines.toIntOrNull() ?: 0
    val config = BoardConfig(c, r, m)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(columns, { columns = it.filter(Char::isDigit).take(2) }, label = { Text("Columns") }, modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = c !in 5..50)
            OutlinedTextField(rows, { rows = it.filter(Char::isDigit).take(2) }, label = { Text("Rows") }, modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = r !in 5..50)
        }
        OutlinedTextField(mines, { mines = it.filter(Char::isDigit).take(3) }, label = { Text("Mines") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = m !in 1..(c * r * 35 / 100))
        Text(if (c in 5..50 && r in 5..50) "${c * r} squares · ${"%.1f".format(m * 100f / (c * r))}% mines\nChoose 1–${c * r * 35 / 100} mines." else "Choose 5–50 columns and 5–50 rows.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { onChoose(config) }, enabled = config.isValid(), modifier = Modifier.fillMaxWidth()) { Text("Play custom board") }
    }
}

@Composable
private fun GameScreen(game: Game, model: GameViewModel, data: AppData, onNew: () -> Unit, onReplay: () -> Unit, onHome: () -> Unit, inputEnabled: Boolean, hintIndex: Int?, menu: @Composable () -> Unit, resultPresentation: ResultPresentation) {
    val feedback = LocalHapticFeedback.current
    val details = resultPresentation.open
    val result = data.results.find { it.id == game.id }
    val official = if (game.mode == PlayMode.DAILY) data.dailyRecords[game.dailyDate] else null
    val largeText = LocalDensity.current.fontScale > 1.3f
    Column(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (game.finished) {
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(if (game.status == GameStatus.WON) "You won" else "Game over", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(if (largeText) formatTime(game.elapsedMillis) else "${game.config.difficulty.title} · ${formatTime(game.elapsedMillis)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(onClick = resultPresentation.show, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text("View result", style = MaterialTheme.typography.labelMedium) }
            } else {
                Counter("Mines", (game.config.mines - game.flags).toString(), Modifier.weight(1f).semantics { contentDescription = "Estimated mines remaining" })
                Spacer(Modifier.width(8.dp))
                Counter("Time", formatTime(game.elapsedMillis), Modifier.weight(1f).testTag("timer"))
                if (largeText) {
                    FilledTonalIconButton(onClick = onNew, modifier = Modifier.semantics { contentDescription = "New game" }) { Text("+", fontSize = 24.sp) }
                } else {
                    FilledTonalButton(onClick = onNew, contentPadding = PaddingValues(horizontal = 12.dp), shape = RoundedCornerShape(14.dp)) {
                        Text("+", fontSize = 20.sp, modifier = Modifier.padding(end = 6.dp))
                        Text("New game", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            menu()
        }
        if (game.finished) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                FinishedGameActions(official, onNew, horizontal = maxWidth >= 480.dp && !largeText)
            }
        }
        if (!game.finished && (game.mode != PlayMode.NORMAL || game.generation == GenerationMode.NO_GUESS)) {
            Text(when (game.mode) { PlayMode.DAILY -> "Daily · ${game.dailyDate}"; PlayMode.PRACTICE -> "Practice · hints ${game.hintsUsed} · undos ${game.undosUsed} · mines hit ${game.minesHit}"; else -> "No-guess" },
                modifier = Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelSmall)
        }
        if (game.mode == PlayMode.PRACTICE && !game.finished) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = model::requestHint, enabled = inputEnabled && game.status == GameStatus.PLAYING) { Text("Hint") }
            TextButton(onClick = model::undo, enabled = inputEnabled && game.undo.isNotEmpty()) { Text("Undo") }
        }
        key(game.id) {
            Board(game, onCell = { intent ->
                if (!game.finished) {
                    if (data.preferences.haptics && (intent.action == CellAction.TOGGLE_FLAG)) feedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    model.cell(intent)
                }
            }, onViewport = { model.viewport(game.id, it) }, modifier = Modifier.weight(1f).fillMaxWidth(), inputEnabled = inputEnabled && !details, hintIndex = hintIndex,
                holdMillis = data.preferences.holdMillis, tapToChord = data.preferences.tapToChord, questionMarks = data.preferences.questionMarks, controls = {
                if (!game.finished) {
                    Spacer(Modifier.width(12.dp))
                    FilledTonalIconToggleButton(checked = game.flagMode, onCheckedChange = { model.toggleFlagMode() }, enabled = inputEnabled,
                        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimary),
                        modifier = Modifier.width(64.dp).semantics { contentDescription = "Flag mode" }) {
                        Text("Flag", style = MaterialTheme.typography.labelMedium)
                    }
                }
            })
        }
    }
    if (details && game.finished) ResultDetailsDialog(game, result, official, onClose = resultPresentation.dismiss,
        onNew = { resultPresentation.dismiss(); onNew() },
        onBack = { resultPresentation.dismiss(); onHome() },
        onReplay = { resultPresentation.dismiss(); onReplay() },
        onRetry = { resultPresentation.dismiss(); model.retrySameBoard(game.id) },
        onUndo = { resultPresentation.dismiss(); model.undo() })
}

@Composable
private fun Counter(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun InfoDialog(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp), content = content) },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } })
}

@Composable
internal fun ProgressDialog(message: String, onCancel: () -> Unit) {
    val canCancel = message.contains("no-guess") || message.contains("deduction")
    AlertDialog(onDismissRequest = { if (canCancel) onCancel() },
        properties = DialogProperties(dismissOnBackPress = canCancel, dismissOnClickOutside = false),
        title = { Text(message) }, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
        confirmButton = { if (canCancel) TextButton(onClick = onCancel) { Text("Cancel") } })
}

private data class StartRequest(val config: BoardConfig? = null, val generation: GenerationMode = GenerationMode.CLASSIC,
    val practice: Boolean = false, val dailyDate: String? = null, val practiceDate: String? = null)
