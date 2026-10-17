package com.cbulock.minescape

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cbulock.minescape.data.*
import com.cbulock.minescape.game.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.IOException

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GameRepository(application)
    private val engine = GameEngine()
    private val daily by lazy { DailyChallenge(Json.decodeFromString<List<List<Int>>>(
        application.assets.open("daily-v1.json").bufferedReader().use { it.readText() })) }
    private val mutableData = MutableStateFlow<AppData?>(null)
    val data = mutableData.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val mutableBusy = MutableStateFlow<String?>(null)
    val busy = mutableBusy.asStateFlow()
    private val mutableHint = MutableStateFlow<Hint?>(null)
    val hint = mutableHint.asStateFlow()
    private val mutableHintMessage = MutableStateFlow<String?>(null)
    val hintMessage = mutableHintMessage.asStateFlow()
    private val mutablePreview = MutableStateFlow<BackupPreview?>(null)
    val preview = mutablePreview.asStateFlow()
    private var calculation: Job? = null
    private var calculationToken = 0L
    private data class Command(val at: Long, val active: Boolean? = null, val change: (AppData) -> AppData = { it })
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private var saveFailed = false
    private var active = false
    private var lastTime = SystemClock.elapsedRealtime()

    init {
        viewModelScope.launch {
            try { repository.data.collect { mutableData.value = it } }
            catch (_: IOException) { mutableError.value = "Your save could not be opened. Free some storage and reopen the app." }
        }
        viewModelScope.launch {
            for (command in commands) {
                val delta = (command.at - lastTime).coerceAtLeast(0)
                try {
                    repository.update { old ->
                        val g = old.activeGame
                        val timed = if (active && g?.status == GameStatus.PLAYING)
                            old.withActiveGame(g.copy(elapsedMillis = g.elapsedMillis + delta)) else old
                        command.change(timed).recordResult()
                    }
                    if (saveFailed) { mutableError.value = null; saveFailed = false }
                } catch (_: IOException) { saveFailed = true; mutableError.value = "Progress could not be saved. Check free storage and try again." }
                lastTime = command.at
                command.active?.let { active = it }
            }
        }
        viewModelScope.launch {
            while (true) { delay(1000); if (active && data.value?.activeGame?.status == GameStatus.PLAYING) submit() }
        }
    }
    private fun submit(active: Boolean? = null, change: (AppData) -> AppData = { it }) {
        commands.trySend(Command(SystemClock.elapsedRealtime(), active, change))
    }
    fun setActive(value: Boolean) = submit(active = value)
    fun onBackground() { if (calculation?.isActive == true) cancelCalculation() }
    fun cancelCalculation() {
        calculationToken++; calculation?.cancel(); calculation = null; mutableBusy.value = null
        dismissHint()
    }
    fun dismissError() { mutableError.value = null }
    fun dismissHint() { mutableHint.value = null; mutableHintMessage.value = null }
    fun today() = daily.today()
    fun start(config: BoardConfig, generation: GenerationMode = data.value?.preferences?.generation ?: GenerationMode.CLASSIC,
              practice: Boolean = data.value?.preferences?.practice ?: false) {
        cancelCalculation()
        submit { old -> old.copy(game = engine.newGame(config).copy(
            generation = if (config.difficulty == Difficulty.CUSTOM) GenerationMode.CLASSIC else generation,
            mode = if (practice) PlayMode.PRACTICE else PlayMode.NORMAL), activeSlot = ActiveSlot.REGULAR,
            preferences = if (config.difficulty == Difficulty.CUSTOM) old.preferences.copy(lastCustom = config) else old.preferences) }
    }
    fun replay() {
        val g = data.value?.activeGame ?: return
        if (g.mode == PlayMode.DAILY || g.dailyDate != null) startDailyPractice(g.dailyDate!!)
        else start(g.config, g.generation, g.mode == PlayMode.PRACTICE)
    }
    fun retrySameBoard(gameId: String) {
        cancelCalculation()
        submit { old ->
            val g = old.activeGame
            if (g?.id != gameId || !g.finished) old
            else if (old.activeSlot == ActiveSlot.DAILY && old.game?.finished == false) old
            else old.copy(game = engine.retryAsPractice(g), activeSlot = ActiveSlot.REGULAR)
        }
    }
    fun switchSlot(slot: ActiveSlot) { cancelCalculation(); submit { old ->
        if (slot == ActiveSlot.DAILY && old.dailyGame == null) old else old.copy(activeSlot = slot)
    } }
    fun startDaily(date: String = today()) {
        cancelCalculation()
        submit { old ->
            val existing = old.dailyRecords[date]
            when {
                old.dailyGame?.dailyDate == date -> old.copy(activeSlot = ActiveSlot.DAILY)
                existing != null -> old.copy(game = daily.game(date, practice = true), activeSlot = ActiveSlot.REGULAR)
                else -> {
                    var records = old.dailyRecords
                    old.dailyGame?.takeIf { !it.finished }?.let { previous -> records = records +
                        (previous.dailyDate!! to DailyRecord(previous.dailyDate, DailyOutcome.ABANDONED, previous.elapsedMillis)) }
                    old.copy(dailyGame = daily.game(date), activeSlot = ActiveSlot.DAILY,
                        dailyRecords = records + (date to DailyRecord(date, DailyOutcome.IN_PROGRESS)))
                }
            }
        }
    }
    fun startDailyPractice(date: String) {
        cancelCalculation(); submit { it.copy(game = daily.game(date, practice = true), activeSlot = ActiveSlot.REGULAR) }
    }
    fun cell(intent: CellIntent) {
        val snapshot = data.value?.activeGame ?: return
        if (mutableBusy.value != null || snapshot.id != intent.gameId || snapshot.flagMode != intent.flagMode ||
            intent.fromTap && intent.action == CellAction.CHORD && data.value?.preferences?.tapToChord == false) return
        dismissHint()
        if (snapshot.generation == GenerationMode.NO_GUESS && snapshot.status == GameStatus.READY && intent.action == CellAction.REVEAL &&
            snapshot.cells.getOrNull(intent.index)?.flagged == false) {
            val token = ++calculationToken
            mutableBusy.value = "Preparing a no-guess board…"
            calculation = viewModelScope.launch {
                try {
                    val generated = withContext(Dispatchers.Default) { NoGuessGenerator.generate(snapshot, intent.index) }
                    if (token != calculationToken) return@launch
                    if (generated == null) {
                        mutableHintMessage.value = "A no-guess board could not be prepared within the limit. Retry your opening or choose classic."
                        mutableBusy.value = null
                    } else submit { old ->
                        if (token == calculationToken) mutableBusy.value = null
                        if (token == calculationToken && old.activeGame?.id == snapshot.id && old.activeGame?.cells == snapshot.cells)
                            old.withActiveGame(generated) else old
                    }
                } catch (_: CancellationException) { /* another game owns the screen now */ }
            }
            return
        }
        submit { old ->
            val g = old.activeGame ?: return@submit old
            if (g.id != intent.gameId || g.flagMode != intent.flagMode ||
                intent.fromTap && intent.action == CellAction.CHORD && !old.preferences.tapToChord) old
            else old.withActiveGame(engine.act(g, intent.index, intent.action, old.preferences.questionMarks))
        }
    }
    fun useClassic() { cancelCalculation(); submit { old -> old.withActiveGame(old.activeGame?.takeIf { it.status == GameStatus.READY }?.copy(generation = GenerationMode.CLASSIC) ?: old.activeGame) } }
    fun toggleFlagMode() { if (mutableBusy.value == null) submit { it.withActiveGame(it.activeGame?.let { g -> g.copy(flagMode = !g.flagMode) }) } }
    fun viewport(gameId: String, value: Viewport) = submit { if (it.activeGame?.id == gameId) it.withActiveGame(it.activeGame?.copy(viewport = value)) else it }
    fun preferences(value: Preferences) = submit { it.copy(preferences = value) }
    fun dismissRecovery() = submit { it.copy(recovered = false) }
    fun dismissMigration() = submit { it.copy(migrated = false) }
    fun undo() {
        dismissHint(); val id = data.value?.activeGame?.id
        submit { old -> if (old.activeGame?.id == id) old.withActiveGame(old.activeGame?.let(engine::undo)) else old }
    }
    fun requestHint() {
        val snapshot = data.value?.activeGame ?: return
        if (snapshot.mode != PlayMode.PRACTICE || snapshot.status != GameStatus.PLAYING || mutableBusy.value != null) return
        val token = ++calculationToken
        mutableBusy.value = "Finding a deduction…"
        calculation = viewModelScope.launch {
            try {
                val context = currentCoroutineContext()
                val found = withContext(Dispatchers.Default) { LogicSolver.hint(snapshot) { context.ensureActive(); true } }
                if (token != calculationToken) return@launch
                mutableBusy.value = null
                if (data.value?.activeGame?.id != snapshot.id || data.value?.activeGame?.cells != snapshot.cells) return@launch
                mutableHint.value = found
                mutableHintMessage.value = found?.explanation ?: "No supported logical deduction is available. Flags are not assumed to be correct."
                if (found != null) submit { old -> if (old.activeGame?.id == snapshot.id)
                    old.withActiveGame(old.activeGame!!.copy(hintsUsed = old.activeGame!!.hintsUsed + 1)) else old }
            } catch (_: CancellationException) { }
        }
    }
    fun applyHint() {
        val hint = mutableHint.value ?: return
        val id = data.value?.activeGame?.id
        dismissHint()
        submit { old ->
            val g = old.activeGame ?: return@submit old
            if (g.id != id || g.mode != PlayMode.PRACTICE || g.finished) return@submit old
            if (!hint.mine && g.cells[hint.index].revealed) return@submit old
            // One hint application is one undoable move, including removal of an incorrect flag.
            val plain = g.copy(mode = PlayMode.NORMAL)
            val next = if (hint.mine) { if (g.cells[hint.index].flagged) plain else plain.copy(cells = plain.cells.toMutableList().also {
                it[hint.index] = it[hint.index].copy(flagged = true, questioned = false) }) }
                else engine.reveal(if (g.cells[hint.index].flagged) engine.toggleFlag(plain, hint.index) else plain, hint.index)
            val delta = UndoMove(g.cells.indices.filter { g.cells[it].revealed != next.cells[it].revealed },
                g.cells.indices.filter { g.cells[it].flagged != next.cells[it].flagged }, g.status, g.explodedIndex,
                g.cells.indices.filter { g.cells[it].questioned != next.cells[it].questioned })
            old.withActiveGame(next.copy(mode = PlayMode.PRACTICE, undo = (g.undo + delta).takeLast(100)))
        }
    }
    fun exportBackup(uri: Uri) {
        submit(active = false)
        mutableBusy.value = "Exporting backup…"
        viewModelScope.launch {
            try {
                // A barrier command takes a timed snapshot after all previous game commands.
                val ready = CompletableDeferred<AppData>()
                submit { ready.complete(it); it }
                val snapshot = ready.await()
                withContext(Dispatchers.IO) {
                    val bytes = Backups.export(snapshot)
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        ?: throw IOException("Cannot open destination")
                }
            } catch (_: Exception) { mutableError.value = "Backup could not be written. Choose another location and try again." }
            finally { mutableBusy.value = null }
        }
    }
    fun importBackup(uri: Uri) {
        cancelCalculation(); mutableBusy.value = "Checking backup…"
        viewModelScope.launch {
            try { mutablePreview.value = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use(Backups::preview)
                    ?: throw IOException("Cannot open backup")
            } } catch (_: Exception) { mutableError.value = "This backup could not be read or is invalid. Your progress has not changed." }
            finally { mutableBusy.value = null }
        }
    }
    fun dismissPreview() { mutablePreview.value = null }
    fun confirmImport() {
        val backup = mutablePreview.value ?: return
        cancelCalculation()
        submit { backup.data.copy(recovered = false) }
        mutablePreview.value = null
    }
}
