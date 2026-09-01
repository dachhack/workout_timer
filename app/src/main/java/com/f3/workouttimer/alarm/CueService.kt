package com.f3.workouttimer.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.f3.workouttimer.MainActivity
import com.f3.workouttimer.R
import com.f3.workouttimer.audio.WorkoutSounds
import com.f3.workouttimer.data.ScheduleRepository
import com.f3.workouttimer.data.TimerRepository
import com.f3.workouttimer.model.ScheduledCue
import com.f3.workouttimer.timer.TimerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Plays one scheduled cue: a tone, a spoken line, the start of a workout, or
 * any mix of them. Short-lived — it stops as soon as it has had its say.
 */
class CueService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sounds: WorkoutSounds? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Scheduled cues",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Alerts at the times you set" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val cueId = intent?.getStringExtra(CueScheduler.EXTRA_CUE_ID)
        startForeground(NOTIFICATION_ID, notification("F3 Workout Timer", "…"))
        if (cueId == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        scope.launch { play(cueId) }
        return START_NOT_STICKY
    }

    private suspend fun play(cueId: String) {
        val cue = ScheduleRepository.get(this).all().find { it.id == cueId }
        if (cue == null) {
            stopSelf()
            return
        }

        val timer = cue.timerId
            .takeIf { it.isNotBlank() }
            ?.let { id -> TimerRepository.get(this).timers.first().find { it.id == id } }

        val title = cue.label.ifBlank { cue.timeLabel() }
        val body = cue.message.ifBlank {
            timer?.let { "Starting ${it.name}" } ?: "Scheduled alert"
        }
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(title, body))

        val snd = WorkoutSounds(this, timer?.voiceEngine.orEmpty()).also { sounds = it }
        if (timer != null) snd.setVoiceByName(timer.voiceName)

        if (cue.alert) {
            snd.stageBeep()
            delay(600)
        }
        if (cue.message.isNotBlank()) {
            snd.speakAndWait(cue.message)
        }

        if (timer != null) {
            // Hand off to the run itself, which owns its own foreground service.
            TimerService.start(this, timer.id, cue.blockId)
        }
        stopSelf()
    }

    override fun onDestroy() {
        sounds?.release()
        sounds = null
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(title: String, body: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .build()

    private companion object {
        const val CHANNEL_ID = "scheduled_cues"
        const val NOTIFICATION_ID = 3
    }
}
