package com.f3.workouttimer.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Wraps text-to-speech stage announcements and countdown beeps.
 * Create when a run (or the voice picker) needs it, [release] when done.
 *
 * A [TextToSpeech] instance is bound to one engine for its lifetime, so
 * [engineName] is a constructor parameter; create a fresh instance to switch
 * engines. Blank means the device's default engine.
 *
 * Announcements duck whatever else is playing — a music app on the phone or a
 * Bluetooth speaker — for as long as they last, the same way navigation
 * guidance does, then hand the volume back.
 */
class WorkoutSounds(context: Context, val engineName: String = "") {

    var isReady by mutableStateOf(false)
        private set

    private var pendingUtterance: String? = null
    private var pendingVoiceName: String? = null

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** Sounds currently playing; the duck lifts when the last one finishes. */
    private val playing = mutableSetOf<String>()
    private var focusRequest: AudioFocusRequest? = null

    private val tts: TextToSpeech = TextToSpeech(
        appContext,
        { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                runCatching {
                    tts.language = Locale.getDefault()
                    tts.setAudioAttributes(audioAttributes)
                }
                pendingVoiceName?.let { applyVoice(it) }
                pendingVoiceName = null
                pendingUtterance?.let { speak(it) }
                pendingUtterance = null
            }
        },
        engineName.ifBlank { null },
    ).apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = endSound(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = endSound(utteranceId)

            @Deprecated("Required by the base class", ReplaceWith(""))
            override fun onError(utteranceId: String?) = endSound(utteranceId)

            override fun onError(utteranceId: String?, errorCode: Int) = endSound(utteranceId)
        })
    }

    private val tones: ToneGenerator? =
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 85) }.getOrNull()

    /** All installed TTS engines on the device. */
    fun availableEngines(): List<TextToSpeech.EngineInfo> =
        runCatching { tts.engines }.getOrNull().orEmpty()

    /** Package name of the device's default TTS engine. */
    fun defaultEngineName(): String =
        runCatching { tts.defaultEngine }.getOrNull().orEmpty()

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
        val id = "f3-${System.nanoTime()}"
        beginSound(id)
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (result != TextToSpeech.SUCCESS) {
            endSound(id)
            return
        }
        // If the engine never reports back, don't hold the duck forever.
        handler.postDelayed({ endSound(id) }, SPEECH_TIMEOUT_MS)
    }

    /** Short tick for the 3-2-1 countdown at the end of a stage. */
    fun countdownBeep() = playTone(ToneGenerator.TONE_PROP_BEEP, 150)

    /** Longer tone marking a stage change. */
    fun stageBeep() = playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)

    private fun playTone(tone: Int, durationMs: Int) {
        val generator = tones ?: return
        val id = "tone-${System.nanoTime()}"
        beginSound(id)
        runCatching { generator.startTone(tone, durationMs) }
            .onFailure { endSound(id) }
            .onSuccess { handler.postDelayed({ endSound(id) }, durationMs + 200L) }
    }

    @Synchronized
    private fun beginSound(id: String) {
        if (playing.isEmpty()) requestDuck()
        playing.add(id)
    }

    /** Idempotent, so a timeout and a real callback can both fire safely. */
    @Synchronized
    private fun endSound(id: String?) {
        if (id == null) return
        if (playing.remove(id) && playing.isEmpty()) abandonDuck()
    }

    private fun requestDuck() {
        val manager = audioManager ?: return
        runCatching {
            val request = AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .setWillPauseWhenDucked(false)
                .build()
            focusRequest = request
            manager.requestAudioFocus(request)
        }
    }

    private fun abandonDuck() {
        val manager = audioManager ?: return
        focusRequest?.let { request -> runCatching { manager.abandonAudioFocusRequest(request) } }
        focusRequest = null
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        tts.stop()
        tts.shutdown()
        tones?.release()
        synchronized(this) {
            playing.clear()
            abandonDuck()
        }
    }

    private companion object {
        const val SPEECH_TIMEOUT_MS = 20_000L
    }
}

/** Human-friendly label for a TTS voice in the picker. */
fun voiceLabel(voice: Voice): String {
    val locale = voice.locale.displayName
    val variant = voice.name.substringAfterLast('-').ifBlank { voice.name }
    val network = if (voice.isNetworkConnectionRequired) " · network" else ""
    return "$locale · $variant$network"
}
