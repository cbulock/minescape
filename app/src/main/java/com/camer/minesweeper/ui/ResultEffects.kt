package com.camer.minesweeper.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import com.camer.minesweeper.game.BoardGeometry
import com.camer.minesweeper.game.Game
import com.camer.minesweeper.game.GameStatus
import kotlinx.coroutines.currentCoroutineContext
import kotlin.math.*
import kotlin.random.Random

private enum class Burst { CONFETTI, EXPLOSION }
private data class Particle(val direction: Float, val speed: Float, val lift: Float, val delay: Float,
    val spin: Float, val phase: Float, val width: Float, val height: Float)

/** A drawing-only layer: no pointer handlers, geometry changes, or game commands. */
@Composable
internal fun ResultEffects(game: Game, geometry: BoardGeometry, enabled: Boolean) {
    // Save the observed status, not the animation: completed saves/recreation never replay a burst.
    var previousStatus by rememberSaveable(game.id) { mutableStateOf(game.status) }
    var burst by remember(game.id) { mutableStateOf<Burst?>(null) }
    val progress = remember(game.id) { Animatable(1f) }
    val particles = remember(game.id) {
        val random = Random(game.id.hashCode())
        List(130) { i -> Particle(if (i % 2 == 0) 1f else -1f,
            .3f + random.nextFloat() * .55f, 1.05f + random.nextFloat() * .65f,
            random.nextFloat() * .24f, (random.nextFloat() - .5f) * 900f,
            random.nextFloat() * 2f * PI.toFloat(), 4f + random.nextFloat() * 4f,
            3f + random.nextFloat() * 7f) }
    }
    LaunchedEffect(game.id, game.status, enabled) {
        val previous = previousStatus
        previousStatus = game.status
        burst = null
        progress.snapTo(1f)
        val fresh = previous == GameStatus.PLAYING || previous == GameStatus.READY
        val kind = when {
            !fresh || !enabled -> null
            game.status == GameStatus.WON -> Burst.CONFETTI
            game.status == GameStatus.LOST && game.explodedIndex in game.cells.indices -> Burst.EXPLOSION
            else -> null
        }
        // Animatable also follows live changes to the system animation scale during playback.
        if (kind == null || (currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f) <= 0f) return@LaunchedEffect
        progress.snapTo(0f)
        burst = kind
        try { progress.animateTo(1f, tween(if (kind == Burst.CONFETTI) 2800 else 950, easing = LinearEasing)) }
        finally { burst = null }
    }
    val kind = burst ?: return
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics {
        testTag = if (kind == Burst.CONFETTI) "result-confetti" else "result-explosion"
    }) {
        val fraction = progress.value
        if (fraction >= 1f) return@Canvas
        when (kind) {
            Burst.CONFETTI -> confetti(fraction, particles)
            Burst.EXPLOSION -> {
                val origin = Offset((geometry.left + (game.explodedIndex % game.config.columns + .5f) * geometry.cell) * density,
                    (geometry.top + (game.explodedIndex / game.config.columns + .5f) * geometry.cell) * density)
                explosion(fraction, origin, geometry.cell * density, particles)
            }
        }
    }
}

private val confettiColors = listOf(Violet, Mint, Coral, Color(0xFFFFD36F), Color(0xFF79CFFF), Color(0xFFF7A8D5))

private fun DrawScope.confetti(progress: Float, particles: List<Particle>) {
    val seconds = progress * 2.8f
    val alpha = ((1f - progress) / .28f).coerceIn(0f, 1f)
    particles.forEachIndexed { i, particle ->
        val t = seconds - particle.delay
        if (t < 0f) return@forEachIndexed
        val travel = (1f - exp(-t * 1.2f)) / 1.2f
        val x = (if (particle.direction > 0) .02f else .98f) * size.width +
            particle.direction * particle.speed * size.width * travel + sin(t * 8f + particle.phase) * 8f * density
        val y = size.height * (.94f - particle.lift * t + .675f * t * t)
        val center = Offset(x, y)
        val width = particle.width * density
        val height = particle.height * density
        if (x < -height || x > size.width + height || y < -height || y > size.height + height) return@forEachIndexed
        val color = confettiColors[i % confettiColors.size].copy(alpha = alpha)
        rotate(particle.phase * 180f / PI.toFloat() + t * particle.spin, center) {
            when (i % 6) {
                0 -> drawCircle(color, width * .45f, center)
                1 -> {
                    drawLine(color, center - Offset(width, 0f), center + Offset(width, 0f), 2f * density)
                    drawLine(color, center - Offset(0f, width), center + Offset(0f, width), 2f * density)
                }
                else -> drawRect(color, center - Offset(width / 2, height / 2), Size(width, height * (.35f + .65f * abs(cos(t * 9f + particle.phase)))))
            }
        }
    }
}

private fun DrawScope.explosion(progress: Float, origin: Offset, cell: Float, particles: List<Particle>) {
    val radius = max(cell * 1.5f, 44f * density).coerceAtMost(120f * density)
    val fade = (1f - progress).pow(2)
    val expansion = 1f - (1f - progress).pow(3)
    // A local warm glow, rather than a full-screen flash.
    if (progress < .5f) {
        drawCircle(Color(0xFFFFD36F).copy(alpha = (1f - progress * 2f) * .35f), radius * (.2f + expansion * .5f), origin)
        val star = Path()
        repeat(20) { i ->
            val angle = i * PI.toFloat() / 10f
            val r = radius * (.15f + expansion * .55f) * if (i % 2 == 0) 1f else .42f
            val point = origin + Offset(cos(angle), sin(angle)) * r
            if (i == 0) star.moveTo(point.x, point.y) else star.lineTo(point.x, point.y)
        }
        star.close()
        drawPath(star, Color(0xFFFFC46B).copy(alpha = (1f - progress * 2f) * .9f))
    }
    drawCircle(Coral.copy(alpha = fade), radius * expansion, origin, style = Stroke(3f * density * (1f - progress) + density))
    drawCircle(Color(0xFFFFD36F).copy(alpha = fade * .75f), radius * expansion * .75f, origin, style = Stroke(1.5f * density))
    // Small smoke puffs drift out and dissolve behind the sparks.
    repeat(9) { i ->
        val angle = i * 2f * PI.toFloat() / 9f
        val center = origin + Offset(cos(angle), sin(angle)) * radius * expansion * .55f - Offset(0f, progress * 18f * density)
        drawCircle(Ink.copy(alpha = sin(progress * PI.toFloat()) * .2f), radius * (.07f + progress * .15f), center)
    }
    repeat(32) { i ->
        val angle = i * 2f * PI.toFloat() / 32f + particles[i].phase * .12f
        val distance = radius * expansion * (.65f + particles[i].speed)
        val direction = Offset(cos(angle), sin(angle))
        val tip = origin + direction * distance + Offset(0f, progress * progress * radius * .3f)
        val color = (if (i % 3 == 0) Coral else if (i % 3 == 1) Color(0xFFFFD36F) else Color(0xFFFFA557)).copy(alpha = fade)
        drawLine(color, tip - direction * (4f + 9f * (1f - progress)) * density, tip, 2.5f * density)
        drawCircle(color, 1.8f * density, tip)
    }
}
