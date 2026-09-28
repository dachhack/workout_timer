package com.f3.workouttimer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.f3.workouttimer.ui.theme.F3Black
import com.f3.workouttimer.ui.theme.F3DarkGray
import com.f3.workouttimer.ui.theme.F3Gray
import kotlinx.coroutines.delay

/** How far through the starting sequence the lights are. */
private enum class LightPhase { RED, YELLOW, GREEN }

val LightRed = Color(0xFFE53935)
val LightYellow = Color(0xFFFDD835)
val LightGreen = Color(0xFF43A047)
private val LightOff = Color(0xFF1E1E1E)

private const val LIGHT_MS = 850L
private const val GREEN_HOLD_MS = 1400L

/**
 * A drag-strip tree: red, then yellow, then green and GO. Draw it over
 * whatever is on screen; it shows itself when [trigger] changes and clears
 * when the sequence ends.
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(F3Black.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Lamp(colour = LightRed, lit = true)
            Spacer(Modifier.height(20.dp))
            Lamp(colour = LightYellow, lit = phase != LightPhase.RED)
            Spacer(Modifier.height(20.dp))
            Lamp(colour = LightGreen, lit = phase == LightPhase.GREEN)
            Spacer(Modifier.height(28.dp))
            Text(
                text = if (phase == LightPhase.GREEN) "GO!" else "READY",
                color = if (phase == LightPhase.GREEN) LightGreen else F3Gray,
                fontSize = if (phase == LightPhase.GREEN) 56.sp else 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp,
            )
        }
    }
}

@Composable
private fun Lamp(colour: Color, lit: Boolean) {
    val shown by animateColorAsState(if (lit) colour else LightOff, label = "lamp")
    Box(
        modifier = Modifier
            .size(96.dp)
            .background(shown, CircleShape)
            .border(3.dp, if (lit) colour else F3DarkGray, CircleShape)
    )
}
