package com.f3.workouttimer.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.f3.workouttimer.model.StageType
import com.f3.workouttimer.model.formatDuration
import com.f3.workouttimer.model.splitMovements
import com.f3.workouttimer.timer.RunPhase
import com.f3.workouttimer.timer.TimerEngine
import com.f3.workouttimer.timer.TimerService
import com.f3.workouttimer.ui.theme.F3Black
import com.f3.workouttimer.ui.theme.F3DarkGray
import com.f3.workouttimer.ui.theme.F3Gray
import com.f3.workouttimer.ui.theme.F3White
import kotlin.math.ceil

@Composable
fun RunScreen(timerId: String, onExit: () -> Unit) {
    val context = LocalContext.current

    // Ask for notification permission so the foreground-service notification shows.
    if (Build.VERSION.SDK_INT >= 33) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {}
        LaunchedEffect(Unit) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // The run lives in TimerService so it survives backgrounding; bind to observe it.
    var service by remember { mutableStateOf<TimerService?>(null) }
    DisposableEffect(timerId) {
        TimerService.start(context, timerId)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as TimerService.LocalBinder).service
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
        context.bindService(
            Intent(context, TimerService::class.java), connection, Context.BIND_AUTO_CREATE
        )
        onDispose { context.unbindService(connection) }
    }

    // Keep the screen awake while this screen is up.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val engine = service?.engine
    if (engine == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(F3Black),
            contentAlignment = Alignment.Center,
        ) {
            Text("STARTING…", color = F3Gray, letterSpacing = 4.sp, fontWeight = FontWeight.Bold)
        }
        // Backing out now just backgrounds the run; the home banner offers resume.
        BackHandler { onExit() }
        return
    }

    RunContent(engine = engine, onExit = onExit)
}

@Composable
private fun RunContent(engine: TimerEngine, onExit: () -> Unit) {
    val context = LocalContext.current
    var confirmEnd by remember { mutableStateOf(false) }

    val endWorkout = {
        TimerService.stop(context)
        onExit()
    }
    // Back backgrounds the run (it keeps going in the service); X ends it.
    BackHandler { onExit() }

    val interval = engine.currentInterval
    val stageLabel = when (engine.phase) {
        RunPhase.READY, RunPhase.LEAD_IN -> "GET READY"
        RunPhase.FINISHED -> "DONE"
        RunPhase.RUNNING -> interval?.displayLabel?.uppercase() ?: ""
    }

    // WORK inverts the screen to white for maximum contrast mid-beatdown.
    val isWork = engine.phase == RunPhase.RUNNING && interval?.type == StageType.WORK
    val background by animateColorAsState(if (isWork) F3White else F3Black, label = "bg")
    val foreground by animateColorAsState(if (isWork) F3Black else F3White, label = "fg")

    val secondsLeft = ceil(engine.remainingMs / 1000.0).toInt()
    val upNext = if (engine.phase == RunPhase.RUNNING && !isWork) engine.upNextLabel() else ""
    val headlineMovements =
        if (engine.phase == RunPhase.RUNNING && interval != null) interval.movements
        else listOf(stageLabel)
    // The headline already carries the exercise or block name, so the context
    // line above it only names the block when that adds something.
    val blockLine = interval?.takeIf { engine.phase == RunPhase.RUNNING }?.let { iv ->
        val position = if (iv.blockCount > 1) "BLOCK ${iv.blockIndex + 1} / ${iv.blockCount}" else ""
        val named = iv.blockName.takeIf {
            it.isNotBlank() && !it.equals(iv.displayLabel, ignoreCase = true)
        }?.uppercase()
        listOfNotNull(named, position.ifBlank { null }).joinToString(" · ")
    }.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .safeDrawingPadding(),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = {
                    if (engine.phase == RunPhase.FINISHED) endWorkout() else confirmEnd = true
                },
                modifier = Modifier.align(Alignment.CenterStart).padding(8.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = "End workout", tint = foreground)
            }
            Text(
                text = engine.timer.name,
                color = F3Gray,
                fontSize = 14.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // The middle takes whatever room is left between the header and the
        // controls, so a long exercise name can never push into them.
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (blockLine.isNotBlank()) {
                Text(
                    text = blockLine,
                    color = if (isWork) F3DarkGray else F3Gray,
                    fontSize = 14.sp,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            if (engine.phase == RunPhase.RUNNING && interval != null && interval.roundsInBlock > 1) {
                Text(
                    text = "ROUND ${interval.round} / ${interval.roundsInBlock}",
                    color = if (isWork) F3DarkGray else F3Gray,
                    fontSize = 16.sp,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            // A compound station ("5 Squats, 5 Merkins, 5 Sit-ups") stacks so the
            // PAX can read every movement at a glance.
            MovementStack(
                movements = headlineMovements,
                color = foreground,
                maxSize = 36.sp,
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
                    // Give the coming exercise room to breathe during a break.
                    fontSize = if (upNext.isNotBlank()) 88.sp else 120.sp,
                    fontWeight = FontWeight.Black,
                )
                if (upNext.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "NEXT UP",
                        color = F3Gray,
                        fontSize = 14.sp,
                        letterSpacing = 4.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    MovementStack(
                        movements = splitMovements(upNext),
                        color = foreground,
                        maxSize = 46.sp,
                    )
                }
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
            modifier = Modifier.fillMaxWidth().padding(24.dp),
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
                    IconButton(onClick = endWorkout) {
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

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End workout?") },
            text = { Text("The timer will stop. Back out instead to keep it running in the background.") },
            confirmButton = {
                TextButton(onClick = { confirmEnd = false; endWorkout() }) { Text("End it") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnd = false }) { Text("Keep going") }
            },
        )
    }
}

/**
 * Renders a station's movements one per line, sized to fit however many there
 * are and however long they run. One movement just reads as a headline.
 */
@Composable
private fun MovementStack(
    movements: List<String>,
    color: Color,
    maxSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    if (movements.isEmpty()) return
    val longest = movements.maxOf { it.length }
    val byCount = when (movements.size) {
        1 -> 1.0f
        2 -> 0.85f
        3 -> 0.72f
        4 -> 0.6f
        else -> 0.5f
    }
    val byLength = when {
        longest > 26 -> 0.58f
        longest > 18 -> 0.72f
        longest > 12 -> 0.85f
        else -> 1.0f
    }
    val size = maxSize * minOf(byCount, byLength)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        movements.forEach { movement ->
            Text(
                text = movement.uppercase(),
                color = color,
                fontSize = size,
                lineHeight = size * 1.15f,
                fontWeight = FontWeight.Black,
                letterSpacing = if (movements.size > 1) 1.sp else 4.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
