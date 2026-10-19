package com.f3.workouttimer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WorkoutTimerTest {

    private fun circuit(
        name: String,
        rounds: Int,
        exercises: List<String>,
        work: Int = 40,
        rest: Int = 20,
        transition: Int = 0,
    ) = Block(
        name = name,
        rounds = rounds,
        work = Stage(enabled = true, seconds = work),
        rest = Stage(enabled = rest > 0, seconds = rest),
        transition = Stage(enabled = transition > 0, seconds = transition),
        exercises = exercises,
    )

    @Test
    fun `each round runs every exercise in order`() {
        val block = circuit("Warm-up", rounds = 2, exercises = listOf("A", "B", "C"))
        val work = block.intervals().filter { it.type == StageType.WORK }

        assertEquals(6, work.size)
        assertEquals(listOf("A", "B", "C", "A", "B", "C"), work.map { it.exercise })
        assertEquals(listOf(1, 1, 1, 2, 2, 2), work.map { it.round })
    }

    @Test
    fun `disabled stages are skipped and a block with no exercises is one station`() {
        val block = Block(
            name = "Cool-down",
            rounds = 1,
            work = Stage(enabled = true, seconds = 90),
            rest = Stage(enabled = false, seconds = 15),
            transition = Stage(enabled = false, seconds = 10),
        )
        val intervals = block.intervals()

        assertEquals(1, intervals.size)
        assertEquals(90, block.totalSeconds())
        // With no exercise, the block name carries the screen and the speech.
        assertEquals("Cool-down", intervals[0].displayLabel)
        assertEquals("Cool-down", intervals[0].announcement)
    }

    @Test
    fun `blocks run back to back and carry their position`() {
        val timer = WorkoutTimer(
            blocks = listOf(
                circuit("Warm-up", rounds = 1, exercises = listOf("A", "B"), work = 30, rest = 10),
                circuit("Cardio", rounds = 2, exercises = emptyList(), work = 60, rest = 30),
            )
        )
        val intervals = timer.intervals()

        // Warm-up runs A/rest/B/rest — its trailing rest is kept as the breather
        // into Cardio; only the workout's final rest is trimmed.
        assertEquals(
            listOf("Warm-up", "Warm-up", "Warm-up", "Warm-up", "Cardio", "Cardio", "Cardio"),
            intervals.map { it.blockName },
        )
        assertEquals(listOf(0, 0, 0, 0, 1, 1, 1), intervals.map { it.blockIndex })
        assertTrue(intervals.all { it.blockCount == 2 })
        assertEquals((30 + 10 + 30 + 10) + (60 + 30 + 60), timer.totalSeconds())
    }

    @Test
    fun `the workout never ends on a rest or transition`() {
        val timer = WorkoutTimer(
            blocks = listOf(circuit("Main", rounds = 3, exercises = emptyList(), rest = 20))
        )
        val intervals = timer.intervals()

        assertEquals(StageType.WORK, intervals.last().type)
        assertEquals(3 * 40 + 2 * 20, timer.totalSeconds())
    }

    @Test
    fun `a work message is spoken ahead of the exercise`() {
        val block = Block(
            name = "Weights",
            rounds = 1,
            work = Stage(enabled = true, seconds = 45, message = "Lift"),
            rest = Stage(enabled = true, seconds = 15, message = "Breathe"),
            transition = Stage(enabled = false, seconds = 0),
            exercises = listOf("Curls"),
        )
        val intervals = block.intervals()

        assertEquals("Lift. Curls", intervals[0].announcement)
        assertEquals("Breathe", intervals[1].announcement)
    }

    @Test
    fun `a compound station is one work interval covering several movements`() {
        val block = circuit(
            "Core",
            rounds = 2,
            exercises = listOf("5 Squats, 5 Merkins, 5 Sit-ups"),
            work = 60,
            rest = 30,
        )
        val intervals = block.intervals()
        val work = intervals.filter { it.type == StageType.WORK }

        // One timed interval per round, not one per movement.
        assertEquals(2, work.size)
        assertEquals(60 + 30 + 60 + 30, block.totalSeconds())
        assertEquals(
            listOf("5 Squats", "5 Merkins", "5 Sit-ups"),
            work[0].movements,
        )
        // Spoken as written, so the pauses land between movements.
        assertEquals("5 Squats, 5 Merkins, 5 Sit-ups", work[0].announcement)
    }

    @Test
    fun `separate lines stay separate stations`() {
        val block = circuit("Circuit", rounds = 1, exercises = listOf("Squats", "Merkins"))

        assertEquals(2, block.intervals().count { it.type == StageType.WORK })
    }

    @Test
    fun `plus signs separate movements and blank entries are dropped`() {
        assertEquals(listOf("Squats", "Merkins"), splitMovements("Squats + Merkins"))
        assertEquals(listOf("Squats", "Merkins"), splitMovements(" Squats , , Merkins "))
        assertEquals(listOf("Burpees"), splitMovements("Burpees"))
        assertTrue(splitMovements("   ").isEmpty())
    }

    @Test
    fun `opening and closing messages fall back to the defaults when blank`() {
        val default = WorkoutTimer(blocks = emptyList())
        assertEquals(DEFAULT_OPENING, default.opening)
        assertEquals(DEFAULT_CLOSING, default.closing)

        val custom = default.copy(
            openingMessage = "Circle up, gentlemen",
            closingMessage = "Good work. See you Thursday.",
        )
        assertEquals("Circle up, gentlemen", custom.opening)
        assertEquals("Good work. See you Thursday.", custom.closing)
    }

    @Test
    fun `an empty workout has no intervals and no length`() {
        val timer = WorkoutTimer(blocks = emptyList())

        assertTrue(timer.intervals().isEmpty())
        assertEquals(0, timer.totalSeconds())
    }

    @Test
    fun `each phrase is said once, on a work set of its own block`() {
        val phrases = listOf("One", "Two", "Three")
        val timer = WorkoutTimer(
            blocks = listOf(
                circuit("A", rounds = 3, exercises = listOf("X", "Y")).copy(phrases = phrases),
                circuit("B", rounds = 2, exercises = listOf("Z")),
            ),
        )
        val intervals = timer.intervals()
        repeat(20) { seed ->
            val plan = planPhrases(intervals, timer.blocks, Random(seed))
            assertEquals(phrases.sorted(), plan.values.sorted())
            plan.keys.forEach { i ->
                assertEquals(StageType.WORK, intervals[i].type)
                assertEquals(0, intervals[i].blockIndex)
            }
        }
    }

    @Test
    fun `phrases run out rather than repeat when a block has few sets`() {
        val block = circuit("A", rounds = 2, exercises = emptyList())
            .copy(phrases = listOf("One", "Two", "Three", "one", " "))
        val timer = WorkoutTimer(blocks = listOf(block))
        val plan = planPhrases(timer.intervals(), timer.blocks, Random(1))

        assertEquals(2, plan.size)
        assertEquals(2, plan.values.toSet().size)
    }

    @Test
    fun `short work sets get no phrase`() {
        val block = circuit("A", rounds = 4, exercises = emptyList(), work = MIN_PHRASE_SET_SECONDS - 1)
            .copy(phrases = listOf("One"))
        val timer = WorkoutTimer(blocks = listOf(block))

        assertTrue(planPhrases(timer.intervals(), timer.blocks).isEmpty())
    }
}
