package com.f3.workouttimer.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Wraps text-to-speech stage announcements and countdown beeps.
 * Create when a run (or the voice picker) needs it, [release] when done.
 */
class WorkoutSounds(context: Context) {

    var isReady by mutableStateOf(false)
        private set

    private var pendingUtterance: String? = null
    private var pendingVoiceName: String? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
            pendingVoiceName?.let { applyVoice(it) }
            pendingVoiceName = null
            pendingUtterance?.let { speak(it) }
            pendingUtterance = null
        }
    }.apply { language = Locale.getDefault() }

    private val tones: ToneGenerator? =
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 85) }.getOrNull()

    /** Installed voices, best-quality first. Empty until [isReady]. */
    fun availableVoices(): List<Voice> {
        if (!isReady) return emptyList()
        return runCatching { tts.voices?.toList() }.getOrNull().orEmpty()
            .filterNot { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true }
            .sortedWith(compareBy({ it.locale.toLanguageTag() }, { -it.quality }, { it.name }))
    }

    /** Select a voice by its [Voice.getName]; blank restores the engine default. */
    fun setVoiceByName(name: String) {
        if (!isReady) {
            pendingVoiceName = name
            return
        }
        applyVoice(name)
    }

    private fun applyVoice(name: String) {
        if (name.isBlank()) {
            runCatching { tts.voice = tts.defaultVoice }
            return
        }
        val match = runCatching { tts.voices?.firstOrNull { it.name == name } }.getOrNull()
        if (match != null) {
            runCatching { tts.voice = match }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!isReady) {
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

/** Human-friendly label for a TTS voice in the picker. */
fun voiceLabel(voice: Voice): String {
    val locale = voice.locale.displayName
    val variant = voice.name.substringAfterLast('-').ifBlank { voice.name }
    val network = if (voice.isNetworkConnectionRequired) " · network" else ""
    return "$locale · $variant$network"
}
