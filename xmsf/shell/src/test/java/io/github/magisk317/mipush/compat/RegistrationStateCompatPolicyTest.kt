package io.github.magisk317.mipush.compat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegistrationStateCompatPolicyTest {
    @Test
    fun `registration paths are isolated to supplied Android user`() {
        val primaryPaths = RegistrationStateCompat.registrationArtifactPathsForUser("com.example.app", 0)
        val clonePaths = RegistrationStateCompat.registrationArtifactPathsForUser("com.example.app", 999)

        assertEquals(4, primaryPaths.size)
        assertTrue(primaryPaths.all { "/0/com.example.app/" in it })
        assertFalse(primaryPaths.any { "/999/" in it })
        assertEquals(4, clonePaths.size)
        assertTrue(clonePaths.all { "/999/com.example.app/" in it })
        assertFalse(clonePaths.any { "/0/" in it })
    }

    @Test
    fun `batch probe uses supplied user and valid shell quoting`() {
        val script = RegistrationStateCompat.buildBatchProbeScript(listOf("com.example.app"), 999)

        assertTrue("/data/user/999" in script)
        assertTrue("/data_mirror/data_ce/null/999" in script)
        assertFalse("/data/user/0" in script)
        assertTrue("[ -f \"${'$'}f_xml\" ]" in script)
        assertFalse("\\\"${'$'}f_xml\\\"" in script)
        assertFalse("grep -aq 'regSec'" in script)
    }

    @Test
    fun `batch probe keeps later chunks instead of applying a global cutoff`() {
        val packages = (1..61).map { "com.example.app$it" }
        val chunks = RegistrationStateCompat.batchProbeChunks(packages)

        assertEquals(2, chunks.size)
        assertEquals(60, chunks.first().size)
        assertEquals(listOf("com.example.app61"), chunks.last())
        assertEquals(packages, chunks.flatten())
    }

    @Test
    fun `registration XML yields any plausible regSec candidate`() {
        val complete = registrationXml(valid = true, regId = "reg-id", regSec = "YWJjZGVmZ2g=")

        assertEquals("YWJjZGVmZ2g=", RegistrationStateCompat.extractRegSecFromRegistrationXml(complete))
        assertEquals(
            "YWJjZGVmZ2g=",
            RegistrationStateCompat.extractRegSecFromRegistrationXml(
                registrationXml(valid = false, regId = null, regSec = "YWJjZGVmZ2g="),
            ),
        )
        assertNull(RegistrationStateCompat.extractRegSecFromRegistrationXml(registrationXml(regSec = null)))
    }

    @Test
    fun `invalid package names and user ids produce no probe targets`() {
        listOf("", ".", "..", "com.example/escape", "com.example app", "singleSegment").forEach { packageName ->
            assertTrue(RegistrationStateCompat.registrationArtifactPathsForUser(packageName, 999).isEmpty())
            assertTrue(RegistrationStateCompat.buildBatchProbeScript(listOf(packageName), 999).isEmpty())
        }
        assertTrue(RegistrationStateCompat.registrationArtifactPathsForUser("com.example.app", -1).isEmpty())
        assertTrue(RegistrationStateCompat.buildBatchProbeScript(listOf("com.example.app"), -1).isEmpty())
    }

    private fun registrationXml(
        valid: Boolean = true,
        regId: String? = "reg-id",
        regSec: String? = "YWJjZGVmZ2g=",
    ): String = buildString {
        append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>")
        append("<map>")
        append("<boolean name=\"valid\" value=\"$valid\" />")
        regId?.let { append("<string name=\"regId\">$it</string>") }
        regSec?.let { append("<string name=\"regSec\">$it</string>") }
        append("</map>")
    }
}
