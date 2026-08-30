package com.f3.workouttimer.model

import kotlinx.serialization.Serializable
import java.util.UUID

enum class StageType(val label: String, val defaultAnnouncement: String) {
    WORK("WORK", "Work"),
    REST("REST", "Rest"),
    TRANSITION("TRANSITION", "Transition"),
}

/** The stages that run at every station, in order. */
val ROUND_STAGES = listOf(StageType.WORK, StageType.REST, StageType.TRANSITION)

@Serializable
data class Stage(
    val enabled: Boolean = true,
    val seconds: Int = 30,
    /** Optional text-to-speech message spoken when the stage starts. Blank = speak the stage name. */
    val message: String = "",
)

/**
 * One segment of a workout: a named circuit of exercises repeated for a number
 * of rounds, with its own work / rest / transition timings.
 *
 * Each round runs every exercise in turn (a "station"), so a block of three
 * exercises for four rounds is twelve work intervals. A block with no
 * exercises is a plain interval block; a one-round block with only work
 * enabled is a single timed block — a warm-up, a cool-down, a COT.
 */
@Serializable
data class Block(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val rounds: Int = 1,
    val work: Stage = Stage(enabled = true, seconds = 45),
    val rest: Stage = Stage(enabled = true, seconds = 15),
    val transition: Stage = Stage(enabled = false, seconds = 10),
    /** Exercises run in order within each round; empty = one plain work interval. */
    val exercises: List<String> = emptyList(),
) {
    fun stage(type: StageType): Stage = when (type) {
        StageType.WORK -> work
        StageType.REST -> rest
        StageType.TRANSITION -> transition
    }

    /** The stations of one round: the exercise list, or a single unnamed station. */
    fun stations(): List<String> = exercises.ifEmpty { listOf("") }

    fun intervals(blockIndex: Int = 0, blockCount: Int = 1): List<Interval> {
        val result = mutableListOf<Interval>()
        for (round in 1..rounds) {
            for (exercise in stations()) {
                for (type in ROUND_STAGES) {
                    val s = stage(type)
                    if (s.enabled && s.seconds > 0) {
                        result.add(
                            Interval(
                                type = type,
                                seconds = s.seconds,
                                round = round,
                                roundsInBlock = rounds,
                                message = s.message,
                                exercise = if (type == StageType.WORK) exercise else "",
                                blockName = name,
                                blockIndex = blockIndex,
                                blockCount = blockCount,
                            )
                        )
                    }
                }
            }
        }
        return result
    }

    fun totalSeconds(): Int = intervals().sumOf { it.seconds }
}

@Serializable
data class WorkoutTimer(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Beatdown",
    /** The whole workout, in order. */
    val blocks: List<Block> = emptyList(),
    /** Speak the coming exercise during rest and transition, so the PAX can set up. */
    val announceNextExercise: Boolean = true,
    /** TTS voice name ([android.speech.tts.Voice.getName]); blank = engine default. */
    val voiceName: String = "",
    /** TTS engine package name; blank = the device's default engine. */
    val voiceEngine: String = "",
) {
    /** Every block's intervals back to back, trimmed so the workout ends on work. */
    fun intervals(): List<Interval> {
        val all = blocks.flatMapIndexed { i, b -> b.intervals(i, blocks.size) }
        val trimmed = all.dropLastWhile { it.type != StageType.WORK }
        return if (trimmed.isEmpty()) all else trimmed
    }

    fun totalSeconds(): Int = intervals().sumOf { it.seconds }
}

data class Interval(
    val type: StageType,
    val seconds: Int,
    val round: Int,
    val roundsInBlock: Int,
    val message: String,
    val exercise: String = "",
    val blockName: String = "",
    val blockIndex: Int = 0,
    val blockCount: Int = 1,
) {
    /** The headline on the run screen: the exercise, else the block, else the stage. */
    val displayLabel: String
        get() = when (type) {
            StageType.WORK -> exercise.ifBlank { blockName.ifBlank { type.label } }
            else -> type.label
        }

    /** The movements of a compound station, for stacking them on screen. */
    val movements: List<String>
        get() = splitMovements(displayLabel)

    /** What gets spoken when this interval starts. */
    val announcement: String
        get() = when (type) {
            StageType.WORK -> exercise.ifBlank {
                message.ifBlank { blockName.ifBlank { type.defaultAnnouncement } }
            }
            else -> message.ifBlank { type.defaultAnnouncement }
        }
}

/**
 * Splits one station into the movements it packs in: "5 Squats, 5 Merkins,
 * 5 Sit-ups" is a single timed work interval covering three movements. Commas
 * and plus signs both separate.
 */
fun splitMovements(station: String): List<String> =
    station.split(',', '+', '\n').map { it.trim() }.filter { it.isNotEmpty() }

fun formatDuration(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return if (m > 0) "%d:%02d".format(m, s) else "${s}s"
}
