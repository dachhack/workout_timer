package com.f3.workouttimer.timer

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.f3.workouttimer.audio.WorkoutSounds
import com.f3.workouttimer.model.Interval
import com.f3.workouttimer.model.StageType
import com.f3.workouttimer.model.WorkoutTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.ceil

enum class RunPhase { READY, LEAD_IN, RUNNING, FINISHED }

private const val LEAD_IN_SECONDS = 5

/**
 * Drives one workout run. All mutable properties are Compose state, so the run
 * screen recomposes as the clock ticks.
 */
class TimerEngine(
    val timer: WorkoutTimer,
    private val scope: CoroutineScope,
    private val sounds: WorkoutSounds,
    private val onFinished: () -> Unit = {},
) {
    val intervals: List<Interval> = timer.intervals()
    val totalSeconds: Int = intervals.sumOf { it.seconds }

    var phase by mutableStateOf(RunPhase.READY)
        private set
    var currentIndex by mutableIntStateOf(-1)
        private set
    var remainingMs by mutableLongStateOf(0L)
        private set
    var isPaused by mutableStateOf(false)
        private set

    val currentInterval: Interval?
        get() = intervals.getOrNull(currentIndex)

    /** Seconds of the workout completed so far, for the overall progress bar. */
    val elapsedSeconds: Int
        get() {
            if (phase == RunPhase.FINISHED) return totalSeconds
            val idx = currentIndex
            if (idx < 0) return 0
            val before = intervals.take(idx).sumOf { it.seconds }
            val inCurrent = intervals[idx].seconds - ceil(remainingMs / 1000.0).toInt()
            return before + inCurrent.coerceAtLeast(0)
        }

    private val paused = MutableStateFlow(false)
    private var skipRequested = false
    private var halfwayAnnounced = false
    private var job: Job? = null

    fun start() {
        if (phase != RunPhase.READY || intervals.isEmpty()) return
        sounds.setVoiceByName(timer.voiceName)
        job = scope.launch {
            phase = RunPhase.LEAD_IN
            sounds.speak("Get ready")
            countdown(LEAD_IN_SECONDS)
            for (i in intervals.indices) {
                currentIndex = i
                phase = RunPhase.RUNNING
                sounds.stageBeep()
                sounds.speak(announcementFor(i))
                countdown(intervals[i].seconds)
            }
            phase = RunPhase.FINISHED
            sounds.stageBeep()
            sounds.speak("Workout complete. Nice work.")
            onFinished()
        }
    }

    /** The exercise of the next work interval after [afterIndex], for "up next" cues. */
    fun nextExercise(afterIndex: Int = currentIndex): String =
        intervals.drop(afterIndex + 1)
            .firstOrNull { it.type == StageType.WORK && it.exercise.isNotBlank() }
            ?.exercise ?: ""

    private fun announcementFor(index: Int): String {
        val interval = intervals[index]
        val base = when {
            interval.type == StageType.WORK && interval.exercise.isNotBlank() ->
                interval.exercise
            interval.type == StageType.BLOCK ->
                interval.message.ifBlank { interval.name.ifBlank { interval.type.defaultAnnouncement } }
            else ->
                interval.message.ifBlank { interval.type.defaultAnnouncement }
        }
        if (interval.type == StageType.TRANSITION) {
            val next = nextExercise(index)
            if (next.isNotBlank()) return "$base. Next up: $next"
        }
        return base
    }

    fun togglePause() {
        if (phase != RunPhase.RUNNING && phase != RunPhase.LEAD_IN) return
        isPaused = !isPaused
        paused.value = isPaused
    }

    fun skip() {
        if (phase == RunPhase.RUNNING || phase == RunPhase.LEAD_IN) {
            skipRequested = true
            if (isPaused) togglePause()
        }
    }

    fun stop() {
        job?.cancel()
    }

    private suspend fun countdown(seconds: Int) {
        skipRequested = false
        var remaining = seconds * 1000L
        remainingMs = remaining
        var lastWhole = seconds
        while (remaining > 0 && !skipRequested) {
            paused.first { !it }
            val tickStart = SystemClock.elapsedRealtime()
            delay(100)
            if (paused.value) continue
            remaining = (remaining - (SystemClock.elapsedRealtime() - tickStart)).coerceAtLeast(0)
            remainingMs = remaining
            val whole = ceil(remaining / 1000.0).toInt()
            if (whole < lastWhole) {
                lastWhole = whole
                if (whole in 1..3) sounds.countdownBeep()
            }
            if (timer.announceHalfway && !halfwayAnnounced &&
                phase == RunPhase.RUNNING && elapsedSeconds * 2 >= totalSeconds
            ) {
                halfwayAnnounced = true
                sounds.speak("Halfway there. Keep pushing.")
            }
        }
        remainingMs = 0
        skipRequested = false
    }
}
