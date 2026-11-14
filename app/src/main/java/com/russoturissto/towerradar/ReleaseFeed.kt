package com.russoturissto.towerradar

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class TowerRelease(
    val version: String,
    val title: String,
    val prerelease: Boolean,
    val apkUrl: String,
    val apkBytes: Long,
    val digest: String?,
    val releasePage: String
)

/** Unauthenticated public GitHub Releases; no GitHub credentials or background notifications. */
object ReleaseFeed {
    private const val REPO = "RussoTurissto/TowerRadar"
    private val versionRegex = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$")
    private val allowedAsset = Regex("^TowerRadar-v\\d+\\.\\d+\\.\\d+\\.apk$")

    fun isNewer(candidate: String, current: String): Boolean {
        val a = versionRegex.matchEntire(candidate)?.groupValues?.drop(1)
            ?.mapNotNull(String::toIntOrNull) ?: return false
        val b = versionRegex.matchEntire(current)?.groupValues?.drop(1)
            ?.mapNotNull(String::toIntOrNull) ?: return false
        for (i in 0..2) if (a[i] != b[i]) return a[i] > b[i]
        return false
    }

    fun parse(json: String, testChannel: Boolean): TowerRelease? {
        val releases = JSONArray(json)
        for (index in 0 until releases.length()) {
            val obj = releases.optJSONObject(index) ?: continue
            if (obj.optBoolean("draft")) continue
            val prerelease = obj.optBoolean("prerelease")
            if (testChannel != prerelease) continue
            val tag = obj.optString("tag_name")
            if (!versionRegex.matches(tag)) continue
            val assets = obj.optJSONArray("assets") ?: continue
            for (i in 0 until assets.length()) {
                val file = assets.optJSONObject(i) ?: continue
                val filename = file.optString("name")
                val url = file.optString("browser_download_url")
                if (!allowedAsset.matches(filename) || !url.startsWith(
                    "https://github.com/" + REPO + "/releases/download/" + tag + "/"
                )) continue
                if (file.optLong("size") < 1024 * 1024) continue
                if (filename != "TowerRadar-" + tag + ".apk") continue
                val digest = file.optString("digest").takeIf {
                    it.matches(Regex("sha256:[0-9a-fA-F]{64}"))
                }
                return TowerRelease(tag.removePrefix("v"), obj.optString("name"),
                    prerelease, url, file.optLong("size"), digest,
                    "https://github.com/" + REPO + "/releases/tag/" + tag)
            }
        }
        return null
    }

    fun query(testChannel: Boolean): TowerRelease? {
        val c = URL("https://api.github.com/repos/" + REPO +
            "/releases?per_page=25").openConnection() as HttpURLConnection
        c.connectTimeout = 7500
        c.readTimeout = 10000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "TowerRadar-Android-UpdateCheck")
        return try {
            if (c.responseCode != 200)
                throw IllegalStateException("GitHub API: HTTP " + c.responseCode)
            parse(c.inputStream.bufferedReader().use { it.readText() }, testChannel)
        } finally { c.disconnect() }
    }
}
