package com.f3.workouttimer.data

import com.f3.workouttimer.model.Block
import com.f3.workouttimer.model.WorkoutTimer
import com.f3.workouttimer.model.formatDuration
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Passing a workout to another PAX. A timer becomes a compact link that
 * survives a paste into Slack or a text message; the other phone decodes it
 * back into a timer of its own.
 */
object TimerShare {

    const val SCHEME = "f3timer"
    const val HOST = "import"
    private const val PARAM = "d"
    private const val VERSION = 1

    @Serializable
    private data class Envelope(val v: Int, val timer: WorkoutTimer)

    // encodeDefaults = false keeps the link short: anything left at its default
    // is simply absent and comes back as the default.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    /** The bare payload, without the link wrapper. */
    fun encode(timer: WorkoutTimer): String {
        // Ids are regenerated on import, so there is no point shipping them.
        val stripped = timer.copy(
            id = "",
            blocks = timer.blocks.map { it.copy(id = "") },
        )
        val bytes = json
            .encodeToString(Envelope.serializer(), Envelope(VERSION, stripped))
            .toByteArray()
        val gzipped = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(bytes) }
        }.toByteArray()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(gzipped)
    }

    fun link(timer: WorkoutTimer): String = "$SCHEME://$HOST?$PARAM=${encode(timer)}"

    /** What goes into the share sheet: a readable summary plus the link. */
    fun shareText(timer: WorkoutTimer): String = buildString {
        val blocks = timer.blocks.size
        append(timer.name)
        append(" — $blocks block")
        if (blocks != 1) append("s")
        append(", ${formatDuration(timer.totalSeconds())}")
        val names = timer.blocks.mapNotNull { it.name.ifBlank { null } }
        if (names.isNotEmpty()) {
            append("\n")
            append(names.joinToString(" → "))
        }
        append("\n\nOpen this in the F3 Workout Timer app to add it:\n")
        append(link(timer))
    }

    /**
     * Reads back a share link, a bare payload, or a message with either buried
     * in it. Returns null if there is nothing valid in [input].
     *
     * The result is a fresh timer: new ids so it never collides with a saved
     * one, and no voice selection, since a voice or engine from the sender's
     * phone may not exist on this one.
     */
    fun decode(input: String): WorkoutTimer? {
        val payload = extractPayload(input) ?: return null
        return runCatching {
            val gzipped = Base64.getUrlDecoder().decode(payload)
            val bytes = GZIPInputStream(gzipped.inputStream()).use { it.readBytes() }
            val envelope = json.decodeFromString(Envelope.serializer(), String(bytes))
            if (envelope.v > VERSION) return null
            envelope.timer
                .copy(
                    id = UUID.randomUUID().toString(),
                    blocks = envelope.timer.blocks.map { block: Block ->
                        block.copy(id = UUID.randomUUID().toString())
                    },
                    voiceName = "",
                    voiceEngine = "",
                )
                .takeIf { it.blocks.isNotEmpty() && it.totalSeconds() > 0 }
        }.getOrNull()
    }

    /** Pulls the payload out of a link, a pasted message, or a bare code. */
    private fun extractPayload(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        val marker = "$SCHEME://$HOST?$PARAM="
        val start = text.indexOf(marker)
        val candidate = if (start >= 0) {
            text.substring(start + marker.length)
        } else {
            text
        }
        // Stop at the first character that cannot be part of a base64url payload,
        // so trailing words or punctuation in a pasted message are ignored.
        val payload = candidate.takeWhile { it.isLetterOrDigit() || it == '-' || it == '_' }
        return payload.ifEmpty { null }
    }
}
