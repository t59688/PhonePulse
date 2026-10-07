package com.aizeek.phonepulse

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aizeek.phonepulse.update.ApkVerifier
import com.aizeek.phonepulse.update.GitHubRelease
import com.aizeek.phonepulse.update.ReleaseVersion
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ApkVerifierTest {
    @Test fun `truncated APK and mismatched digest never proceed to installation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("apk-test-", ".apk", context.cacheDir)
        try {
            file.writeText("test data")
            val release = GitHubRelease(ReleaseVersion(1, 1, 0), "test", "", "test.apk", "", file.length())
            val verifier = ApkVerifier(context)
            val sizeError = assertThrows(IOException::class.java) { verifier.verify(file, release.copy(size = file.length() + 1)) }
            assertTrue(sizeError.message!!.contains("大小不符"))
            val hashError = assertThrows(IOException::class.java) { verifier.verify(file, release.copy(sha256 = "0".repeat(64))) }
            assertTrue(hashError.message!!.contains("校验失败"))
        } finally { file.delete() }
    }
}
