package com.f3.workouttimer.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import com.f3.workouttimer.MainActivity
import com.f3.workouttimer.R
import com.f3.workouttimer.audio.WorkoutSounds
import com.f3.workouttimer.data.TimerRepository
import com.f3.workouttimer.model.formatDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * Foreground service that owns a workout run, so the timer, beeps, and speech
 * keep going when the screen locks or the app is backgrounded. The run screen
 * binds to it and drives the same [TimerEngine] the notification reports on.
 */
class TimerService : Service() {

    inner class LocalBinder : Binder() {
        val service: TimerService get() = this@TimerService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var engine by mutableStateOf<TimerEngine?>(null)
        private set

    private var sounds: WorkoutSounds? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var tickerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID, "Workout in progress", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Live countdown for the running workout" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getStringExtra(EXTRA_TIMER_ID)
                val current = engine
                if (id != null &&
                    (current == null || current.timer.id != id || current.phase == RunPhase.FINISHED)
                ) {
                    startRun(id)
                } else {
                    // Re-entering the same run from the UI: just refresh the notification.
                    startForeground(NOTIFICATION_ID, buildNotification())
                }
            }
            ACTION_TOGGLE_PAUSE -> {
                engine?.togglePause()
                notifyNow()
            }
            ACTION_STOP -> stopRun()
        }
        return START_NOT_STICKY
    }

    private fun startRun(timerId: String) {
        startForeground(NOTIFICATION_ID, buildNotification())
        engine?.stop()
        tickerJob?.cancel()
        scope.launch {
            val timer = TimerRepository.get(this@TimerService).timers.first()
                .find { it.id == timerId }
            if (timer == null) {
                stopRun()
                return@launch
            }
            // A TTS instance is bound to one engine; recreate if this timer wants another.
            val snd = sounds?.takeIf { it.engineName == timer.voiceEngine }
                ?: run {
                    sounds?.release()
                    WorkoutSounds(this@TimerService, timer.voiceEngine).also { sounds = it }
                }
            acquireWakeLock(timer.totalSeconds())
            engine = TimerEngine(timer, scope, snd, onFinished = { onRunFinished() })
                .also { it.start() }
            activeTimerId = timerId
            tickerJob = launch {
                while (isActive) {
                    notifyNow()
                    delay(1000)
                }
            }
        }
    }

    private fun onRunFinished() {
        tickerJob?.cancel()
        releaseWakeLock()
        activeTimerId = null
        // Swap the ongoing notification for a dismissible "done" one, and give the
        // final announcement a moment to play before winding down.
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java)
            .notify(DONE_NOTIFICATION_ID, doneNotification())
        scope.launch {
            delay(6000)
            stopSelf()
        }
    }

    private fun stopRun() {
        engine?.stop()
        tickerJob?.cancel()
        releaseWakeLock()
        activeTimerId = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        engine?.stop()
        tickerJob?.cancel()
        releaseWakeLock()
        sounds?.release()
        sounds = null
        activeTimerId = null
        scope.cancel()
        super.onDestroy()
    }

    private fun acquireWakeLock(totalSeconds: Int) {
        releaseWakeLock()
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "F3WorkoutTimer::run").apply {
            // Generous buffer for lead-in, pauses, and speech.
            acquire((totalSeconds + 600) * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun notifyNow() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val e = engine
        val secondsLeft = e?.let { ceil(it.remainingMs / 1000.0).toInt() } ?: 0
        val text = when {
            e == null -> "Starting…"
            e.phase == RunPhase.FINISHED -> "Workout complete"
            e.phase == RunPhase.LEAD_IN -> "Get ready · ${secondsLeft}s"
            else -> buildString {
                if (e.isPaused) append("Paused · ")
                val interval = e.currentInterval
                if (interval != null && interval.blockCount > 1 &&
                    interval.blockName.isNotBlank() &&
                    !interval.blockName.equals(interval.displayLabel, ignoreCase = true)
                ) {
                    append("${interval.blockName} · ")
                }
                append(interval?.displayLabel ?: "")
                append(" · ${formatDuration(secondsLeft)}")
                if (interval != null && interval.roundsInBlock > 1) {
                    append(" · round ${interval.round}/${interval.roundsInBlock}")
                }
            }
        }

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_LAUNCH_RUN_ID, e?.timer?.id)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(e?.timer?.name ?: "F3 Workout Timer")
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)

        if (e != null && e.phase != RunPhase.FINISHED) {
            builder.addAction(
                0,
                if (e.isPaused) "Resume" else "Pause",
                servicePendingIntent(1, ACTION_TOGGLE_PAUSE),
            )
            builder.addAction(0, "Stop", servicePendingIntent(2, ACTION_STOP))
        }
        return builder.build()
    }

    private fun doneNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(engine?.timer?.name ?: "F3 Workout Timer")
            .setContentText("Workout complete. Nice work.")
            .setAutoCancel(true)
            .build()

    private fun servicePendingIntent(requestCode: Int, action: String): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, TimerService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        private const val CHANNEL_ID = "workout_run"
        private const val NOTIFICATION_ID = 1
        private const val DONE_NOTIFICATION_ID = 2
        private const val ACTION_START = "com.f3.workouttimer.action.START"
        private const val ACTION_TOGGLE_PAUSE = "com.f3.workouttimer.action.TOGGLE_PAUSE"
        private const val ACTION_STOP = "com.f3.workouttimer.action.STOP"
        private const val EXTRA_TIMER_ID = "timer_id"

        /** Timer id of the run in progress, for the home screen's resume banner. */
        var activeTimerId by mutableStateOf<String?>(null)
            private set

        fun start(context: Context, timerId: String) {
            context.startForegroundService(
                Intent(context, TimerService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_TIMER_ID, timerId)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, TimerService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
