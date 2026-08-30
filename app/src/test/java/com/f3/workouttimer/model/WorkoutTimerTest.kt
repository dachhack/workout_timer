package com.f3.workouttimer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun `an exercise outranks the stage message on work intervals`() {
        val block = Block(
            name = "Weights",
            rounds = 1,
            work = Stage(enabled = true, seconds = 45, message = "Lift"),
            rest = Stage(enabled = true, seconds = 15, message = "Breathe"),
            transition = Stage(enabled = false, seconds = 0),
            exercises = listOf("Curls"),
        )
        val intervals = block.intervals()

        assertEquals("Curls", intervals[0].announcement)
        assertEquals("Breathe", intervals[1].announcement)
    }

    @Test
    fun `an empty workout has no intervals and no length`() {
        val timer = WorkoutTimer(blocks = emptyList())

        assertTrue(timer.intervals().isEmpty())
        assertEquals(0, timer.totalSeconds())
    }
}
