package com.aizeek.phonepulse.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class ApkVerifier(private val context: Context) {
    fun verify(file: File, release: GitHubRelease) {
        if (file.length() != release.size) throw IOException("安装包大小不符，请重新下载")
        release.sha256?.let { expected ->
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            if (actual != expected) throw IOException("安装包校验失败，请重新下载")
        }
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION")
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw IOException("无法解析安装包，请重新下载")
        verifyIdentity(installed, archive, release.version)
    }

    internal fun verifyIdentity(installed: PackageInfo, archive: PackageInfo, target: ReleaseVersion) {
        validateApkIdentity(identity(installed), identity(archive), target)
    }

    private fun identity(info: PackageInfo): ApkIdentity {
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        @Suppress("DEPRECATION")
        val signers = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        val history = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else signers
        return ApkIdentity(info.packageName, info.versionName.orEmpty(), code,
            signers?.map { it.toCharsString() }?.toSet().orEmpty(),
            history?.map { it.toCharsString() }?.toSet().orEmpty())
    }
}

internal data class ApkIdentity(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val signers: Set<String>,
    val signingHistory: Set<String> = signers
)

internal fun validateApkIdentity(installed: ApkIdentity, archive: ApkIdentity, target: ReleaseVersion) {
    if (archive.packageName != installed.packageName || ReleaseVersion.parse(archive.versionName) != target) {
        throw IOException("安装包的应用或版本与更新信息不符")
    }
    if (archive.versionCode <= installed.versionCode || archive.versionCode != target.versionCode) {
        throw IOException("安装包版本无法覆盖当前版本")
    }
    val signingMatches = if (installed.signers.size > 1 || archive.signers.size > 1) {
        installed.signers == archive.signers
    } else archive.signingHistory.containsAll(installed.signers)
    if (installed.signers.isEmpty() || archive.signers.isEmpty() || !signingMatches) {
        throw IOException("安装包签名与当前应用不一致，无法覆盖安装")
    }
}
