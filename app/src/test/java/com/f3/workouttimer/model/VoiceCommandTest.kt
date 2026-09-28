package com.f3.workouttimer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandTest {

    @Test
    fun `the phrase this was built for`() {
        assertEquals(VoiceCommand.NEXT_BLOCK, parseVoiceCommand("Start the next block!"))
    }

    @Test
    fun `a block is distinguished from a plain skip`() {
        assertEquals(VoiceCommand.NEXT_BLOCK, parseVoiceCommand("next block"))
        assertEquals(VoiceCommand.NEXT_BLOCK, parseVoiceCommand("skip to the next station"))
        assertEquals(VoiceCommand.NEXT_BLOCK, parseVoiceCommand("move on to the next round"))

        assertEquals(VoiceCommand.NEXT_STAGE, parseVoiceCommand("next"))
        assertEquals(VoiceCommand.NEXT_STAGE, parseVoiceCommand("skip this"))
    }

    @Test
    fun `pause and resume`() {
        assertEquals(VoiceCommand.PAUSE, parseVoiceCommand("pause"))
        assertEquals(VoiceCommand.PAUSE, parseVoiceCommand("hold up"))
        assertEquals(VoiceCommand.RESUME, parseVoiceCommand("resume"))
        assertEquals(VoiceCommand.RESUME, parseVoiceCommand("keep going"))
    }

    @Test
    fun `ending the workout needs saying what to end`() {
        assertEquals(VoiceCommand.END_WORKOUT, parseVoiceCommand("end the workout"))
        assertEquals(VoiceCommand.END_WORKOUT, parseVoiceCommand("stop the beatdown"))

        // A bare "stop" is far too easy to mishear to throw a run away on.
        assertNull(parseVoiceCommand("stop"))
        assertNull(parseVoiceCommand("stop it"))
    }

    @Test
    fun `asking how much is left`() {
        assertEquals(VoiceCommand.TIME_LEFT, parseVoiceCommand("how long"))
        assertEquals(VoiceCommand.TIME_LEFT, parseVoiceCommand("how much time is left"))
    }

    @Test
    fun `punctuation and case do not matter`() {
        assertEquals(VoiceCommand.NEXT_BLOCK, parseVoiceCommand("NEXT BLOCK!!!"))
        assertEquals(VoiceCommand.PAUSE, parseVoiceCommand("  Pause.  "))
    }

    @Test
    fun `mumblechatter is not a command`() {
        assertNull(parseVoiceCommand(""))
        assertNull(parseVoiceCommand("   "))
        assertNull(parseVoiceCommand("my legs hurt"))
        assertNull(parseVoiceCommand("who picked this workout"))
    }

    @Test
    fun `durations are spoken, not read off a clock`() {
        assertEquals("3 minutes 20 seconds", spokenDuration(200))
        assertEquals("1 minute 1 second", spokenDuration(61))
        assertEquals("2 minutes", spokenDuration(120))
        assertEquals("45 seconds", spokenDuration(45))
        assertEquals("0 seconds", spokenDuration(-5))
    }
}
