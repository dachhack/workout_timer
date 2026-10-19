package com.f3.workouttimer.model

import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.random.Random

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
    /** Up to [MAX_PHRASES] lines, each said once per run halfway through a random work set. */
    val phrases: List<String> = emptyList(),
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
    /** Spoken over the lead-in when the run starts; blank uses [DEFAULT_OPENING]. */
    val openingMessage: String = "",
    /** Spoken when the workout finishes; blank uses [DEFAULT_CLOSING]. */
    val closingMessage: String = "",
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

    /** What the app says as the run starts. */
    val opening: String get() = openingMessage.ifBlank { DEFAULT_OPENING }

    /** What the app says once the last interval is done. */
    val closing: String get() = closingMessage.ifBlank { DEFAULT_CLOSING }
}

const val DEFAULT_OPENING = "Get ready"
const val MAX_PHRASES = 6

/** Work sets shorter than this are over too quickly to talk in the middle of. */
const val MIN_PHRASE_SET_SECONDS = 10

/**
 * Decides which work sets get a mid-set phrase, and which phrase: interval
 * index → phrase. Each block's phrases are shuffled and scattered over that
 * block's work sets, so every phrase is said once and none repeats. A block
 * with more phrases than sets says only as many as it has sets.
 */
fun planPhrases(
    intervals: List<Interval>,
    blocks: List<Block>,
    random: Random = Random,
): Map<Int, String> {
    val plan = mutableMapOf<Int, String>()
    intervals.withIndex()
        .filter { it.value.type == StageType.WORK && it.value.seconds >= MIN_PHRASE_SET_SECONDS }
        .groupBy { it.value.blockIndex }
        .forEach { (blockIndex, sets) ->
            val phrases = blocks.getOrNull(blockIndex)?.phrases.orEmpty()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinctBy(String::lowercase)
                .take(MAX_PHRASES)
                .shuffled(random)
            val chosen = sets.shuffled(random).take(phrases.size)
            chosen.zip(phrases).forEach { (set, phrase) -> plan[set.index] = phrase }
        }
    return plan
}
const val DEFAULT_CLOSING = "Workout complete. Nice work."

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
            // The stage message leads into the exercise rather than being lost behind it.
            StageType.WORK -> when {
                exercise.isNotBlank() && message.isNotBlank() -> "$message. $exercise"
                exercise.isNotBlank() -> exercise
                else -> message.ifBlank { blockName.ifBlank { type.defaultAnnouncement } }
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

/** "3 minutes 20 seconds" — for speech, where "3:20" reads as a time of day. */
fun spokenDuration(totalSeconds: Int): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val m = seconds / 60
    val s = seconds % 60
    fun plural(value: Int, unit: String) = "$value $unit${if (value == 1) "" else "s"}"
    return when {
        m > 0 && s > 0 -> "${plural(m, "minute")} ${plural(s, "second")}"
        m > 0 -> plural(m, "minute")
        else -> plural(s, "second")
    }
}

fun formatDuration(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return if (m > 0) "%d:%02d".format(m, s) else "${s}s"
}
