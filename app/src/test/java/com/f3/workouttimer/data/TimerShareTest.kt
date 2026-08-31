package com.f3.workouttimer.data

import com.f3.workouttimer.model.Block
import com.f3.workouttimer.model.Stage
import com.f3.workouttimer.model.WorkoutTimer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerShareTest {

    private val timer = WorkoutTimer(
        name = "Sneaky Squirrel",
        openingMessage = "Circle up, gentlemen",
        closingMessage = "Good work. See you Thursday.",
        announceNextExercise = false,
        voiceName = "en-us-x-sfg#male_1-local",
        voiceEngine = "com.google.android.tts",
        blocks = listOf(
            Block(
                name = "Warm-up",
                rounds = 2,
                work = Stage(enabled = true, seconds = 30, message = "Go"),
                rest = Stage(enabled = true, seconds = 10),
                transition = Stage(enabled = false, seconds = 0),
                exercises = listOf("Side Straddle Hop", "5 Squats, 5 Merkins"),
            ),
            Block(
                name = "Cool-down",
                rounds = 1,
                work = Stage(enabled = true, seconds = 90),
                rest = Stage(enabled = false, seconds = 0),
                transition = Stage(enabled = false, seconds = 0),
            ),
        ),
    )

    @Test
    fun `a shared workout survives the round trip`() {
        val restored = TimerShare.decode(TimerShare.link(timer))!!

        assertEquals(timer.name, restored.name)
        assertEquals(timer.openingMessage, restored.openingMessage)
        assertEquals(timer.closingMessage, restored.closingMessage)
        assertEquals(timer.announceNextExercise, restored.announceNextExercise)
        assertEquals(timer.totalSeconds(), restored.totalSeconds())
        assertEquals(timer.blocks.map { it.name }, restored.blocks.map { it.name })
        assertEquals(timer.blocks.map { it.rounds }, restored.blocks.map { it.rounds })
        assertEquals(timer.blocks.map { it.exercises }, restored.blocks.map { it.exercises })
        assertEquals(timer.blocks.map { it.work }, restored.blocks.map { it.work })
        assertEquals(timer.blocks.map { it.rest }, restored.blocks.map { it.rest })
    }

    @Test
    fun `an import gets fresh ids so it never overwrites a saved timer`() {
        val restored = TimerShare.decode(TimerShare.link(timer))!!

        assertNotEquals(timer.id, restored.id)
        assertTrue(restored.id.isNotBlank())
        assertTrue(restored.blocks.all { it.id.isNotBlank() })
        assertNotEquals(
            timer.blocks.map { it.id },
            restored.blocks.map { it.id },
        )
        // Two imports of the same link are two separate timers.
        val second = TimerShare.decode(TimerShare.link(timer))!!
        assertNotEquals(restored.id, second.id)
    }

    @Test
    fun `the sender's voice choice does not follow the workout`() {
        val restored = TimerShare.decode(TimerShare.link(timer))!!

        // The sender's engine may not even be installed on this phone.
        assertEquals("", restored.voiceName)
        assertEquals("", restored.voiceEngine)
    }

    @Test
    fun `a link buried in a pasted message is still found`() {
        val message = "Here's Thursday: ${TimerShare.link(timer)} — see you in the gloom"

        assertEquals(timer.name, TimerShare.decode(message)?.name)
    }

    @Test
    fun `the bare payload works without the link wrapper`() {
        assertEquals(timer.name, TimerShare.decode(TimerShare.encode(timer))?.name)
    }

    @Test
    fun `junk decodes to nothing rather than a broken timer`() {
        assertNull(TimerShare.decode(""))
        assertNull(TimerShare.decode("   "))
        assertNull(TimerShare.decode("hello there"))
        assertNull(TimerShare.decode("f3timer://import?d=notrealbase64!!"))
        assertNull(TimerShare.decode("https://example.com/something"))
    }

    @Test
    fun `an empty workout is not importable`() {
        assertNull(TimerShare.decode(TimerShare.encode(WorkoutTimer(blocks = emptyList()))))
    }

    @Test
    fun `even a big workout stays short enough to paste`() {
        val big = timer.copy(
            blocks = (1..8).map { i ->
                Block(
                    name = "Block $i",
                    rounds = 4,
                    exercises = listOf("5 Squats, 5 Merkins, 5 Sit-ups", "Burpees", "Plank"),
                )
            }
        )
        // A link goes into a Slack message or a text, so it has to stay sane.
        val length = TimerShare.link(big).length
        assertTrue("link was $length characters", length < 1500)
    }

    @Test
    fun `the share text carries a summary and the link`() {
        val text = TimerShare.shareText(timer)

        assertTrue(text.startsWith("Sneaky Squirrel"))
        assertTrue(text.contains("2 blocks"))
        assertTrue(text.contains("Warm-up → Cool-down"))
        assertTrue(text.contains(TimerShare.link(timer)))
    }
}
