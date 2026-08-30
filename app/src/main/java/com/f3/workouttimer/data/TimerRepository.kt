package com.f3.workouttimer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.f3.workouttimer.model.WorkoutTimer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "timers")
private val TIMERS_KEY = stringPreferencesKey("timers_json")

class TimerRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val timers: Flow<List<WorkoutTimer>> = context.dataStore.data.map { prefs ->
        prefs[TIMERS_KEY]?.let {
            runCatching { json.decodeFromString<List<WorkoutTimer>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun save(timer: WorkoutTimer) {
        context.dataStore.edit { prefs ->
            val current = prefs[TIMERS_KEY]?.let {
                runCatching { json.decodeFromString<List<WorkoutTimer>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = if (current.any { it.id == timer.id }) {
                current.map { if (it.id == timer.id) timer else it }
            } else {
                current + timer
            }
            prefs[TIMERS_KEY] = json.encodeToString(updated)
        }
    }

    suspend fun delete(id: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[TIMERS_KEY]?.let {
                runCatching { json.decodeFromString<List<WorkoutTimer>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            prefs[TIMERS_KEY] = json.encodeToString(current.filterNot { it.id == id })
        }
    }

    companion object {
        @Volatile private var instance: TimerRepository? = null
        fun get(context: Context): TimerRepository =
            instance ?: synchronized(this) {
                instance ?: TimerRepository(context.applicationContext).also { instance = it }
            }
    }
}
