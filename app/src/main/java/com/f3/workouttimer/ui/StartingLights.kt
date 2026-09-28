package com.f3.workouttimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.f3.workouttimer.ui.theme.F3Black
import com.f3.workouttimer.ui.theme.F3White
import kotlinx.coroutines.delay

/**
 * ARMED holds on red until someone says go; the rest is the countdown itself.
 */
private enum class LightPhase { ARMED, RED, YELLOW, GREEN }

val LightRed = Color(0xFFE53935)
val LightYellow = Color(0xFFFDD835)
val LightGreen = Color(0xFF43A047)

private const val LIGHT_MS = 1_700L
private const val GREEN_HOLD_MS = 2_800L

/**
 * The starting countdown, filling the screen with each colour in turn.
 *
 * With [armed] it opens on red and waits — for the GO button, or for [goSignal]
 * to change, which is how a spoken "here we go" reaches in. Without it the
 * count starts the moment it appears, which is what a run in progress wants.
 */
@Composable
fun StartingLightsOverlay(
    visible: Boolean,
    armed: Boolean,
    goSignal: Int,
    onLight: () -> Unit,
    onGo: () -> Unit,
    onDismiss: () -> Unit,
    listening: Boolean = false,
) {
    if (!visible) return

    var phase by remember { mutableStateOf(if (armed) LightPhase.ARMED else LightPhase.RED) }
    var started by remember { mutableStateOf(!armed) }

    // Only a change counts; the signal arrives already non-zero if the lights
    // were used earlier in this session.
    val goSignalAtOpen = remember { goSignal }
    LaunchedEffect(goSignal) {
        if (goSignal != goSignalAtOpen) started = true
    }

    LaunchedEffect(started) {
        if (!started) return@LaunchedEffect
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
        onDismiss()
    }

    Lights(
        phase = phase,
        listening = listening,
        onGo = { started = true },
        onCancel = onDismiss,
    )
}

/** The one place colour earns its keep in an otherwise black-and-white app. */
@Composable
private fun Lights(
    phase: LightPhase,
    listening: Boolean,
    onGo: () -> Unit,
    onCancel: () -> Unit,
) {
    // Snapping between colours reads as a countdown; fading would blur them.
    val colour = when (phase) {
        LightPhase.ARMED, LightPhase.RED -> LightRed
        LightPhase.YELLOW -> LightYellow
        LightPhase.GREEN -> LightGreen
    }
    val label = when (phase) {
        LightPhase.ARMED -> "READY"
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                color = ink,
                fontSize = if (phase == LightPhase.GREEN) 104.sp else 76.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 8.sp,
            )

            if (phase == LightPhase.ARMED) {
                Spacer(Modifier.height(40.dp))
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(F3White)
                        .clickable(onClick = onGo),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "GO",
                        color = LightRed,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = if (listening) "or say \"here we go\"" else "tap GO to start",
                    color = F3White,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = onCancel) {
                    Text("CANCEL", color = F3White, letterSpacing = 2.sp)
                }
            }
        }
    }
}
