package com.f3.workouttimer.model

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/**
 * Something the app does at a wall-clock time: sound an alert, say a line,
 * start a workout, or any combination. An AO starts at 5:15 whether or not
 * anyone remembered to open the app.
 */
@Serializable
data class ScheduledCue(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val hour: Int = 5,
    val minute: Int = 15,
    /** Repeat days as [DayOfWeek] values (1 = Monday). Empty fires once, at the next occurrence. */
    val days: Set<Int> = emptySet(),
    val enabled: Boolean = true,
    /** Sound a tone when it fires. */
    val alert: Boolean = true,
    /** Spoken when it fires; blank says nothing. */
    val message: String = "",
    /** Workout to start when it fires; blank just plays the cue. */
    val timerId: String = "",
    /** A single block of that workout; blank runs the whole thing. */
    val blockId: String = "",
) {
    val time: LocalTime
        get() = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    val repeats: Boolean get() = days.isNotEmpty()

    /** True when the cue would do nothing at all. */
    val isSilent: Boolean get() = !alert && message.isBlank() && timerId.isBlank()

    /**
     * The next time this cue should fire, strictly after [from]. A cue with no
     * repeat days fires at the next occurrence of its time — later today, or
     * tomorrow if that has already passed.
     */
    fun nextOccurrence(from: LocalDateTime): LocalDateTime {
        val today = from.toLocalDate()
        if (!repeats) {
            val todayAt = LocalDateTime.of(today, time)
            return if (todayAt.isAfter(from)) todayAt else LocalDateTime.of(today.plusDays(1), time)
        }
        for (offset in 0..7) {
            val date = today.plusDays(offset.toLong())
            if (date.dayOfWeek.value !in days) continue
            val candidate = LocalDateTime.of(date, time)
            if (candidate.isAfter(from)) return candidate
        }
        // Only reachable if days somehow holds no valid weekday.
        return LocalDateTime.of(today.plusDays(1), time)
    }

    fun nextTriggerMillis(
        from: LocalDateTime = LocalDateTime.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long = nextOccurrence(from).atZone(zone).toInstant().toEpochMilli()

    /** "5:15 AM" */
    fun timeLabel(): String {
        val suffix = if (hour < 12) "AM" else "PM"
        val h = when {
            hour % 12 == 0 -> 12
            else -> hour % 12
        }
        return "%d:%02d %s".format(h, minute, suffix)
    }

    /** "Mon, Wed, Fri" / "Every day" / "Once" */
    fun daysLabel(): String = when {
        days.isEmpty() -> "Once"
        days.size == 7 -> "Every day"
        days == WEEKDAYS -> "Weekdays"
        else -> days.sorted().joinToString(", ") { shortDayName(it) }
    }

    /** "Alert + speech · starts Sneaky Squirrel" */
    fun actionLabel(timerName: String?, blockName: String?): String {
        val parts = buildList {
            if (alert) add("Alert")
            if (message.isNotBlank()) add("Speech")
            if (timerName != null) {
                add(if (blockName != null) "Starts $blockName" else "Starts $timerName")
            }
        }
        return if (parts.isEmpty()) "Does nothing yet" else parts.joinToString(" · ")
    }
}

val WEEKDAYS = setOf(1, 2, 3, 4, 5)

fun shortDayName(day: Int): String =
    DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.getDefault())
