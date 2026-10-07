package com.aizeek.phonepulse.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class GitHubUpdateClient {
    suspend fun latestRelease(): GitHubRelease? = withContext(Dispatchers.IO) {
        val connection = URL("https://api.github.com/repos/${GitHubRelease.REPOSITORY}/releases/latest")
            .openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "PhonePulse-Android")
            when (connection.responseCode) {
                404 -> null
                200 -> {
                    val bytes = connection.inputStream.use { it.readBytesWithLimit(1_048_576) }
                    GitHubRelease.parse(bytes.toString(Charsets.UTF_8))
                        ?: throw IOException("最新发布没有可用的正式 APK，请稍后重试")
                }
                403, 429 -> throw IOException("GitHub 请求受限，请稍后重试")
                else -> throw IOException("GitHub 检测失败（HTTP ${connection.responseCode}）")
            }
        } finally {
            connection.disconnect()
        }
    }
}

private fun java.io.InputStream.readBytesWithLimit(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (output.size() + count > limit) throw IOException("GitHub 响应过大")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
