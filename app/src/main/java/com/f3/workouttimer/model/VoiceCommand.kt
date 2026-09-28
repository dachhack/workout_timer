package com.f3.workouttimer.model

/** What the app can be told to do out loud during a run. */
enum class VoiceCommand {
    NEXT_BLOCK,
    NEXT_STAGE,
    PAUSE,
    RESUME,
    END_WORKOUT,
    TIME_LEFT,
    COUNTDOWN,
}

/**
 * Matches what the recogniser heard against the handful of things worth
 * saying mid-beatdown. Deliberately keyword-based rather than exact-phrase:
 * nobody shouts a fixed sentence across a parking lot, and the recogniser
 * mangles half of it anyway.
 *
 * Ending the workout needs an unambiguous phrase, since mishearing it costs
 * the whole run; everything else is cheap to get wrong.
 */
fun parseVoiceCommand(heard: String): VoiceCommand? {
    val text = heard.lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }
    if (text.isBlank()) return null

    fun has(vararg words: String) = words.any { it in text }

    val target = when {
        has("block", "station", "round") -> "block"
        else -> ""
    }

    return when {
        has("count me down", "count us down", "count it down", "countdown") ->
            VoiceCommand.COUNTDOWN

        // "stop the workout" / "end the beatdown" — never a bare "stop".
        has("end", "stop", "finish", "were done", "we are done") &&
            has("workout", "beatdown", "timer", "everything") -> VoiceCommand.END_WORKOUT

        has("next", "skip", "move on", "start the") && target == "block" ->
            VoiceCommand.NEXT_BLOCK

        has("next", "skip", "move on") -> VoiceCommand.NEXT_STAGE

        has("pause", "hold up", "hold on", "wait") -> VoiceCommand.PAUSE

        has("resume", "continue", "keep going", "unpause", "carry on") ->
            VoiceCommand.RESUME

        has("how long", "how much", "time left", "time remaining") ->
            VoiceCommand.TIME_LEFT

        else -> null
    }
}
