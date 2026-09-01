package com.f3.workouttimer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ScheduledCueTest {

    // Wednesday 2026-09-02, 06:00.
    private val wednesdayMorning = LocalDateTime.of(2026, 9, 2, 6, 0)

    private fun cue(hour: Int, minute: Int = 0, days: Set<Int> = emptySet()) =
        ScheduledCue(hour = hour, minute = minute, days = days)

    @Test
    fun `a one-shot fires later today when the time is still ahead`() {
        val next = cue(17, 30).nextOccurrence(wednesdayMorning)

        assertEquals(LocalDateTime.of(2026, 9, 2, 17, 30), next)
    }

    @Test
    fun `a one-shot rolls to tomorrow when the time has passed`() {
        val next = cue(5, 15).nextOccurrence(wednesdayMorning)

        assertEquals(LocalDateTime.of(2026, 9, 3, 5, 15), next)
    }

    @Test
    fun `the exact current minute counts as passed, not as now`() {
        val next = cue(6, 0).nextOccurrence(wednesdayMorning)

        // Otherwise a repeating cue would re-fire the instant it finished.
        assertEquals(LocalDateTime.of(2026, 9, 3, 6, 0), next)
    }

    @Test
    fun `a weekday cue skips to the next matching day`() {
        // Monday, Wednesday, Friday at 05:15; it is already Wednesday 06:00.
        val next = cue(5, 15, setOf(1, 3, 5)).nextOccurrence(wednesdayMorning)

        assertEquals(LocalDateTime.of(2026, 9, 4, 5, 15), next) // Friday
    }

    @Test
    fun `a repeating cue still fires later the same day`() {
        val next = cue(17, 0, setOf(1, 3, 5)).nextOccurrence(wednesdayMorning)

        assertEquals(LocalDateTime.of(2026, 9, 2, 17, 0), next)
    }

    @Test
    fun `a once-weekly cue wraps to the following week`() {
        // Wednesday only, already past this week's time.
        val next = cue(5, 15, setOf(3)).nextOccurrence(wednesdayMorning)

        assertEquals(LocalDateTime.of(2026, 9, 9, 5, 15), next)
    }

    @Test
    fun `labels read the way a Q would say them`() {
        assertEquals("5:15 AM", cue(5, 15).timeLabel())
        assertEquals("12:00 AM", cue(0, 0).timeLabel())
        assertEquals("12:30 PM", cue(12, 30).timeLabel())
        assertEquals("6:05 PM", cue(18, 5).timeLabel())

        assertEquals("Once", cue(5).daysLabel())
        assertEquals("Weekdays", cue(5, days = WEEKDAYS).daysLabel())
        assertEquals("Every day", cue(5, days = (1..7).toSet()).daysLabel())
    }

    @Test
    fun `a cue that does nothing is flagged rather than scheduled`() {
        val doesNothing = ScheduledCue(alert = false, message = "", timerId = "")
        assertTrue(doesNothing.isSilent)

        assertFalse(doesNothing.copy(alert = true).isSilent)
        assertFalse(doesNothing.copy(message = "Circle up").isSilent)
        assertFalse(doesNothing.copy(timerId = "abc").isSilent)
    }

    @Test
    fun `the action summary names what will actually happen`() {
        val cue = ScheduledCue(alert = true, message = "Circle up", timerId = "t1")

        assertEquals("Alert · Speech · Starts Beatdown", cue.actionLabel("Beatdown", null))
        assertEquals("Alert · Speech · Starts Warm-up", cue.actionLabel("Beatdown", "Warm-up"))
        assertEquals("Alert · Speech", cue.actionLabel(null, null))
        assertEquals("Alert", cue.copy(message = "").actionLabel(null, null))
        assertEquals("Does nothing yet", ScheduledCue(alert = false).actionLabel(null, null))
    }
}
