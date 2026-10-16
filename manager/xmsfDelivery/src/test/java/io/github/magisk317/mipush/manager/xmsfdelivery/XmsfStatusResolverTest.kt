package io.github.magisk317.mipush.manager.xmsfdelivery

import java.util.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class XmsfStatusResolverTest {

    @Test
    fun `absent package is NOT_INSTALLED regardless of other inputs`() {
        assertEquals(
            XmsfRuntimeState.NOT_INSTALLED,
            XmsfStatusResolver.decideStatus(null, hostHashes = HOST, expectedVersionName = PINNED_VERSION),
        )
    }

    @Test
    fun `foreign signature is SIGNATURE_MISMATCH even at the pinned version`() {
        val installed = installed(versionName = PINNED_VERSION, signerHashes = FOREIGN)

        assertEquals(
            XmsfRuntimeState.SIGNATURE_MISMATCH,
            XmsfStatusResolver.decideStatus(installed, hostHashes = HOST, expectedVersionName = PINNED_VERSION),
        )
    }

    @Test
    fun `foreign signature outranks an outdated version`() {
        val installed = installed(versionName = "0.0.1", signerHashes = FOREIGN)

        assertNotEquals(
            XmsfRuntimeState.UPDATE_AVAILABLE,
            XmsfStatusResolver.decideStatus(installed, hostHashes = HOST, expectedVersionName = PINNED_VERSION),
        )
    }

    @Test
    fun `same signature at an old version is UPDATE_AVAILABLE`() {
        val installed = installed(versionName = "1.0.8", signerHashes = HOST)

        assertEquals(
            XmsfRuntimeState.UPDATE_AVAILABLE,
            XmsfStatusResolver.decideStatus(installed, hostHashes = HOST, expectedVersionName = PINNED_VERSION),
        )
    }

    @Test
    fun `same signature at the pinned version is READY`() {
        val installed = installed(versionName = PINNED_VERSION, signerHashes = HOST)

        assertEquals(
            XmsfRuntimeState.READY,
            XmsfStatusResolver.decideStatus(installed, hostHashes = HOST, expectedVersionName = PINNED_VERSION),
        )
    }

    @Test
    fun `null version name on the package is UPDATE_AVAILABLE, never READY`() {
        val installed = installed(versionName = null, signerHashes = HOST)

        assertEquals(
            XmsfRuntimeState.UPDATE_AVAILABLE,
            XmsfStatusResolver.decideStatus(installed, hostHashes = HOST, expectedVersionName = PINNED_VERSION),
        )
    }

    @Test
    fun `unknown expected pin keeps the version gate open`() {
        val installed = installed(versionName = "whatever", signerHashes = HOST)

        assertEquals(
            XmsfRuntimeState.READY,
            XmsfStatusResolver.decideStatus(installed, hostHashes = HOST, expectedVersionName = null),
        )
    }

    @Test
    fun `empty host certificates never pass the signature gate`() {
        val installed = installed(versionName = PINNED_VERSION, signerHashes = emptyList())

        assertEquals(
            XmsfRuntimeState.SIGNATURE_MISMATCH,
            XmsfStatusResolver.decideStatus(installed, hostHashes = emptyList(), expectedVersionName = PINNED_VERSION),
        )
    }

    @Test
    fun `identical signer sets never report SIGNATURE_MISMATCH`() {
        // Property (seeded, 500 rounds): re-ordered but equal signer sets
        // must always pass the signature gate.
        val random = Random(20261016L)
        repeat(500) {
            val size = 1 + random.nextInt(8)
            val host = List(size) { "signer-${random.nextInt(64)}-${it}" }
            val shuffled = host.shuffled(random)
            val state = XmsfStatusResolver.decideStatus(
                installed = installed(versionName = PINNED_VERSION, signerHashes = shuffled),
                hostHashes = host,
                expectedVersionName = PINNED_VERSION,
            )
            assertNotEquals(XmsfRuntimeState.SIGNATURE_MISMATCH, state, "host=$host shuffled=$shuffled")
        }
    }

    private fun installed(versionName: String?, signerHashes: List<String>) =
        XmsfStatusResolver.Installed(
            versionName = versionName,
            versionCode = 1003003000L,
            signerHashes = signerHashes,
        )

    private companion object {
        const val PINNED_VERSION = "1.0.9"
        val HOST = listOf("host-cert-a", "host-cert-b")
        val FOREIGN = listOf("xiaomi-platform-cert")
    }
}
