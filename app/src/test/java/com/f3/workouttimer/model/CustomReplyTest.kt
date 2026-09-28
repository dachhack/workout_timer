package com.f3.workouttimer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomReplyTest {

    private val sprocket = CustomReply(
        trigger = "Who has the best pushup form?",
        reply = "It's Sprocket. Hands down.",
    )
    private val coffee = CustomReply(
        trigger = "where is coffeeteria",
        reply = "Same place as always.",
    )
    private val replies = listOf(sprocket, coffee)

    @Test
    fun `the phrase as written`() {
        assertEquals(sprocket, matchCustomReply("who has the best pushup form", replies))
    }

    @Test
    fun `a recogniser splitting pushup into two words still matches`() {
        assertEquals(sprocket, matchCustomReply("who has the best push up form", replies))
    }

    @Test
    fun `losing a word to the wind is forgiven`() {
        // "form" missed: 3 of 4 key words is still a match.
        assertEquals(sprocket, matchCustomReply("who has the best pushup", replies))
    }

    @Test
    fun `losing most of it is not`() {
        assertNull(matchCustomReply("who has", replies))
        assertNull(matchCustomReply("best", replies))
    }

    @Test
    fun `filler words and punctuation are ignored`() {
        assertEquals(sprocket, matchCustomReply("WHO HAS THE BEST PUSHUP FORM???", replies))
        assertEquals(coffee, matchCustomReply("hey, where is the coffeeteria", replies))
    }

    @Test
    fun `the closest trigger wins when several could fit`() {
        val vague = CustomReply(trigger = "who has best form", reply = "Everyone but you.")
        val both = listOf(sprocket, vague)

        // An exact hit on the shorter trigger scores 1.0 and takes it.
        assertEquals(vague, matchCustomReply("who has best form", both))
    }

    @Test
    fun `unrelated chatter says nothing`() {
        assertNull(matchCustomReply("my legs hurt", replies))
        assertNull(matchCustomReply("", replies))
        assertNull(matchCustomReply("next block", replies))
    }

    @Test
    fun `half-written replies are skipped`() {
        val incomplete = listOf(
            CustomReply(trigger = "who has the best pushup form", reply = ""),
            CustomReply(trigger = "", reply = "Sprocket"),
        )

        assertNull(matchCustomReply("who has the best pushup form", incomplete))
    }

    @Test
    fun `a one-word trigger has to be a distinctive word`() {
        val single = listOf(CustomReply(trigger = "Sprocket", reply = "Present."))

        assertEquals("Present.", matchCustomReply("is sprocket here", single)?.reply)
        assertNull(matchCustomReply("nobody said that", single))
    }

    @Test
    fun `count me down is a command, not banter`() {
        assertEquals(VoiceCommand.COUNTDOWN, parseVoiceCommand("count me down"))
        assertEquals(VoiceCommand.COUNTDOWN, parseVoiceCommand("count us down"))
    }
}
