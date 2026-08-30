package com.f3.workouttimer.model

import kotlinx.serialization.Serializable
import java.util.UUID

enum class StageType(val label: String, val defaultAnnouncement: String) {
    INTRO("WARM-UP", "Warm up"),
    WORK("WORK", "Work"),
    REST("REST", "Rest"),
    TRANSITION("TRANSITION", "Transition"),
    OUTRO("COOL-DOWN", "Cool down. Great work."),
}

/** The stages that repeat every round, in order. */
val ROUND_STAGES = listOf(StageType.WORK, StageType.REST, StageType.TRANSITION)

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
    /** One block before round 1 (warm-up, disclaimer, instructions…). */
    val intro: Stage = Stage(enabled = false, seconds = 60),
    /** One block after the final round (cool-down, COT…). */
    val outro: Stage = Stage(enabled = false, seconds = 60),
    /** Speak "halfway there" at the midpoint of the whole workout. */
    val announceHalfway: Boolean = false,
    /** Optional exercise names, one per round; repeats if shorter than the round count. */
    val exercises: List<String> = emptyList(),
    /** TTS voice name ([android.speech.tts.Voice.getName]); blank = engine default. */
    val voiceName: String = "",
    /** TTS engine package name; blank = the device's default engine. */
    val voiceEngine: String = "",
) {
    fun stage(type: StageType): Stage = when (type) {
        StageType.INTRO -> intro
        StageType.WORK -> work
        StageType.REST -> rest
        StageType.TRANSITION -> transition
        StageType.OUTRO -> outro
    }

    /**
     * The flat sequence of intervals for a full run: the intro block, then
     * work → rest → transition each round (skipping disabled stages, with rest
     * and transition dropped after the final round), then the outro block.
     */
    fun exerciseForRound(round: Int): String =
        if (exercises.isEmpty()) "" else exercises[(round - 1) % exercises.size]

    fun intervals(): List<Interval> {
        val full = mutableListOf<Interval>()
        for (round in 1..rounds) {
            for (type in ROUND_STAGES) {
                val s = stage(type)
                if (s.enabled && s.seconds > 0) {
                    val exercise = if (type == StageType.WORK) exerciseForRound(round) else ""
                    full.add(Interval(type, s.seconds, round, s.message, exercise))
                }
            }
        }
        // End the rounds on the last work interval instead of resting/transitioning
        // into nothing. Intro/outro sit outside the rounds (round = 0).
        val trimmed = full.dropLastWhile { it.type != StageType.WORK }
        val core = if (trimmed.isEmpty()) full else trimmed
        return buildList {
            if (intro.enabled && intro.seconds > 0) {
                add(Interval(StageType.INTRO, intro.seconds, 0, intro.message))
            }
            addAll(core)
            if (outro.enabled && outro.seconds > 0) {
                add(Interval(StageType.OUTRO, outro.seconds, 0, outro.message))
            }
        }
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
