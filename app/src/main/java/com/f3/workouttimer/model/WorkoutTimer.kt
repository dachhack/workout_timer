package com.f3.workouttimer.model

import kotlinx.serialization.Serializable
import java.util.UUID

enum class StageType(val label: String, val defaultAnnouncement: String) {
    WORK("WORK", "Work"),
    REST("REST", "Rest"),
    TRANSITION("TRANSITION", "Transition"),
    /** A one-shot custom block outside the rounds (warm-up, disclaimer, stretch, COT…). */
    BLOCK("BLOCK", "Begin"),
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

/** A one-shot custom block that runs before or after the rounds. */
@Serializable
data class Block(
    val name: String = "",
    val seconds: Int = 60,
    /** Optional text-to-speech message spoken when the block starts. Blank = speak the name. */
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
    /** Blocks run in order before round 1 (warm-up, disclaimer, instructions…). */
    val blocksBefore: List<Block> = emptyList(),
    /** Blocks run in order after the final round (cool-down, stretch, COT…). */
    val blocksAfter: List<Block> = emptyList(),
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
        StageType.WORK -> work
        StageType.REST -> rest
        StageType.TRANSITION -> transition
        StageType.BLOCK -> error("Custom blocks live in blocksBefore/blocksAfter")
    }

    /**
     * The flat sequence of intervals for a full run: the before-blocks in order,
     * then work → rest → transition each round (skipping disabled stages, with
     * rest and transition dropped after the final round), then the after-blocks.
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
        // into nothing. Custom blocks sit outside the rounds (round = 0).
        val trimmed = full.dropLastWhile { it.type != StageType.WORK }
        val core = if (trimmed.isEmpty()) full else trimmed
        fun blockInterval(b: Block) =
            Interval(StageType.BLOCK, b.seconds, 0, b.message, name = b.name)
        return buildList {
            blocksBefore.filter { it.seconds > 0 }.forEach { add(blockInterval(it)) }
            addAll(core)
            blocksAfter.filter { it.seconds > 0 }.forEach { add(blockInterval(it)) }
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
    /** Custom display name for BLOCK intervals. */
    val name: String = "",
) {
    val displayLabel: String
        get() = if (type == StageType.BLOCK) name.ifBlank { "Block" } else type.label
}

fun formatDuration(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return if (m > 0) "%d:%02d".format(m, s) else "${s}s"
}
