package dev.glasstv.launcher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Current time, ticking exactly on minute boundaries (no per-second recomposition). */
@Composable
fun rememberMinuteClock(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        val now = LocalDateTime.now()
        value = now
        val next = now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        delay(java.time.Duration.between(now, next).toMillis() + 50)
    }
}
