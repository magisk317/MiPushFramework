package io.github.magisk317.mipush.main.viewmodel

import io.github.magisk317.mipush.manager.api.ManagerHandshake
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RuntimeCommitMismatchTest {
    @Test
    fun `matching commits do not warn`() {
        val availability = available(runtimeCommit = "abc1234")

        assertNull(availability.runtimeCommitMismatch(moduleCommit = "abc1234"))
    }

    @Test
    fun `different commits report mismatch with both sides`() {
        val availability = available(runtimeCommit = "def5678")

        val mismatch = availability.runtimeCommitMismatch(moduleCommit = "abc1234")

        assertEquals(
            RuntimeCommitMismatch(moduleCommit = "abc1234", runtimeCommit = "def5678"),
            mismatch,
        )
    }

    @Test
    fun `runtime without a usable commit does not warn`() {
        assertNull(available(runtimeCommit = null).runtimeCommitMismatch(moduleCommit = "abc1234"))
        assertNull(available(runtimeCommit = "").runtimeCommitMismatch(moduleCommit = "abc1234"))
        assertNull(available(runtimeCommit = "unknown").runtimeCommitMismatch(moduleCommit = "abc1234"))
    }

    @Test
    fun `module without a usable commit does not warn`() {
        assertNull(available(runtimeCommit = "def5678").runtimeCommitMismatch(moduleCommit = "unknown"))
        assertNull(available(runtimeCommit = "def5678").runtimeCommitMismatch(moduleCommit = ""))
    }

    @Test
    fun `non-available states never warn`() {
        assertNull(ManagerRuntimeAvailability.Binding.runtimeCommitMismatch(moduleCommit = "abc1234"))
        assertNull(ManagerRuntimeAvailability.RuntimeMissing.runtimeCommitMismatch(moduleCommit = "abc1234"))
        assertNull(
            ManagerRuntimeAvailability.Incompatible(
                handshake = handshake(runtimeCommit = "def5678"),
                reason = "protocol_incompatible",
            ).runtimeCommitMismatch(moduleCommit = "abc1234"),
        )
    }

    private fun available(runtimeCommit: String?) = ManagerRuntimeAvailability.Available(
        handshake = handshake(runtimeCommit = runtimeCommit),
    )

    private fun handshake(runtimeCommit: String?) = ManagerHandshake(
        protocolMajor = ManagerProtocol.MAJOR,
        protocolMinor = ManagerProtocol.MINOR,
        runtimeVersionName = "test",
        runtimeVersionCode = 1,
        supportedCapabilities = emptyList(),
        maxPageSize = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
        maxPayloadBytes = ManagerProtocol.DEFAULT_MAX_PAYLOAD_BYTES,
        runtimeCommit = runtimeCommit,
    )
}
