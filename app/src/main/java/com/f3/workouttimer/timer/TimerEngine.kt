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
    private var job: Job? = null

    fun start() {
        if (phase != RunPhase.READY || intervals.isEmpty()) return
        sounds.setVoiceByName(timer.voiceName)
        job = scope.launch {
            phase = RunPhase.LEAD_IN
            sounds.speak(timer.opening)
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
            sounds.speak(timer.closing)
            onFinished()
        }
    }

    /** The next work interval after [afterIndex], for "up next" cues. */
    fun nextWork(afterIndex: Int = currentIndex): Interval? =
        intervals.drop(afterIndex + 1).firstOrNull { it.type == StageType.WORK }

    /**
     * What comes after [afterIndex]: the coming exercise, or the coming block
     * when that block has no exercises of its own. Blank when there is nothing
     * useful to say — the next work interval is more of the same, or the
     * workout is over.
     */
    fun upNextLabel(afterIndex: Int = currentIndex): String {
        val current = intervals.getOrNull(afterIndex) ?: return ""
        val next = nextWork(afterIndex) ?: return ""
        val worthShowing = next.exercise.isNotBlank() || next.blockIndex != current.blockIndex
        return if (worthShowing) next.displayLabel else ""
    }

    private fun announcementFor(index: Int): String {
        val interval = intervals[index]
        val base = interval.announcement
        // Name the block when the run crosses into a new one.
        val startsBlock = index == 0 || intervals[index - 1].blockIndex != interval.blockIndex
        val withBlock =
            if (startsBlock && interval.blockName.isNotBlank() &&
                !base.equals(interval.blockName, ignoreCase = true)
            ) {
                "${interval.blockName}. $base"
            } else {
                base
            }
        val isBreak = interval.type == StageType.REST || interval.type == StageType.TRANSITION
        if (timer.announceNextExercise && isBreak) {
            val next = upNextLabel(index)
            if (next.isNotBlank()) return "$withBlock. Next up: $next"
        }
        return withBlock
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
        }
        remainingMs = 0
        skipRequested = false
    }
}
