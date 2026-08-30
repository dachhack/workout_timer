package com.f3.workouttimer.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Wraps text-to-speech stage announcements and countdown beeps.
 * Create when the run screen appears, [release] when it leaves.
 */
class WorkoutSounds(context: Context) {

    private var ttsReady = false
    private var pendingUtterance: String? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            pendingUtterance?.let { speak(it) }
            pendingUtterance = null
        }
    }.apply { language = Locale.getDefault() }

    private val tones: ToneGenerator? =
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 85) }.getOrNull()

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!ttsReady) {
            pendingUtterance = text
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "f3-${System.nanoTime()}")
    }

    /** Short tick for the 3-2-1 countdown at the end of a stage. */
    fun countdownBeep() {
        tones?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
    }

    /** Longer tone marking a stage change. */
    fun stageBeep() {
        tones?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
    }

    fun release() {
        tts.stop()
        tts.shutdown()
        tones?.release()
    }
}
