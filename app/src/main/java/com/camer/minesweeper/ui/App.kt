package com.camer.minesweeper.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.window.Dialog
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import com.camer.minesweeper.GameViewModel
import com.camer.minesweeper.R
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import com.camer.minesweeper.data.*
import com.camer.minesweeper.game.*

@Composable
fun MinesweeperApp(model: GameViewModel) {
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
    var page by rememberSaveable { mutableStateOf<String?>(null) }
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
    BackHandler(page != null && data?.activeGame != null) { page = null }
    fun launch(request: StartRequest) {
        when {
            request.practiceDate != null -> model.startDailyPractice(request.practiceDate)
            request.dailyDate != null -> model.startDaily(request.dailyDate)
            request.config != null -> model.start(request.config, request.generation, request.practice)
        }
        page = null
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
        else { model.replay(); page = null }
    }
    val menu: @Composable () -> Unit = {
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.semantics { contentDescription = "Open menu" }) { Text("•••", fontWeight = FontWeight.Bold) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                listOf("New game" to "new", "Statistics" to "stats", "Settings" to "settings", "How to play" to "help", "Daily challenges" to "daily").forEach { (label, destination) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { menuOpen = false; page = destination })
                }
                if (data?.game != null && data?.activeSlot == ActiveSlot.DAILY)
                    DropdownMenuItem(text = { Text("Resume regular game") }, onClick = { menuOpen = false; model.switchSlot(ActiveSlot.REGULAR) })
                if (data?.dailyGame != null && data?.activeSlot == ActiveSlot.REGULAR)
                    DropdownMenuItem(text = { Text("Resume daily challenge") }, onClick = { menuOpen = false; model.switchSlot(ActiveSlot.DAILY) })
            }
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (data?.activeGame == null || page == "new") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (LocalDensity.current.fontScale <= 1.3f) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text("✦", Modifier.padding(horizontal = 11.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    menu()
                }
            }
            error?.let { TextButton(onClick = model::dismissError) { Text(it, color = MaterialTheme.colorScheme.error) } }
            val loaded = data
            if (loaded == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (loaded.activeGame == null || page == "new") {
                DifficultyPicker(onChoose = chooseConfig, canResume = loaded.activeGame != null, onResume = { page = null },
                    preferences = loaded.preferences, onPreferences = model::preferences, onDaily = { daily(model.today()) }, onLearn = { page = "tutorial" })
            } else {
                GameScreen(loaded.activeGame!!, model, loaded, onNew = { page = "new" }, onReplay = replay,
                    inputEnabled = resumed && !paused, hintIndex = hint?.index, menu = menu)
            }
        }
    }
    when (page) {
        "stats" -> FullPage("Statistics", { page = null }) { data?.let { StatsContent(it) } }
        "daily" -> FullPage("Daily challenges", { page = null }) {
            Text("One shared intermediate puzzle each day. The date changes at midnight UTC. Your first attempt counts; replays are practice.")
            Button(onClick = { daily(model.today()) }, modifier = Modifier.fillMaxWidth()) { Text("Today's challenge · ${model.today()}") }
            data?.dailyGame?.takeIf { !it.finished }?.let { g ->
                OutlinedButton(onClick = { model.switchSlot(ActiveSlot.DAILY); page = null }, modifier = Modifier.fillMaxWidth()) { Text("Resume ${g.dailyDate}") }
            }
            data?.dailyRecords.orEmpty().toSortedMap(reverseOrder()).forEach { (date, record) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(date, fontWeight = FontWeight.Bold); Text(dailyOutcome(record.outcome) + " · " + formatTime(record.elapsedMillis)) }
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = { daily(date) }) { Text(if (record.outcome == DailyOutcome.IN_PROGRESS) "Resume" else "Practice") }
                        if (record.outcome == DailyOutcome.WON || record.outcome == DailyOutcome.LOST) ShareResultButton(record)
                    }
                }
            }
            if (data?.dailyRecords.isNullOrEmpty()) Text("Your daily results will appear here.")
        }
        "settings" -> InfoDialog("Settings", { page = null }) {
            data?.preferences?.let { preferences ->
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                ThemeMode.entries.forEach { mode ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = preferences.theme == mode, onClick = { model.preferences(preferences.copy(theme = mode)) })
                        TextButton(onClick = { model.preferences(preferences.copy(theme = mode)) }) {
                            Text(when (mode) { ThemeMode.SYSTEM -> "Use device theme"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" })
                        }
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Haptic feedback", Modifier.weight(1f))
                    Switch(preferences.haptics, onCheckedChange = { model.preferences(preferences.copy(haptics = it)) }, modifier = Modifier.semantics { contentDescription = "Haptic feedback" })
                }
                HorizontalDivider()
                Text("Controls", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Question marks", Modifier.weight(1f))
                    Switch(preferences.questionMarks, { model.preferences(preferences.copy(questionMarks = it)) }, modifier = Modifier.semantics { contentDescription = "Question marks" })
                }
                Text("When enabled, holds and Flag mode cycle through flag → question mark → clear. Question marks don't count as flags. Turning this off keeps existing marks; hold one to clear it.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Tap a number to chord", Modifier.weight(1f))
                    Switch(preferences.tapToChord, { model.preferences(preferences.copy(tapToChord = it)) }, modifier = Modifier.semantics { contentDescription = "Tap to chord" })
                }
                Text("When off, taps on revealed numbers do nothing. You can still reveal covered neighbors individually or use the cell's accessibility action.", style = MaterialTheme.typography.bodySmall)
                Text("Hold duration", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null, 300L, 500L, 800L, 1200L).forEach { duration ->
                        FilterChip(selected = preferences.holdMillis == duration,
                            onClick = { model.preferences(preferences.copy(holdMillis = duration)) },
                            label = { Text(duration?.let { "$it ms" } ?: "Device default") })
                    }
                }
                Text("A hold only changes a marker. Moving between squares cancels the move.", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("Backup and restore", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { pickerOpen = true; exporter.launch("minescape-${model.today()}.json") }, modifier = Modifier.fillMaxWidth()) { Text("Export progress") }
                OutlinedButton(onClick = { pickerOpen = true; importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("Import progress") }
                Text("Your game and statistics stay on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        "help" -> InfoDialog("How to play", { page = null }) {
            Button(onClick = { page = "tutorial" }, modifier = Modifier.fillMaxWidth()) { Text("Learn with a guided board") }
            Text("Reveal every safe square to win. A number tells you how many mines touch that square, including diagonally.")
            Text("Tap to reveal. Hold a covered square to flag it, or turn on Flag mode to place flags with taps. Tap a flag in Flag mode, or hold it, to remove it.")
            Text("Tap a revealed number when you have placed the matching number of flags around it to open its other neighbors. Incorrect flags can uncover a mine!")
            Text("A hold flags once and never reveals. Moving into another cell cancels the move. When squares are too small to tap reliably, your first touch zooms in; lift and touch again to play.")
            Text("Pinch to zoom and drag to explore. Fit board shows the entire field. Your first reveal always opens a safe area; later moves may require a guess.")
            Text("No-guess preset boards are verified solvable by deduction. Practice offers hints and undo, without changing normal records. Daily challenges use a fixed opening and count only the first attempt.")
            Text("Your game saves automatically. The clock pauses while menus are open or the app is in the background.")
            Text("Optional question marks are available in Settings. They mark uncertainty and never count toward a chord. Finished regular boards can be retried in practice with the same mine layout.")
        }
        "tutorial" -> TutorialScreen(data?.preferences ?: Preferences(), resumed, onClose = { page = null })
    }
    replacing?.let { request ->
        AlertDialog(onDismissRequest = { replacing = null }, title = { Text("Replace current game?") },
            text = { Text(if (request.dailyDate != null) "This archives your unfinished daily attempt as abandoned. It won't count as a normal loss." else "This replaces your unfinished regular or practice game. It won't count as a loss.") },
            confirmButton = { TextButton(onClick = { launch(request); replacing = null }) { Text("Start new game") } },
            dismissButton = { TextButton(onClick = { replacing = null; page = null }) { Text("Keep playing") } })
    }
    busy?.let { message ->
        AlertDialog(onDismissRequest = {}, title = { Text(message) }, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
            confirmButton = { if (message.contains("no-guess") || message.contains("deduction")) TextButton(onClick = model::cancelCalculation) { Text("Cancel") } })
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
            } }, confirmButton = { TextButton(onClick = { model.confirmImport(); page = null }) { Text("Replace progress") } },
            dismissButton = { TextButton(onClick = model::dismissPreview) { Text("Cancel") } })
    }
    if (data?.recovered == true) AlertDialog(onDismissRequest = { model.dismissRecovery() }, title = { Text("Saved data reset") },
        text = { Text("Your saved data could not be read. It has been reset so you can play again.") },
        confirmButton = { TextButton(onClick = { model.dismissRecovery() }) { Text("Got it") } })
}

@Composable
private fun DifficultyPicker(onChoose: (BoardConfig) -> Unit, canResume: Boolean, onResume: () -> Unit,
    preferences: Preferences, onPreferences: (Preferences) -> Unit, onDaily: () -> Unit, onLearn: () -> Unit) {
    var custom by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedButton(onClick = onDaily, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Daily challenge") }
        TextButton(onClick = onLearn, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Learn to play") }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Practice"); Text("Hints and undo · records unaffected", style = MaterialTheme.typography.bodySmall) }
            Switch(preferences.practice, { onPreferences(preferences.copy(practice = it)) }, modifier = Modifier.semantics { contentDescription = "Practice mode" })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("No-guess presets"); Text("Every move can be deduced", style = MaterialTheme.typography.bodySmall) }
            Switch(preferences.generation == GenerationMode.NO_GUESS, { onPreferences(preferences.copy(generation = if (it) GenerationMode.NO_GUESS else GenerationMode.CLASSIC)) }, modifier = Modifier.semantics { contentDescription = "No-guess presets" })
        }
        Text("Choose difficulty", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        listOf(Difficulty.BEGINNER, Difficulty.INTERMEDIATE, Difficulty.EXPERT).forEachIndexed { i, difficulty ->
            val config = BoardConfig.preset(difficulty)
            val shades = listOf(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer)
            Card(onClick = { onChoose(config) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = shades[i], shape = RoundedCornerShape(14.dp)) { Text(listOf("01", "02", "03")[i], Modifier.padding(14.dp), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface) }
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text(difficulty.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${config.columns} × ${config.rows}  ·  ${config.mines} mines", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("→", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        OutlinedButton(onClick = { custom = !custom }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(18.dp)) { Text(if (custom) "Hide custom board" else "Make a custom board") }
        if (custom) CustomBoard(onChoose, preferences.lastCustom)
        if (canResume) TextButton(onClick = onResume, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Back to current game") }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CustomBoard(onChoose: (BoardConfig) -> Unit, remembered: BoardConfig) {
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
private fun GameScreen(game: Game, model: GameViewModel, data: AppData, onNew: () -> Unit, onReplay: () -> Unit, inputEnabled: Boolean, hintIndex: Int?, menu: @Composable () -> Unit) {
    val feedback = LocalHapticFeedback.current
    var details by remember(game.id) { mutableStateOf(false) }
    val result = data.results.find { it.id == game.id }
    val official = if (game.mode == PlayMode.DAILY) data.dailyRecords[game.dailyDate] else null
    val compact = LocalWindowInfo.current.containerSize.height / LocalDensity.current.density < 420f
    Column(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Counter("Mines", (game.config.mines - game.flags).toString(), Modifier.weight(1f).semantics { contentDescription = "Estimated mines remaining" })
            Spacer(Modifier.width(8.dp))
            Counter("Time", formatTime(game.elapsedMillis), Modifier.weight(1f).testTag("timer"))
            if (LocalDensity.current.fontScale > 1.3f) {
                IconButton(onClick = onNew, modifier = Modifier.semantics { contentDescription = "New game" }) { Text("↻", fontSize = 24.sp) }
            } else {
                TextButton(onClick = onNew, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("New game", style = MaterialTheme.typography.labelMedium)
                }
            }
            menu()
        }
        if (game.mode != PlayMode.NORMAL || game.generation == GenerationMode.NO_GUESS) {
            Text(when (game.mode) { PlayMode.DAILY -> "Daily · ${game.dailyDate}"; PlayMode.PRACTICE -> "Practice · hints ${game.hintsUsed} · undos ${game.undosUsed} · mines hit ${game.minesHit}"; else -> "No-guess" },
                modifier = Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelSmall)
        }
        if (game.mode == PlayMode.PRACTICE && !game.finished) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = model::requestHint, enabled = inputEnabled && game.status == GameStatus.PLAYING) { Text("Hint") }
            TextButton(onClick = model::undo, enabled = inputEnabled && game.undo.isNotEmpty()) { Text("Undo") }
        }
        if (game.finished) {
            Surface(color = if (game.status == GameStatus.WON) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (result?.newBest == true) "New personal best" else if (game.status == GameStatus.WON) "You won" else "Game over", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    if (game.mode == PlayMode.PRACTICE) TextButton(onClick = model::undo, enabled = inputEnabled && game.undo.isNotEmpty()) { Text("Undo") }
                    if (compact) IconButton(onClick = { details = true }, modifier = Modifier.semantics { contentDescription = "Result details" }) { Text("•••") }
                    TextButton(onClick = onReplay) { Text(if (game.dailyDate != null) "Practice again" else "Play again", style = MaterialTheme.typography.labelMedium) }
                }
            }
            if (!compact) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { details = true }) { Text("Result details") }
                if (game.dailyDate == null) TextButton(onClick = { model.retrySameBoard(game.id) }, enabled = inputEnabled) { Text("Retry same board") }
                if (official != null) ShareResultButton(official)
            }
        }
        key(game.id) {
            Board(game, onCell = { intent ->
                if (!game.finished) {
                    if (data.preferences.haptics && (intent.action == CellAction.TOGGLE_FLAG)) feedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    model.cell(intent)
                }
            }, onViewport = { model.viewport(game.id, it) }, modifier = Modifier.weight(1f).fillMaxWidth(), inputEnabled = inputEnabled && !details, hintIndex = hintIndex,
                holdMillis = data.preferences.holdMillis, tapToChord = data.preferences.tapToChord, questionMarks = data.preferences.questionMarks, controls = {
                Spacer(Modifier.width(12.dp))
                FilledTonalIconToggleButton(checked = game.flagMode, onCheckedChange = { model.toggleFlagMode() }, enabled = !game.finished && inputEnabled,
                    colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary),
                    modifier = Modifier.width(64.dp).semantics { contentDescription = "Flag mode" }) {
                    Text("Flag", style = MaterialTheme.typography.labelMedium)
                }
            })
        }
    }
    if (details) InfoDialog("Result details", { details = false }) {
        Text(if (result?.newBest == true) "New personal best" else if (game.status == GameStatus.WON) "You won" else "Game over", style = MaterialTheme.typography.titleMedium)
        Text("${game.config.difficulty.title} · ${game.config.columns} × ${game.config.rows} · ${game.config.mines} mines")
        Text("${if (game.generation == GenerationMode.NO_GUESS) "No-guess" else "Classic"} · ${when (game.mode) { PlayMode.NORMAL -> "Normal"; PlayMode.PRACTICE -> "Practice"; PlayMode.DAILY -> "Daily · ${game.dailyDate}" }}")
        Text("Time ${formatTime(game.elapsedMillis)} · ${game.cleared}/${game.config.size - game.config.mines} safe squares revealed")
        if (result?.newBest == true || result?.previousBestMillis != null) {
            Text(result.previousBestMillis?.let { "Previous best ${formatTime(it)}" } ?: "Your first recorded win at this difficulty and mode.")
        }
        if (game.mode == PlayMode.PRACTICE) Text("Hints ${game.hintsUsed} · undos ${game.undosUsed} · mines hit ${game.minesHit}\nPractice does not affect records.")
        if (game.dailyDate == null) {
            Text("Retry same board resets the clock and markers in practice. ${if (game.openingCells.isEmpty()) "This older save will use a safe opening because its original opening wasn't stored." else "Your original safe opening and mine layout are preserved."}")
            OutlinedButton(onClick = { details = false; model.retrySameBoard(game.id) }, modifier = Modifier.fillMaxWidth()) { Text("Retry same board in practice") }
        }
        if (official != null) ShareResultButton(official)
    }
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

private data class StartRequest(val config: BoardConfig? = null, val generation: GenerationMode = GenerationMode.CLASSIC,
    val practice: Boolean = false, val dailyDate: String? = null, val practiceDate: String? = null)

private fun dailyOutcome(value: DailyOutcome) = when (value) {
    DailyOutcome.IN_PROGRESS -> "In progress"; DailyOutcome.WON -> "Won"; DailyOutcome.LOST -> "Lost"; DailyOutcome.ABANDONED -> "Abandoned"
}

@Composable
private fun FullPage(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onClose) { Text("Done") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
            }
        }
    }
}

@Composable
private fun StatsContent(data: AppData) {
    var generation by remember { mutableStateOf(GenerationMode.CLASSIC) }
    var difficulty by remember { mutableStateOf<Difficulty?>(null) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GenerationMode.entries.forEach { mode -> FilterChip(selected = generation == mode, onClick = { generation = mode; if (mode == GenerationMode.NO_GUESS && difficulty == Difficulty.CUSTOM) difficulty = null }, label = { Text(if (mode == GenerationMode.CLASSIC) "Classic" else "No-guess") }) }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = difficulty == null, onClick = { difficulty = null }, label = { Text("All difficulties") })
        Difficulty.entries.filter { generation == GenerationMode.CLASSIC || it != Difficulty.CUSTOM }.forEach { value ->
            FilterChip(selected = difficulty == value, onClick = { difficulty = value }, label = { Text(value.title) })
        }
    }
    val statistics = if (generation == GenerationMode.CLASSIC) data.statistics else data.noGuessStatistics
    Difficulty.entries.filter { (difficulty == null || it == difficulty) && (generation == GenerationMode.CLASSIC || it != Difficulty.CUSTOM) }.forEach { value ->
        val stats = statistics[value] ?: Statistics()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${stats.wins} wins · ${stats.losses} losses · ${stats.winPercent?.let { "%.1f%%".format(it) } ?: "—"} win rate")
            Text("Winning streak ${stats.currentStreak} · best ${stats.bestStreak}")
            if (value != Difficulty.CUSTOM) Text("Best time  ${stats.bestMillis?.let(::formatTime) ?: "—"}", color = MaterialTheme.colorScheme.primary)
        }
    }
    Text("Only finished normal games count. Practice and daily attempts are separate. Custom boards share a win/loss total. Streak tracking began with this update.", style = MaterialTheme.typography.bodySmall)
    HorizontalDivider()
    Text("Recent results", style = MaterialTheme.typography.titleMedium)
    val results = data.results.asReversed().filter { it.generation == generation && (difficulty == null || it.config.difficulty == difficulty) }
    if (results.isEmpty()) Text("No recent results yet.")
    results.forEach { result ->
        Text("${result.config.difficulty.title} · ${if (result.won) "Won" else "Lost"} · ${formatTime(result.elapsedMillis)}\n${result.finishedAt.take(10)}")
    }
}
