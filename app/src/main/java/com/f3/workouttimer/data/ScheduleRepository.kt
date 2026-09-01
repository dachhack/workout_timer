package com.f3.workouttimer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.f3.workouttimer.model.ScheduledCue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.scheduleStore by preferencesDataStore(name = "schedules")
private val CUES_KEY = stringPreferencesKey("cues_json")

class ScheduleRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val cues: Flow<List<ScheduledCue>> = context.scheduleStore.data.map { prefs ->
        decode(prefs[CUES_KEY])
    }

    suspend fun all(): List<ScheduledCue> = cues.first()

    suspend fun save(cue: ScheduledCue) {
        context.scheduleStore.edit { prefs ->
            val current = decode(prefs[CUES_KEY])
            val updated = if (current.any { it.id == cue.id }) {
                current.map { if (it.id == cue.id) cue else it }
            } else {
                current + cue
            }
            prefs[CUES_KEY] = encode(updated)
        }
    }

    suspend fun delete(id: String) {
        context.scheduleStore.edit { prefs ->
            prefs[CUES_KEY] = encode(decode(prefs[CUES_KEY]).filterNot { it.id == id })
        }
    }

    private fun encode(cues: List<ScheduledCue>): String =
        json.encodeToString(CUE_LIST, cues)

    private fun decode(raw: String?): List<ScheduledCue> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(CUE_LIST, raw) }.getOrDefault(emptyList())
    }

    companion object {
        private val CUE_LIST = ListSerializer(ScheduledCue.serializer())

        @Volatile private var instance: ScheduleRepository? = null
        fun get(context: Context): ScheduleRepository =
            instance ?: synchronized(this) {
                instance ?: ScheduleRepository(context.applicationContext).also { instance = it }
            }
    }
}
