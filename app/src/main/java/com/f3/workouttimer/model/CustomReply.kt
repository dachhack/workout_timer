package com.f3.workouttimer.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** Something the PAX can ask, and what the app fires back. Purely for fun. */
@Serializable
data class CustomReply(
    val id: String = UUID.randomUUID().toString(),
    val trigger: String = "",
    val reply: String = "",
) {
    val isUsable: Boolean get() = trigger.isNotBlank() && reply.isNotBlank()
}

/** Words too common to count towards a match. */
private val FILLER = setOf(
    "a", "an", "the", "is", "are", "was", "were", "be", "to", "of", "in", "on",
    "at", "for", "and", "or", "it", "its", "my", "our", "your", "you", "we",
    "do", "does", "did", "has", "have", "had", "that", "this", "with", "hey",
)

private fun normalize(text: String): String =
    text.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")

private fun keyWords(text: String): List<String> =
    normalize(text).split(' ').filter { it.isNotBlank() && it !in FILLER }

/**
 * Finds the reply whose trigger best matches what was heard.
 *
 * Recognition is lossy outdoors, so this does not demand the exact sentence:
 * it asks that most of the trigger's meaningful words turn up, in any order.
 * Words are looked for in a de-spaced copy of what was heard, so a trigger
 * written "pushup" still matches a recogniser that returns "push up".
 */
fun matchCustomReply(heard: String, replies: List<CustomReply>): CustomReply? {
    val heardWords = keyWords(heard)
    if (heardWords.isEmpty()) return null
    val haystack = heardWords.joinToString("")

    var best: CustomReply? = null
    var bestScore = 0.0

    for (reply in replies) {
        if (!reply.isUsable) continue
        val wanted = keyWords(reply.trigger)
        if (wanted.isEmpty()) continue

        val found = wanted.count { word -> haystack.contains(word) }
        val score = found.toDouble() / wanted.size

        // A single-word trigger has to be present and distinctive; longer ones
        // may lose a word to the wind.
        val enough = when (wanted.size) {
            1 -> found == 1 && wanted[0].length >= 4
            else -> score >= MIN_SCORE && found >= 2
        }
        if (enough && score > bestScore) {
            bestScore = score
            best = reply
        }
    }
    return best
}

private const val MIN_SCORE = 0.7
