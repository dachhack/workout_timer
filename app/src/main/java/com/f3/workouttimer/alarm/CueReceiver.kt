package com.f3.workouttimer.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.f3.workouttimer.data.ScheduleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** A cue's moment arrived: hand it to [CueService] and book the next one. */
class CueReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val cueId = intent.getStringExtra(CueScheduler.EXTRA_CUE_ID) ?: return
        val app = context.applicationContext

        ContextCompat.startForegroundService(
            app,
            Intent(app, CueService::class.java).putExtra(CueScheduler.EXTRA_CUE_ID, cueId),
        )

        // Repeating cues need their next firing booked; a one-shot switches off.
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val repo = ScheduleRepository.get(app)
                val cue = repo.all().find { it.id == cueId } ?: return@launch
                if (cue.repeats) {
                    CueScheduler.schedule(app, cue)
                } else {
                    repo.save(cue.copy(enabled = false))
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Alarms do not survive a reboot or a clock change, so re-book them all. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                CueScheduler.rescheduleAll(app)
            } finally {
                pending.finish()
            }
        }
    }
}
