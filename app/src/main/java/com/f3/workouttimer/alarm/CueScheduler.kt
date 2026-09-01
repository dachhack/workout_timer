package com.f3.workouttimer.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.f3.workouttimer.data.ScheduleRepository
import com.f3.workouttimer.model.ScheduledCue

/** Books scheduled cues with the system alarm clock. */
object CueScheduler {

    const val EXTRA_CUE_ID = "cue_id"

    fun canScheduleExact(context: Context): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 31) manager.canScheduleExactAlarms() else true
    }

    /** Books the next firing of one cue, replacing any already booked for it. */
    fun schedule(context: Context, cue: ScheduledCue) {
        cancel(context, cue)
        if (!cue.enabled || cue.isSilent) return
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val at = cue.nextTriggerMillis()
        val operation = pendingIntent(context, cue, mutable = false)

        runCatching {
            if (canScheduleExact(context)) {
                // An alarm clock survives Doze and shows in the status bar, which
                // is what a 5:15 workout needs.
                manager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(at, showIntent(context)),
                    operation,
                )
            } else {
                // Without the exact-alarm permission this can drift by minutes,
                // but it still fires.
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            }
        }
    }

    fun cancel(context: Context, cue: ScheduledCue) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(pendingIntent(context, cue, mutable = false))
    }

    /** Re-books everything: after a reboot, a clock change, or an edit. */
    suspend fun rescheduleAll(context: Context) {
        ScheduleRepository.get(context).all().forEach { schedule(context, it) }
    }

    private fun pendingIntent(context: Context, cue: ScheduledCue, mutable: Boolean): PendingIntent {
        val intent = Intent(context, CueReceiver::class.java)
            .setAction("com.f3.workouttimer.CUE")
            .putExtra(EXTRA_CUE_ID, cue.id)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, cue.id.hashCode(), intent, flags)
    }

    /** Where the status-bar alarm icon takes you. */
    private fun showIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, com.f3.workouttimer.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
