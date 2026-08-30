package com.f3.workouttimer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.f3.workouttimer.model.Block
import com.f3.workouttimer.model.Stage
import com.f3.workouttimer.model.WorkoutTimer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val Context.dataStore by preferencesDataStore(name = "timers")
private val TIMERS_KEY = stringPreferencesKey("timers_json")

class TimerRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val timers: Flow<List<WorkoutTimer>> = context.dataStore.data.map { prefs ->
        decode(prefs[TIMERS_KEY])
    }

    suspend fun save(timer: WorkoutTimer) {
        context.dataStore.edit { prefs ->
            val current = decode(prefs[TIMERS_KEY])
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
            val current = decode(prefs[TIMERS_KEY])
            prefs[TIMERS_KEY] = json.encodeToString(current.filterNot { it.id == id })
        }
    }

    private fun decode(raw: String?): List<WorkoutTimer> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.map { element ->
                val obj = element.jsonObject
                if (obj.containsKey("blocks")) {
                    json.decodeFromJsonElement(WorkoutTimer.serializer(), obj)
                } else {
                    migrateLegacy(obj)
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Converts a timer saved before the workout became a list of blocks: the
     * old flat work/rest/transition stages become one block, and the old
     * before/after blocks become one single-interval block each.
     */
    private fun migrateLegacy(obj: JsonObject): WorkoutTimer {
        fun str(key: String, default: String = "") =
            runCatching { obj[key]?.jsonPrimitive?.content }.getOrNull() ?: default

        fun int(key: String, default: Int) =
            runCatching { obj[key]?.jsonPrimitive?.int }.getOrNull() ?: default

        fun stage(key: String, default: Stage) =
            runCatching { obj[key]?.let { json.decodeFromJsonElement(Stage.serializer(), it) } }
                .getOrNull() ?: default

        fun oneShot(name: String, element: JsonElement?): Block? {
            val o = runCatching { element?.jsonObject }.getOrNull() ?: return null
            val seconds = runCatching { o["seconds"]?.jsonPrimitive?.int }.getOrNull() ?: 0
            if (seconds <= 0) return null
            val enabled = runCatching { o["enabled"]?.jsonPrimitive?.boolean }.getOrNull() ?: true
            if (!enabled) return null
            return Block(
                name = runCatching { o["name"]?.jsonPrimitive?.content }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: name,
                rounds = 1,
                work = Stage(
                    enabled = true,
                    seconds = seconds,
                    message = runCatching { o["message"]?.jsonPrimitive?.content }.getOrNull()
                        .orEmpty(),
                ),
                rest = Stage(enabled = false, seconds = 0),
                transition = Stage(enabled = false, seconds = 0),
            )
        }

        val exercises = runCatching {
            obj["exercises"]?.jsonArray?.map { it.jsonPrimitive.content }
        }.getOrNull().orEmpty()
        val legacyRounds = int("rounds", 1)
        // Rounds used to count work intervals with the exercises cycling across
        // them; now every round runs the whole list, so scale to keep the
        // interval count roughly the same.
        val rounds =
            if (exercises.isEmpty()) legacyRounds
            else (legacyRounds / exercises.size).coerceAtLeast(1)

        val main = Block(
            name = str("name", "Beatdown"),
            rounds = rounds,
            work = stage("work", Stage(enabled = true, seconds = 45)),
            rest = stage("rest", Stage(enabled = true, seconds = 15)),
            transition = stage("transition", Stage(enabled = false, seconds = 10)),
            exercises = exercises,
        )

        val before = buildList {
            oneShot("Warm-up", obj["intro"])?.let { add(it) }
            runCatching { obj["blocksBefore"]?.jsonArray }.getOrNull()?.forEach { el ->
                oneShot("Block", el)?.let { add(it) }
            }
        }
        val after = buildList {
            runCatching { obj["blocksAfter"]?.jsonArray }.getOrNull()?.forEach { el ->
                oneShot("Block", el)?.let { add(it) }
            }
            oneShot("Cool-down", obj["outro"])?.let { add(it) }
        }

        return WorkoutTimer(
            id = str("id").ifBlank { java.util.UUID.randomUUID().toString() },
            name = str("name", "Beatdown"),
            blocks = before + main + after,
            voiceName = str("voiceName"),
            voiceEngine = str("voiceEngine"),
        )
    }

    companion object {
        @Volatile private var instance: TimerRepository? = null
        fun get(context: Context): TimerRepository =
            instance ?: synchronized(this) {
                instance ?: TimerRepository(context.applicationContext).also { instance = it }
            }
    }
}
