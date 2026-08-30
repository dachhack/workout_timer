package com.f3.workouttimer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.f3.workouttimer.audio.WorkoutSounds
import com.f3.workouttimer.data.TimerRepository
import com.f3.workouttimer.model.StageType
import com.f3.workouttimer.model.WorkoutTimer
import com.f3.workouttimer.model.formatDuration
import com.f3.workouttimer.timer.RunPhase
import com.f3.workouttimer.timer.TimerEngine
import com.f3.workouttimer.ui.theme.F3Black
import com.f3.workouttimer.ui.theme.F3DarkGray
import com.f3.workouttimer.ui.theme.F3Gray
import com.f3.workouttimer.ui.theme.F3White
import kotlinx.coroutines.flow.first
import kotlin.math.ceil

@Composable
fun RunScreen(timerId: String, onExit: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { TimerRepository.get(context) }

    val timer by produceState<WorkoutTimer?>(initialValue = null) {
        value = repo.timers.first().find { it.id == timerId }
    }

    val loaded = timer ?: return
    RunContent(timer = loaded, onExit = onExit)
}

@Composable
private fun RunContent(timer: WorkoutTimer, onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sounds = remember { WorkoutSounds(context) }
    val engine = remember(timer.id) { TimerEngine(timer, scope, sounds) }

    LaunchedEffect(engine) { engine.start() }
    DisposableEffect(Unit) {
        onDispose {
            engine.stop()
            sounds.release()
        }
    }

    // Keep the screen awake for the whole workout.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val interval = engine.currentInterval
    val stageLabel = when (engine.phase) {
        RunPhase.READY, RunPhase.LEAD_IN -> "GET READY"
        RunPhase.FINISHED -> "DONE"
        RunPhase.RUNNING -> interval?.type?.label ?: ""
    }

    // WORK inverts the screen to white for maximum contrast mid-beatdown.
    val isWork = engine.phase == RunPhase.RUNNING && interval?.type == StageType.WORK
    val background by animateColorAsState(if (isWork) F3White else F3Black, label = "bg")
    val foreground by animateColorAsState(if (isWork) F3Black else F3White, label = "fg")

    val secondsLeft = ceil(engine.remainingMs / 1000.0).toInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .safeDrawingPadding(),
    ) {
        IconButton(
            onClick = onExit,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) {
            Icon(Icons.Default.Close, contentDescription = "Exit", tint = foreground)
        }
        Text(
            text = timer.name,
            color = F3Gray,
            fontSize = 14.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
        )

        Column(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (engine.phase == RunPhase.RUNNING && interval != null) {
                Text(
                    text = "ROUND ${interval.round} / ${timer.rounds}",
                    color = if (isWork) F3DarkGray else F3Gray,
                    fontSize = 16.sp,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = stageLabel,
                color = foreground,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp,
            )
            if (engine.phase == RunPhase.FINISHED) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = formatDuration(engine.totalSeconds),
                    color = foreground,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(8.dp))
                Text("Beatdown complete", color = F3Gray, fontSize = 16.sp)
            } else {
                Text(
                    text = formatDuration(secondsLeft),
                    color = foreground,
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Black,
                )
                if (engine.isPaused) {
                    Text(
                        text = "PAUSED",
                        color = F3Gray,
                        fontSize = 18.sp,
                        letterSpacing = 4.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LinearProgressIndicator(
                progress = {
                    if (engine.totalSeconds == 0) 0f
                    else engine.elapsedSeconds.toFloat() / engine.totalSeconds
                },
                color = foreground,
                trackColor = if (isWork) Color(0xFFDDDDDD) else F3DarkGray,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${formatDuration(engine.elapsedSeconds)} / ${formatDuration(engine.totalSeconds)}",
                color = F3Gray,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))
            if (engine.phase == RunPhase.FINISHED) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(foreground, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = onExit) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Done",
                            tint = background,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(foreground, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(onClick = { engine.togglePause() }, modifier = Modifier.size(80.dp)) {
                            Icon(
                                if (engine.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (engine.isPaused) "Resume" else "Pause",
                                tint = background,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(F3DarkGray, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(onClick = { engine.skip() }, modifier = Modifier.size(56.dp)) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Skip stage",
                                tint = F3White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
