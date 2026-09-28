package com.f3.workouttimer.data

import com.f3.workouttimer.model.AppUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Looks up the published build description. Any failure — no signal at an AO,
 * a mangled file, a repository that has not published one yet — simply means
 * no update to report, never an error in the user's face.
 */
object UpdateChecker {

    /**
     * HEAD resolves to whatever the repository's default branch is, so this
     * keeps working if the branch is renamed or merged into main.
     */
    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/dachhack/workout_timer/HEAD/update.json"

    private const val TIMEOUT_MS = 6_000

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(): AppUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                json.decodeFromString(AppUpdate.serializer(), body)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}
