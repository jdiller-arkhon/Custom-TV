package dev.glasstv.launcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset

/** One motion language for every screen change: a soft spring "lift" in, a quick settle out. */
object Motion {
    /** Apple-style ease-out: fast start, long gentle landing. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    private val lift = spring<IntOffset>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
    private val grow = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

    fun sheetIn(reduce: Boolean): EnterTransition =
        if (reduce) fadeIn(tween(180))
        else fadeIn(tween(260, easing = Emphasized)) + scaleIn(grow, initialScale = 0.94f) + slideInVertically(lift) { it / 14 }

    fun sheetOut(reduce: Boolean): ExitTransition =
        if (reduce) fadeOut(tween(140))
        else fadeOut(tween(180)) + scaleOut(tween(220, easing = Emphasized), targetScale = 0.97f) + slideOutVertically(tween(220)) { it / 24 }

    /** Going deeper (picker → game) slides left; going back slides right. */
    fun forward(reduce: Boolean, deeper: Boolean): ContentTransform {
        if (reduce) return fadeIn(tween(180)) togetherWith fadeOut(tween(120))
        val d = if (deeper) 1 else -1
        return (fadeIn(tween(280, 60, Emphasized)) + slideInHorizontally(lift) { d * it / 8 } + scaleIn(grow, 0.97f)) togetherWith
            (fadeOut(tween(160)) + slideOutHorizontally(tween(260, easing = Emphasized)) { -d * it / 10 })
    }
}

/**
 * Shows [value]'s sheet with the shared lift-in motion, and keeps drawing the last value while it
 * animates out (so closing an app doesn't blink to empty).
 */
@Composable
fun <T : Any> SheetHost(value: T?, reduceMotion: Boolean, modifier: Modifier = Modifier, content: @Composable AnimatedVisibilityScope.(T) -> Unit) {
    var last by remember { mutableStateOf(value) }
    if (value != null) last = value
    AnimatedVisibility(value != null, modifier, enter = Motion.sheetIn(reduceMotion), exit = Motion.sheetOut(reduceMotion)) {
        last?.let { content(it) }
    }
}

@Composable
fun SheetHost(visible: Boolean, reduceMotion: Boolean, modifier: Modifier = Modifier, content: @Composable AnimatedVisibilityScope.() -> Unit) =
    SheetHost(if (visible) Unit else null, reduceMotion, modifier) { content() }
