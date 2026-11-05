package com.erhu.erhu.core.network.update

import com.erhu.erhu.core.network.model.GitHubAssetDto
import com.erhu.erhu.core.network.model.GitHubReleaseDto
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkUpdateValidationTest {
    private val current = ApkIdentity("com.lochanpthakke.empee3", "1.0.1", 2, 26, setOf("arm64-v8a"), setOf("original"), setOf("original"))
    private val candidate = current.copy(versionName = "1.0.2", versionCode = 3)
    private val release = UpdateCheck.Available(GitHubReleaseDto(tagName = "v1.0.2"), GitHubAssetDto(), 3)

    @Test fun `only a genuinely newer compatible APK is installable`() {
        validateUpdateIdentity(current, candidate, release, 37, listOf("arm64-v8a"))
        validateUpdateIdentity(current, candidate.copy(signers = setOf("rotated"), signingHistory = setOf("original", "rotated")), release, 37, listOf("arm64-v8a"))
        validateUpdateIdentity(current, candidate.copy(abis = emptySet()), release, 37, listOf("arm64-v8a"))
    }

    @Test fun `invalid incompatible and same-code APKs are rejected`() {
        for (apk in listOf(candidate.copy(packageName = "other"), candidate.copy(versionCode = 2), candidate.copy(versionCode = 1),
            candidate.copy(versionName = "1.0.9"), candidate.copy(signers = setOf("attacker"), signingHistory = setOf("attacker")),
            candidate.copy(minSdk = 99), candidate.copy(abis = setOf("x86_64")), candidate.copy(signers = emptySet()), candidate.copy(debuggable = true), candidate.copy(testOnly = true))) {
            var rejected = false
            try { validateUpdateIdentity(current, apk, release, 37, listOf("arm64-v8a")) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(apk.toString(), rejected)
        }
    }
}
