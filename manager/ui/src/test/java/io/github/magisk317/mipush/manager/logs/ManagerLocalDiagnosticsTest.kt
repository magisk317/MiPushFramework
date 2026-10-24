package io.github.magisk317.mipush.manager.logs

import io.github.magisk317.mipush.manager.api.ManagerHandshake
import io.github.magisk317.mipush.manager.client.DisconnectReason
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ManagerLocalDiagnosticsTest {
    @Test
    fun `every runtime state is described without touching the binder`() {
        assertEquals("unobserved", ManagerLocalDiagnostics.describe(null))
        assertEquals("binding", ManagerLocalDiagnostics.describe(ManagerRuntimeAvailability.Binding))
        assertEquals("runtime_missing", ManagerLocalDiagnostics.describe(ManagerRuntimeAvailability.RuntimeMissing))
        assertEquals("timed_out", ManagerLocalDiagnostics.describe(ManagerRuntimeAvailability.TimedOut))
        assertEquals(
            "failed reason=bind_refused",
            ManagerLocalDiagnostics.describe(ManagerRuntimeAvailability.Failed("bind_refused")),
        )
        assertEquals(
            "temporarily_disconnected reason=BINDER_DIED",
            ManagerLocalDiagnostics.describe(
                ManagerRuntimeAvailability.TemporarilyDisconnected(DisconnectReason.BINDER_DIED),
            ),
        )

        val available = ManagerLocalDiagnostics.describe(ManagerRuntimeAvailability.Available(handshake()))
        assertTrue(available.startsWith("available"), available)
        assertTrue("1.2.3" in available && "(123)" in available && "2.0" in available, available)

        val incompatible = ManagerLocalDiagnostics.describe(
            ManagerRuntimeAvailability.Incompatible(handshake(), reason = "protocol_too_new"),
        )
        assertTrue(incompatible.startsWith("incompatible reason=protocol_too_new"), incompatible)
    }

    @Test
    fun `probe lines keep root and recovery records only`(@TempDir dir: File) {
        val log = File(dir, "runtime.manager.2026-10-22.jsonl")
        log.writeText(
            """
            {"message":"availability Disconnected -> Binding"}
            {"message":"root_probe role=manager source=request grantState=true available=true code=0"}
            {"message":"availability Binding -> TemporarilyDisconnected"}
            {"message":"xmsf_recovery action=recover stage=boot_receiver userId=0 success=false code=1"}
            """.trimIndent(),
        )

        val lines = ManagerLocalDiagnostics.probeLines(listOf(log))

        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("root_probe"), lines[0])
        assertTrue(lines[1].contains("xmsf_recovery action=recover stage=boot_receiver"), lines[1])
    }

    @Test
    fun `probe lines keep the newest tail across days`(@TempDir dir: File) {
        val older = File(dir, "runtime.manager.2026-10-21.jsonl")
        older.writeText((1..30).joinToString("\n") { """{"message":"root_probe source=refresh seq=$it"}""" })
        val newer = File(dir, "runtime.manager.2026-10-22.jsonl")
        newer.writeText("""{"message":"root_probe source=request seq=last"}""")

        val lines = ManagerLocalDiagnostics.probeLines(listOf(older, newer))

        assertEquals(EXPECTED_PROBE_LINE_CAP, lines.size)
        assertTrue(lines.last().contains("seq=last"), lines.last())
        assertTrue(lines.first().contains("seq=8"), lines.first())
    }

    @Test
    fun `an unreadable log file never fails the whole block`(@TempDir dir: File) {
        val missing = File(dir, "runtime.manager.2026-10-20.jsonl")
        val present = File(dir, "runtime.manager.2026-10-22.jsonl")
        present.writeText("""{"message":"xmsf_recovery action=force_stop userId=0 success=true code=0"}""")

        val lines = ManagerLocalDiagnostics.probeLines(listOf(missing, present))

        assertEquals(1, lines.size)
    }

    private fun handshake() = ManagerHandshake(
        protocolMajor = 2,
        protocolMinor = 0,
        runtimeVersionName = "1.2.3",
        runtimeVersionCode = 123L,
        supportedCapabilities = listOf("log_export"),
        maxPageSize = 50,
        maxPayloadBytes = 1024,
        runtimeCommit = "abc1234",
    )

    private companion object {
        /** Mirrors ManagerLocalDiagnostics.MAX_PROBE_LINES, which stays private on purpose. */
        const val EXPECTED_PROBE_LINE_CAP = 24
    }
}
