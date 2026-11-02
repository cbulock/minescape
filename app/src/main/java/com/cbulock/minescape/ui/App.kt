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
    val strings = textResources()
    val data by model.data.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val hint by model.hint.collectAsStateWithLifecycle()
    val hintMessage by model.hintMessage.collectAsStateWithLifecycle()
    val preview by model.preview.collectAsStateWithLifecycle()
    val incomingChallenge by model.pendingChallenge.collectAsStateWithLifecycle()
    var pasteChallenge by rememberSaveable { mutableStateOf(false) }
    var shareChallenge by rememberSaveable { mutableStateOf(false) }
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
        model.dismissHint()
        if (destination == "new" && homeVisible) return
        if (destination != page && (destination != "new" || data?.activeGame != null)) pages = pages + destination
    }
    fun closePage() { pages = pages.dropLast(1) }
    fun showGame() { pages = emptyList() }
    fun showHome() { pages = listOf("home") }
    var menuOpen by remember { mutableStateOf(false) }
    var replacing by remember { mutableStateOf<StartRequest?>(null) }
    LaunchedEffect(incomingChallenge) {
        if (incomingChallenge != null) {
            pages = emptyList(); menuOpen = false; replacing = null; pasteChallenge = false; shareChallenge = false
        }
    }
    val owner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED); if (!resumed) model.onBackground() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); model.setActive(false) }
    }
    val overlayPaused = menuOpen || page != null || replacing != null || data?.recovered == true || busy != null || preview != null || pickerOpen || error != null || incomingChallenge != null || pasteChallenge || shareChallenge
    val paused = overlayPaused || hintMessage != null
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
        if (date != model.today() || record != null && record.outcome != DailyOutcome.IN_PROGRESS) choose(StartRequest(practiceDate = date))
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
            IconButton(onClick = { model.dismissHint(); menuOpen = true }, modifier = Modifier.semantics { contentDescription = strings(R.string.open_menu) }) { Text("•••", fontWeight = FontWeight.Bold) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(strings(R.string.open_shared_board)) }, onClick = { menuOpen = false; pasteChallenge = true; model.dismissHint() })
                if (data?.activeGame?.status != null && data?.activeGame?.status != GameStatus.READY)
                    DropdownMenuItem(text = { Text(strings(R.string.share_this_board)) }, onClick = { menuOpen = false; shareChallenge = true; model.dismissHint() })
                listOf(strings(R.string.new_game) to "new", strings(R.string.statistics) to "stats", strings(R.string.settings) to "settings", strings(R.string.how_to_play) to "help", strings(R.string.daily_challenges) to "daily").forEach { (label, destination) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { menuOpen = false; openPage(destination) })
                }
                if (data?.game != null && data?.activeSlot == ActiveSlot.DAILY)
                    DropdownMenuItem(text = { Text(strings(R.string.resume_regular_game)) }, onClick = { menuOpen = false; model.switchSlot(ActiveSlot.REGULAR); showGame() })
                if (data?.dailyGame?.finished == false && data?.activeSlot == ActiveSlot.REGULAR)
                    DropdownMenuItem(text = { Text(strings(R.string.resume_daily_challenge)) }, onClick = { menuOpen = false; model.switchSlot(ActiveSlot.DAILY); showGame() })
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
            error?.let { TextButton(onClick = model::dismissError) { Text(strings.message(it), color = MaterialTheme.colorScheme.error) } }
            val loaded = data
            val resultPresentation = loaded?.activeGame?.let { rememberResultPresentation(it, resumed && !paused) }
            if (loaded == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (homeVisible) {
                HomeScreen(onChoose = chooseConfig, currentGame = loaded.activeGame, onResume = ::showGame,
                    preferences = loaded.preferences, onPreferences = model::preferences, onDaily = { openPage("daily") },
                    onLearn = { openPage("tutorial") }, onStatistics = { openPage("stats") })
            } else {
                GameScreen(loaded.activeGame!!, model, loaded, onNew = { openPage("new") }, onReplay = replay,
                    onHome = ::showHome,
                    inputEnabled = resumed && !overlayPaused && (hintMessage == null || hint != null), hint = hint, menu = menu, resultPresentation = resultPresentation!!)
            }
        }
    }
    if (pasteChallenge) PasteChallengeDialog(model::receiveChallenge, onClose = { pasteChallenge = false })
    if (shareChallenge) data?.activeGame?.let { ShareChallengeDialog(it, onClose = { shareChallenge = false }) }
    if (incomingChallenge != null && data != null && preview == null && busy == null && data?.recovered != true) {
        val snapshot = data!!
        val challengeToday = model.today()
        IncomingChallenge(incomingChallenge!!, snapshot, challengeToday, onOpen = {
            model.openChallenge(incomingChallenge!!, snapshot.game?.id, snapshot.dailyGame?.id, challengeToday)
            replacing = null; pasteChallenge = false; shareChallenge = false; menuOpen = false
            showGame()
        }, onClose = model::dismissChallenge)
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
        "help" -> InfoDialog(strings(R.string.how_to_play), ::closePage) {
            Button(onClick = { openPage("tutorial") }, modifier = Modifier.fillMaxWidth()) { Text(strings(R.string.learn_with_a_guided_board)) }
            Text(strings(R.string.reveal_every_safe_square_to_win_a_number_tells))
            Text(strings(R.string.tap_to_reveal_hold_a_covered_square_to_flag))
            Text(strings(R.string.tap_a_revealed_number_when_you_have_placed_the))
            Text(strings(R.string.a_hold_flags_once_and_never_reveals_moving_into))
            Text(strings(R.string.pinch_to_zoom_and_drag_to_explore_fit_board))
            Text(strings(R.string.no_guess_preset_boards_are_verified_solvable_by_deduction))
            Text(strings(R.string.your_game_saves_automatically_the_clock_pauses_while_menus))
            Text(strings(R.string.optional_question_marks_are_available_in_settings_they_mark))
        }
        "tutorial" -> TutorialScreen(data?.preferences ?: Preferences(), resumed, onClose = ::closePage)
    }
    replacing?.let { request ->
        AlertDialog(onDismissRequest = { replacing = null }, title = { Text(strings(R.string.replace_current_game)) },
            text = { Text(if (request.dailyDate != null) strings(R.string.this_archives_your_unfinished_daily_attempt_as_abandoned_it) else strings(R.string.this_replaces_your_unfinished_regular_or_practice_game_it)) },
            confirmButton = { TextButton(onClick = { launch(request); replacing = null }) { Text(strings(R.string.start_new_game)) } },
            dismissButton = { TextButton(onClick = { replacing = null; showGame() }) { Text(strings(R.string.keep_playing)) } })
    }
    busy?.let { message ->
        ProgressDialog(strings.message(message), message.cancellable, model::cancelCalculation)
    }
    BackHandler(hint != null && !overlayPaused) { model.dismissHint() }
    hintMessage?.takeIf { hint == null }?.let { message ->
        val target = hint
        val columns = data?.activeGame?.config?.columns ?: 1
        AlertDialog(onDismissRequest = model::dismissHint,
            title = { Text(target?.let { strings(R.string.row_column, if (it.mine) strings(R.string.mine) else strings(R.string.safe_square), it.index / columns + 1, it.index % columns + 1) } ?: strings(R.string.no_deduction_available)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(strings.message(message)) } },
            confirmButton = {
                if (target != null) TextButton(onClick = model::applyHint) { Text(if (target.mine) strings(R.string.flag_square) else strings(R.string.reveal_square)) }
                else if (data?.activeGame?.status == GameStatus.READY) TextButton(onClick = model::useClassic) { Text(strings(R.string.use_classic)) }
            }, dismissButton = { TextButton(onClick = model::dismissHint) { Text(strings(R.string.close)) } })
    }
    preview?.let { backup ->
        AlertDialog(onDismissRequest = model::dismissPreview, title = { Text(strings(R.string.replace_progress_with_backup)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings(R.string.this_replaces_both_saved_games_settings_statistics_and_history))
                Text(strings(R.string.backup_date, backup.exportedAt?.let { DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm 'UTC'", strings.locale).withZone(ZoneOffset.UTC).format(Instant.parse(it)) } ?: strings(R.string.legacy_save_date_unavailable)))
                Text(strings(R.string.regular_game, backup.data.game?.config?.difficulty?.let(strings::difficulty) ?: strings(R.string.none)))
                Text(strings(R.string.daily_game, backup.data.dailyGame?.dailyDate ?: strings(R.string.none)))
                Text(strings.backupSummary((backup.data.statistics.values + backup.data.noGuessStatistics.values).sumOf { it.wins },
                    (backup.data.statistics.values + backup.data.noGuessStatistics.values).sumOf { it.losses }, backup.data.dailyRecords.size))
            } }, confirmButton = { TextButton(onClick = { model.confirmImport(); showGame() }) { Text(strings(R.string.replace_progress)) } },
            dismissButton = { TextButton(onClick = model::dismissPreview) { Text(strings(R.string.cancel)) } })
    }
    if (data?.recovered == true) AlertDialog(onDismissRequest = { model.dismissRecovery() }, title = { Text(strings(R.string.saved_data_reset)) },
        text = { Text(strings(R.string.your_saved_data_could_not_be_read_it_has)) },
        confirmButton = { TextButton(onClick = { model.dismissRecovery() }) { Text(strings(R.string.got_it)) } })
}

@Composable
internal fun CustomBoard(onChoose: (BoardConfig) -> Unit, remembered: BoardConfig) {
    val strings = textResources()
    var columns by rememberSaveable { mutableStateOf(remembered.columns.toString()) }
    var rows by rememberSaveable { mutableStateOf(remembered.rows.toString()) }
    var mines by rememberSaveable { mutableStateOf(remembered.mines.toString()) }
    val c = columns.toIntOrNull() ?: 0
    val r = rows.toIntOrNull() ?: 0
    val m = mines.toIntOrNull() ?: 0
    val config = BoardConfig(c, r, m)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(columns, { columns = it.filter(Char::isDigit).take(2) }, label = { Text(strings(R.string.columns)) }, modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = c !in 5..50)
            OutlinedTextField(rows, { rows = it.filter(Char::isDigit).take(2) }, label = { Text(strings(R.string.rows)) }, modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = r !in 5..50)
        }
        OutlinedTextField(mines, { mines = it.filter(Char::isDigit).take(3) }, label = { Text(strings(R.string.mines)) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = m !in 1..(c * r * 35 / 100))
        Text(if (c in 5..50 && r in 5..50) strings(R.string.squares_mines_choose_1_mines, c * r, strings.format("%.1f", m * 100f / (c * r)), c * r * 35 / 100) else strings(R.string.choose_5_50_columns_and_5_50_rows), style = MaterialTheme.typography.bodySmall)
        Button(onClick = { onChoose(config) }, enabled = config.isValid(), modifier = Modifier.fillMaxWidth()) { Text(strings(R.string.play_custom_board)) }
    }
}

@Composable
private fun GameScreen(game: Game, model: GameViewModel, data: AppData, onNew: () -> Unit, onReplay: () -> Unit, onHome: () -> Unit, inputEnabled: Boolean, hint: Hint?, menu: @Composable () -> Unit, resultPresentation: ResultPresentation) {
    var hintAnswer by rememberSaveable(game.id, hint) { mutableStateOf(false) }
    val strings = textResources()
    val feedback = LocalHapticFeedback.current
    val details = resultPresentation.open
    val result = data.results.find { it.id == game.id }
    val official = if (game.mode == PlayMode.DAILY) data.dailyRecords[game.dailyDate] else null
    val largeText = LocalDensity.current.fontScale > 1.3f
    Column(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (game.finished) {
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(if (game.status == GameStatus.WON) strings(R.string.you_won) else strings(R.string.game_over), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(if (largeText) strings.time(game.elapsedMillis) else strings(R.string.formatted_value, strings.difficulty(game.config.difficulty), strings.time(game.elapsedMillis)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(onClick = resultPresentation.show, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text(strings(R.string.view_result), style = MaterialTheme.typography.labelMedium) }
            } else {
                Counter(strings(R.string.mines), (game.config.mines - game.flags).toString(), Modifier.weight(.8f).semantics { contentDescription = strings(R.string.estimated_mines_remaining) })
                Spacer(Modifier.width(8.dp))
                GameClock(game.elapsedMillis, Modifier.weight(1.2f).testTag("timer"))
                Spacer(Modifier.width(8.dp))
                if (largeText) {
                    FilledTonalIconButton(onClick = onNew, modifier = Modifier.semantics { contentDescription = strings(R.string.new_game) }) { Text("+", fontSize = 24.sp) }
                } else {
                    FilledTonalButton(onClick = onNew, contentPadding = PaddingValues(horizontal = 12.dp), shape = RoundedCornerShape(14.dp)) {
                        Text("+", fontSize = 20.sp, modifier = Modifier.padding(end = 6.dp))
                        Text(strings(R.string.new_game), style = MaterialTheme.typography.labelMedium)
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
            Text(when (game.mode) { PlayMode.DAILY -> strings(R.string.daily, game.dailyDate); PlayMode.PRACTICE -> strings(R.string.practice_hints_undos_mines_hit, game.hintsUsed, game.undosUsed, game.minesHit); else -> strings(R.string.no_guess) },
                modifier = Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelSmall)
        }
        if (game.mode == PlayMode.PRACTICE && !game.finished && hint == null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = model::requestHint, enabled = inputEnabled && game.status == GameStatus.PLAYING) { Text(strings(R.string.hint)) }
            TextButton(onClick = model::undo, enabled = inputEnabled && game.undo.isNotEmpty()) { Text(strings(R.string.undo)) }
        }
        key(game.id) {
            Board(game, onCell = { intent ->
                if (!game.finished) {
                    if (data.preferences.haptics && (intent.action == CellAction.TOGGLE_FLAG)) feedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    model.cell(intent)
                }
            }, onViewport = { model.viewport(game.id, it) }, modifier = Modifier.weight(1f).fillMaxWidth(), inputEnabled = inputEnabled && !details,
                movesEnabled = inputEnabled && !details && hint == null, hintIndex = if (hintAnswer) hint?.index else null,
                hintClues = hint?.clueIndices ?: emptySet(), hintCovered = hint?.coveredIndices ?: emptySet(),
                holdMillis = data.preferences.holdMillis, tapToChord = data.preferences.tapToChord, questionMarks = data.preferences.questionMarks, controls = {
                if (!game.finished) {
                    Spacer(Modifier.width(12.dp))
                    FilledTonalIconToggleButton(checked = game.flagMode, onCheckedChange = { model.toggleFlagMode() }, enabled = inputEnabled && hint == null,
                        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimary),
                        modifier = Modifier.width(64.dp).semantics { contentDescription = strings(R.string.flag_mode) }) {
                        Text(strings(R.string.flag), style = MaterialTheme.typography.labelMedium)
                    }
                }
            })
        }
        if (hint != null) HintPanel(hint, hintAnswer, onAnswer = { hintAnswer = true }, onApply = model::applyHint, onClose = model::dismissHint)
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
    val strings = textResources()
    AlertDialog(onDismissRequest = onClose, title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp), content = content) },
        confirmButton = { TextButton(onClick = onClose) { Text(strings(R.string.done)) } })
}

@Composable
internal fun ProgressDialog(message: String, canCancel: Boolean, onCancel: () -> Unit) {
    val strings = textResources()
    AlertDialog(onDismissRequest = { if (canCancel) onCancel() },
        properties = DialogProperties(dismissOnBackPress = canCancel, dismissOnClickOutside = false),
        title = { Text(message) }, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
        confirmButton = { if (canCancel) TextButton(onClick = onCancel) { Text(strings(R.string.cancel)) } })
}

private data class StartRequest(val config: BoardConfig? = null, val generation: GenerationMode = GenerationMode.CLASSIC,
    val practice: Boolean = false, val dailyDate: String? = null, val practiceDate: String? = null)
