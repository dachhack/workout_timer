package com.f3.workouttimer.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.f3.workouttimer.model.VoiceCommand
import com.f3.workouttimer.model.parseVoiceCommand

/**
 * Listens for the handful of commands worth shouting during a beatdown.
 *
 * Recognition restarts itself after every result, so it keeps listening for
 * the whole workout. Anything heard while the app is talking — or just after —
 * is thrown away, since otherwise its own "next up: burpees" would be taken
 * as an order to skip.
 *
 * Must be created and driven from the main thread.
 */
class VoiceCommands(
    context: Context,
    private val onCommand: (VoiceCommand) -> Unit,
    /** True while the app itself is speaking. */
    private val isAppSpeaking: () -> Boolean,
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    var isListening by mutableStateOf(false)
        private set

    /** The last thing understood, for a bit of on-screen feedback. */
    var lastHeard by mutableStateOf("")
        private set

    var problem by mutableStateOf("")
        private set

    private var recognizer: SpeechRecognizer? = null
    private var wantListening = false
    private var appSpokeAt = 0L

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun start() {
        if (wantListening) return
        wantListening = true
        problem = ""
        beginSession()
    }

    fun stop() {
        wantListening = false
        isListening = false
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.cancel() }
    }

    fun release() {
        stop()
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun createRecognizer(): SpeechRecognizer? = runCatching {
        // On-device recognition keeps working at an AO with no signal.
        if (Build.VERSION.SDK_INT >= 31 &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        } else {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        }
    }.getOrNull()

    private fun beginSession() {
        if (!wantListening) return
        if (!isAvailable) {
            problem = "No speech recognition on this phone"
            wantListening = false
            return
        }
        val engine = recognizer ?: createRecognizer()?.also { created ->
            recognizer = created
            created.setRecognitionListener(listener)
        }
        if (engine == null) {
            problem = "Couldn't start listening"
            wantListening = false
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            if (Build.VERSION.SDK_INT >= 23) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
        runCatching { engine.startListening(intent) }
            .onSuccess { isListening = true }
            .onFailure { restartSoon() }
    }

    /** Recognition sessions are short by design; just keep opening new ones. */
    private fun restartSoon(delayMs: Long = 400) {
        if (!wantListening) return
        isListening = false
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ beginSession() }, delayMs)
    }

    private fun handleHeard(candidates: List<String>) {
        // The app's own announcements come back through the mic.
        if (isAppSpeaking() || SystemClock.elapsedRealtime() - appSpokeAt < SPEECH_COOLDOWN_MS) {
            return
        }
        val match = candidates.firstNotNullOfOrNull { text ->
            parseVoiceCommand(text)?.let { text to it }
        } ?: return
        lastHeard = match.first
        onCommand(match.second)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (isAppSpeaking()) appSpokeAt = SystemClock.elapsedRealtime()
        }

        override fun onBeginningOfSpeech() {
            if (isAppSpeaking()) appSpokeAt = SystemClock.elapsedRealtime()
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onResults(results: Bundle?) {
            val heard = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
            handleHeard(heard)
            restartSoon()
        }

        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onError(error: Int) {
            when (error) {
                // Silence and no-match are the normal case between commands.
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restartSoon(200)

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> restartSoon(1000)

                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    problem = "Microphone permission is off"
                    wantListening = false
                    isListening = false
                }

                else -> restartSoon(1500)
            }
        }
    }

    private companion object {
        /** How long after the app stops talking before the mic is trusted again. */
        const val SPEECH_COOLDOWN_MS = 1_200L
    }
}
