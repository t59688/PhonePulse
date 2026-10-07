package com.aizeek.phonepulse.update

import org.json.JSONObject
import java.net.URI

data class ReleaseVersion(val major: Int, val minor: Int, val patch: Int, val beta: Int? = null) : Comparable<ReleaseVersion> {
    val name: String get() = "$major.$minor.$patch" + (beta?.let { "-beta.$it" } ?: "")
    val versionCode: Long get() = (major.toLong() * 10000 + minor * 100 + patch) * 1000 + (beta ?: 999)
    override fun compareTo(other: ReleaseVersion): Int = versionCode.compareTo(other.versionCode)

    companion object {
        fun parse(value: String): ReleaseVersion? {
            val match = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-beta\\.(\\d+))?$").matchEntire(value) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            val patch = match.groupValues[3].toIntOrNull() ?: return null
            val beta = match.groupValues[4].takeIf { it.isNotEmpty() }?.toIntOrNull()
            if (minor !in 0..99 || patch !in 0..99 || major > 214 ||
                (match.groupValues[4].isNotEmpty() && (beta == null || beta !in 1..998))) return null
            return ReleaseVersion(major, minor, patch, beta).takeIf { it.versionCode <= Int.MAX_VALUE }
        }
    }
}

data class GitHubRelease(
    val version: ReleaseVersion,
    val title: String,
    val notes: String,
    val assetName: String,
    val downloadUrl: String,
    val size: Long,
    val sha256: String? = null
) {
    fun toJson(): String = JSONObject().apply {
        put("tag_name", "v${version.name}")
        put("name", title)
        put("body", notes)
        put("draft", false)
        put("prerelease", false)
        put("assets", org.json.JSONArray().put(JSONObject().apply {
            put("name", assetName); put("browser_download_url", downloadUrl); put("size", size)
            put("digest", sha256?.let { "sha256:$it" } ?: JSONObject.NULL)
        }))
    }.toString()

    companion object {
        const val REPOSITORY = "t59688/PhonePulse"
        fun parse(json: String): GitHubRelease? {
            val root = JSONObject(json)
            if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
            val version = ReleaseVersion.parse(root.optString("tag_name")) ?: return null
            if (version.beta != null) return null
            val assets = root.optJSONArray("assets") ?: return null
            val expectedName = "phonepulse-${version.name}-release.apk"
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                if (asset.optString("name") != expectedName) continue
                val url = asset.optString("browser_download_url")
                val uri = runCatching { URI(url) }.getOrNull() ?: continue
                if (uri.scheme != "https" || uri.host != "github.com" || uri.userInfo != null || uri.port != -1 ||
                    uri.path != "/$REPOSITORY/releases/download/${root.optString("tag_name")}/$expectedName") continue
                val size = asset.optLong("size")
                if (size <= 0) continue
                val digest = asset.optString("digest").takeIf { it.isNotBlank() && it != "null" }
                if (digest != null && !Regex("sha256:[0-9a-fA-F]{64}").matches(digest)) return null
                return GitHubRelease(version, root.optString("name", version.name), root.optString("body"),
                    expectedName, url, size, digest?.substringAfter(':')?.lowercase())
            }
            return null
        }
    }
}
