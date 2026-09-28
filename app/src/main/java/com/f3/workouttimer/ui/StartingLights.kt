package com.f3.workouttimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.f3.workouttimer.ui.theme.F3Black
import com.f3.workouttimer.ui.theme.F3White
import kotlinx.coroutines.delay

/** How far through the starting sequence the lights are. */
private enum class LightPhase { RED, YELLOW, GREEN }

val LightRed = Color(0xFFE53935)
val LightYellow = Color(0xFFFDD835)
val LightGreen = Color(0xFF43A047)

private const val LIGHT_MS = 1_700L
private const val GREEN_HOLD_MS = 2_800L

/**
 * A starting countdown: the whole screen goes red, then yellow, then green.
 * Draw it over whatever is on screen; it shows itself when [trigger] changes
 * and clears when the sequence ends.
 *
 * Filling the screen means the PAX can read it from the ground, across the
 * lot, without looking for a small light.
 *
 * The sound is left to the caller, since during a workout it has to go
 * through the run's own voice and outside one there is no run to ask.
 */
@Composable
fun StartingLightsOverlay(
    trigger: Int,
    onLight: () -> Unit,
    onGo: () -> Unit,
) {
    var phase by remember { mutableStateOf<LightPhase?>(null) }

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        phase = LightPhase.RED
        onLight()
        delay(LIGHT_MS)
        phase = LightPhase.YELLOW
        onLight()
        delay(LIGHT_MS)
        phase = LightPhase.GREEN
        onLight()
        onGo()
        delay(GREEN_HOLD_MS)
        phase = null
    }

    phase?.let { Lights(it) }
}

/** The one place colour earns its keep in an otherwise black-and-white app. */
@Composable
private fun Lights(phase: LightPhase) {
    // Snapping between colours reads as a countdown; fading would blur them.
    val colour = when (phase) {
        LightPhase.RED -> LightRed
        LightPhase.YELLOW -> LightYellow
        LightPhase.GREEN -> LightGreen
    }
    val label = when (phase) {
        LightPhase.RED -> "READY"
        LightPhase.YELLOW -> "SET"
        LightPhase.GREEN -> "GO!"
    }
    // Yellow is far too bright to put white on.
    val ink = if (phase == LightPhase.YELLOW) F3Black else F3White

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colour),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = ink,
            fontSize = if (phase == LightPhase.GREEN) 104.sp else 76.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 8.sp,
        )
    }
}
