package com.cbulock.minescape.ui

import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.cbulock.minescape.R
import com.cbulock.minescape.game.Difficulty
import com.cbulock.minescape.game.Hint
import com.cbulock.minescape.game.HintReason
import java.util.IdentityHashMap
import java.util.Locale

/** Transient presentation state; never written to saves or backups. */
sealed interface UiMessage {
    val cancellable: Boolean get() = false
    data class Resource(@param:StringRes val id: Int, override val cancellable: Boolean = false) : UiMessage
    data class Deduction(val hint: Hint) : UiMessage
}

/** A resource configuration snapshot, safe to also use in callbacks and background rendering. */
class TextResources(val resources: Resources) {
    val locale: Locale get() = resources.configuration.locales[0] ?: Locale.US
    operator fun invoke(@StringRes id: Int, vararg arguments: Any?): String =
        if (arguments.isEmpty()) resources.getString(id) else resources.getString(id, *arguments)

    fun quantity(@PluralsRes id: Int, count: Int, vararg arguments: Any): String =
        resources.getQuantityString(id, count, *arguments)

    fun difficulty(value: Difficulty): String = invoke(when (value) {
        Difficulty.BEGINNER -> R.string.difficulty_beginner
        Difficulty.INTERMEDIATE -> R.string.difficulty_intermediate
        Difficulty.EXPERT -> R.string.difficulty_expert
        Difficulty.CUSTOM -> R.string.difficulty_custom
    })

    fun message(value: UiMessage): String = when (value) {
        is UiMessage.Resource -> invoke(value.id)
        is UiMessage.Deduction -> hint(value.hint)
    }

    fun time(millis: Long): String {
        val seconds = millis / 1000
        return String.format(locale, "%02d:%02d", seconds / 60, seconds % 60)
    }

    fun format(pattern: String, vararg arguments: Any?): String = String.format(locale, pattern, *arguments)

    fun backupSummary(wins: Int, losses: Int, attempts: Int): String = invoke(R.string.backup_summary,
        quantity(R.plurals.wins_count, wins, wins), quantity(R.plurals.losses_count, losses, losses),
        quantity(R.plurals.daily_attempts_count, attempts, attempts))

    fun hint(value: Hint): String {
        // Preserve the solver's former 600-character cap at every constraint, without
        // recursive rendering or exponential work when comparisons share evidence.
        val rendered = IdentityHashMap<HintReason, String>()
        val pending = ArrayDeque<Pair<HintReason, Boolean>>()
        pending.addLast(value.reason to false)
        while (pending.isNotEmpty()) {
            val (reason, visited) = pending.removeLast()
            if (rendered.containsKey(reason)) continue
            if (reason is HintReason.Comparison && !visited) {
                pending.addLast(reason to true)
                pending.addLast(reason.small to false)
                pending.addLast(reason.large to false)
            } else {
                rendered[reason] = when (reason) {
                    is HintReason.Clue -> invoke(R.string.hint_clue, reason.row, reason.column, reason.adjacent)
                    is HintReason.BoardTotal -> quantity(R.plurals.hint_board_total, reason.mines, reason.mines)
                    is HintReason.Comparison -> invoke(R.string.hint_comparison, rendered.getValue(reason.large), rendered.getValue(reason.small))
                }.take(600)
            }
        }
        val conclusion = quantity(if (value.mine) R.plurals.hint_mine else R.plurals.hint_safe, value.coveredSquares, value.coveredSquares)
        return invoke(R.string.hint_explanation, rendered.getValue(value.reason), conclusion)
    }
}

@Composable
internal fun textResources(): TextResources {
    val context = LocalContext.current
    val configuration = Configuration(LocalConfiguration.current)
    return remember(context, configuration) {
        TextResources(context.createConfigurationContext(configuration).resources)
    }
}
