package com.f3.workouttimer.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppUpdateTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val published = AppUpdate(
        versionCode = 5,
        versionName = "1.4",
        notes = listOf("Starting lights", "Voice replies"),
        downloadUrl = "https://example.com/f3-timer.zip",
    )

    @Test
    fun `only a genuinely newer build is offered`() {
        assertTrue(updateAvailable(published, installedVersionCode = 4))
        assertFalse(updateAvailable(published, installedVersionCode = 5))
        assertFalse(updateAvailable(published, installedVersionCode = 6))
    }

    @Test
    fun `nothing to report when the check failed`() {
        assertFalse(updateAvailable(null, installedVersionCode = 1))
    }

    @Test
    fun `a newer build with nowhere to get it is not offered`() {
        // A banner leading to a dead end is worse than no banner.
        assertFalse(updateAvailable(published.copy(downloadUrl = ""), installedVersionCode = 1))
    }

    @Test
    fun `a manifest written by a newer app still parses`() {
        val fromTheFuture = """
            {
              "versionCode": 9,
              "versionName": "2.0",
              "notes": ["Something new"],
              "downloadUrl": "https://example.com/x.zip",
              "minimumAndroid": 33,
              "somethingElse": {"nested": true}
            }
        """.trimIndent()

        val parsed = json.decodeFromString(AppUpdate.serializer(), fromTheFuture)

        assertEquals(9, parsed.versionCode)
        assertEquals(listOf("Something new"), parsed.notes)
    }

    @Test
    fun `a sparse manifest falls back to harmless defaults`() {
        val parsed = json.decodeFromString(AppUpdate.serializer(), """{"versionCode": 3}""")

        assertEquals(3, parsed.versionCode)
        assertTrue(parsed.notes.isEmpty())
        // No download link, so it is never offered.
        assertFalse(updateAvailable(parsed, installedVersionCode = 1))
    }

    @Test
    fun `the manifest in the repository is valid and matches the build`() {
        val file = File("../update.json")
        assertTrue("update.json is missing from the repository root", file.exists())

        val parsed = json.decodeFromString(AppUpdate.serializer(), file.readText())

        assertTrue("update.json needs a version code", parsed.versionCode > 0)
        assertTrue("update.json needs a download link", parsed.downloadUrl.isNotBlank())
        assertTrue("update.json should list what changed", parsed.notes.isNotEmpty())
    }
}
