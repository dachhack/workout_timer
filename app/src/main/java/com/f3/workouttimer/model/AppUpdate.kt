package com.f3.workouttimer.model

import kotlinx.serialization.Serializable

/**
 * What the published update.json says about the newest build. Everything is
 * optional so an older app never chokes on a file written by a newer one.
 */
@Serializable
data class AppUpdate(
    val versionCode: Int = 0,
    val versionName: String = "",
    /** One line per change, shown as a bulleted list. */
    val notes: List<String> = emptyList(),
    /** Where the new file lives — a zip or an APK. */
    val downloadUrl: String = "",
)

/**
 * Worth telling the user about only if it is genuinely newer and there is
 * somewhere to get it; a manifest with no link would be a dead end.
 */
fun updateAvailable(update: AppUpdate?, installedVersionCode: Int): Boolean {
    val candidate = update ?: return false
    return candidate.versionCode > installedVersionCode && candidate.downloadUrl.isNotBlank()
}
