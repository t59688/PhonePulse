package com.aizeek.phonepulse

import com.aizeek.phonepulse.update.ApkIdentity
import com.aizeek.phonepulse.update.ReleaseVersion
import com.aizeek.phonepulse.update.validateApkIdentity
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class ApkIdentityTest {
    private val installed = ApkIdentity("com.aizeek.phonepulse", "1.0.0", 10_000_999, setOf("original"))
    private val target = ReleaseVersion(1, 1, 0)
    private val update = ApkIdentity(installed.packageName, target.name, target.versionCode, installed.signers)

    @Test fun `same application newer version and signing certificate are accepted`() {
        validateApkIdentity(installed, update, target)
    }

    @Test fun `different app tag mismatch and rollback are rejected`() {
        assertThrows(IOException::class.java) { validateApkIdentity(installed, update.copy(packageName = "other.app"), target) }
        assertThrows(IOException::class.java) { validateApkIdentity(installed, update.copy(versionName = "1.2.0"), target) }
        assertThrows(IOException::class.java) { validateApkIdentity(installed, update.copy(versionCode = installed.versionCode), target) }
    }

    @Test fun `unrelated missing or additional signing certificates are rejected`() {
        assertThrows(IOException::class.java) { validateApkIdentity(installed, update.copy(signers = setOf("other"), signingHistory = setOf("other")), target) }
        assertThrows(IOException::class.java) { validateApkIdentity(installed, update.copy(signers = emptySet()), target) }
        assertThrows(IOException::class.java) { validateApkIdentity(installed, update.copy(signers = setOf("original", "other")), target) }
    }

    @Test fun `certificate rotation accepts the installed certificate in verified signing history`() {
        validateApkIdentity(installed, update.copy(signers = setOf("new"), signingHistory = setOf("original", "new")), target)
    }
}
