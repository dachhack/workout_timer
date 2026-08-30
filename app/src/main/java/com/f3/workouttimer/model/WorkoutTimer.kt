package com.f3.workouttimer.model

import kotlinx.serialization.Serializable
import java.util.UUID

enum class StageType(val label: String, val defaultAnnouncement: String) {
    WORK("WORK", "Work"),
    REST("REST", "Rest"),
    TRANSITION("TRANSITION", "Transition"),
}

@Serializable
data class Stage(
    val enabled: Boolean = true,
    val seconds: Int = 30,
    /** Optional text-to-speech message spoken when the stage starts. Blank = speak the stage name. */
    val message: String = "",
)

@Serializable
data class WorkoutTimer(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Beatdown",
    val rounds: Int = 5,
    val work: Stage = Stage(enabled = true, seconds = 45),
    val rest: Stage = Stage(enabled = true, seconds = 15),
    val transition: Stage = Stage(enabled = false, seconds = 10),
    /** Speak "halfway there" at the midpoint of the whole workout. */
    val announceHalfway: Boolean = false,
    /** Optional exercise names, one per round; repeats if shorter than the round count. */
    val exercises: List<String> = emptyList(),
    /** TTS voice name ([android.speech.tts.Voice.getName]); blank = device default. */
    val voiceName: String = "",
) {
    fun stage(type: StageType): Stage = when (type) {
        StageType.WORK -> work
        StageType.REST -> rest
        StageType.TRANSITION -> transition
    }

    /**
     * The flat sequence of intervals for a full run: work → rest → transition each
     * round, skipping disabled stages. Rest and transition are dropped after the
     * final round — the workout ends on the last active stage.
     */
    fun exerciseForRound(round: Int): String =
        if (exercises.isEmpty()) "" else exercises[(round - 1) % exercises.size]

    fun intervals(): List<Interval> {
        val full = mutableListOf<Interval>()
        for (round in 1..rounds) {
            for (type in StageType.entries) {
                val s = stage(type)
                if (s.enabled && s.seconds > 0) {
                    val exercise = if (type == StageType.WORK) exerciseForRound(round) else ""
                    full.add(Interval(type, s.seconds, round, s.message, exercise))
                }
            }
        }
        // End on the last work interval instead of resting/transitioning into nothing.
        val trimmed = full.dropLastWhile { it.type != StageType.WORK }
        return if (trimmed.isEmpty()) full else trimmed
    }

    fun totalSeconds(): Int = intervals().sumOf { it.seconds }
}

data class Interval(
    val type: StageType,
    val seconds: Int,
    val round: Int,
    val message: String,
    val exercise: String = "",
)

fun formatDuration(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return if (m > 0) "%d:%02d".format(m, s) else "${s}s"
}
