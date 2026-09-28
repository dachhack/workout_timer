package com.f3.workouttimer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.f3.workouttimer.model.CustomReply
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.banterStore by preferencesDataStore(name = "banter")
private val REPLIES_KEY = stringPreferencesKey("replies_json")

/** The custom call-and-response lines. */
class BanterRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val replies: Flow<List<CustomReply>> = context.banterStore.data.map { prefs ->
        decode(prefs[REPLIES_KEY])
    }

    suspend fun save(reply: CustomReply) {
        context.banterStore.edit { prefs ->
            val current = decode(prefs[REPLIES_KEY])
            val updated = if (current.any { it.id == reply.id }) {
                current.map { if (it.id == reply.id) reply else it }
            } else {
                current + reply
            }
            prefs[REPLIES_KEY] = json.encodeToString(REPLY_LIST, updated)
        }
    }

    suspend fun delete(id: String) {
        context.banterStore.edit { prefs ->
            val kept = decode(prefs[REPLIES_KEY]).filterNot { it.id == id }
            prefs[REPLIES_KEY] = json.encodeToString(REPLY_LIST, kept)
        }
    }

    private fun decode(raw: String?): List<CustomReply> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(REPLY_LIST, raw) }.getOrDefault(emptyList())
    }

    companion object {
        private val REPLY_LIST = ListSerializer(CustomReply.serializer())

        @Volatile private var instance: BanterRepository? = null
        fun get(context: Context): BanterRepository =
            instance ?: synchronized(this) {
                instance ?: BanterRepository(context.applicationContext).also { instance = it }
            }
    }
}
