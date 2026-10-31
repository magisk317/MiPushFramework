package io.github.magisk317.mipush.manager.xmsfdelivery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XmsfVersionContractTest {

    @Test
    fun `well-formed manifest passes all gates`() {
        val manifest = manifest()

        assertNull(manifest.validate(hostVersionCode = HOST_VERSION_CODE))
        assertTrue(manifest.matchesPin(PINNED_VERSION))
    }

    @Test
    fun `schema drift is rejected`() {
        val manifest = manifest().copy(schema = XmsfRuntimeManifest.SCHEMA + 1)

        assertEquals(
            "manifest schema ${XmsfRuntimeManifest.SCHEMA + 1} does not match host contract ${XmsfRuntimeManifest.SCHEMA}",
            manifest.validate(HOST_VERSION_CODE),
        )
    }

    @Test
    fun `foreign variant is rejected`() {
        val manifest = manifest().copy(variant = "play")

        assertTrue(manifest.validate(HOST_VERSION_CODE)!!.contains("variant"))
    }

    @Test
    fun `blank version name is rejected`() {
        val manifest = manifest().copy(versionName = "  ")

        assertEquals("manifest versionName is blank", manifest.validate(HOST_VERSION_CODE))
    }

    @Test
    fun `non-positive version code is rejected`() {
        val manifest = manifest().copy(versionCode = 0L)

        assertEquals("manifest versionCode is not positive", manifest.validate(HOST_VERSION_CODE))
    }

    @Test
    fun `runtime newer than host manager is rejected`() {
        val manifest = manifest().copy(minManagerVersionCode = HOST_VERSION_CODE + 1)

        assertEquals(
            "runtime requires manager versionCode >= ${HOST_VERSION_CODE + 1}, host is $HOST_VERSION_CODE",
            manifest.validate(HOST_VERSION_CODE),
        )
    }

    @Test
    fun `payload of another variant is rejected`() {
        // Both variants of a release share one versionName, so the version code
        // is the only field that can tell them apart -- and swapping them moves
        // the version code apps compare against.
        val vc105Payload = manifest().copy(versionCode = XmsfRuntimeFlavor.Vc105.versionCode)

        assertEquals(
            "manifest versionCode ${XmsfRuntimeFlavor.Vc105.versionCode} is not the normal variant " +
                "(${XmsfRuntimeFlavor.Normal.versionCode})",
            vc105Payload.validate(HOST_VERSION_CODE, XmsfRuntimeFlavor.Normal),
        )
        assertNull(vc105Payload.validate(HOST_VERSION_CODE, XmsfRuntimeFlavor.Vc105))
        assertNull(manifest().validate(HOST_VERSION_CODE, XmsfRuntimeFlavor.Normal))
    }

    @Test
    fun `declared commit is carried and gated nowhere`() {
        // Diagnostic only: an empty commit stays valid (older publishers) and a
        // foreign one is still installable -- it only shows up in the UI note.
        val declared = manifest().copy(commit = "7865a560")

        assertNull(declared.validate(HOST_VERSION_CODE))
        assertNull(manifest().copy(commit = "").validate(HOST_VERSION_CODE))
        assertEquals(
            "7865a560",
            XmsfRuntimeManifest.parse(
                """
                {
                  "schema": ${XmsfRuntimeManifest.SCHEMA},
                  "variant": "${XmsfRuntimeManifest.VARIANT}",
                  "versionName": "$PINNED_VERSION",
                  "versionCode": 1003003000,
                  "minManagerVersionCode": 0,
                  "sha256": "abc",
                  "commit": "7865a560"
                }
                """.trimIndent(),
            ).commit,
        )
    }

    @Test
    fun `pin mismatch is detected independently of gates`() {
        val manifest = manifest()

        assertNull(manifest.validate(HOST_VERSION_CODE))
        assertFalse(manifest.matchesPin("9.9.9"))
    }

    @Test
    fun `unknown fields are ignored when parsing`() {
        val json = """
            {
              "schema": 1,
              "variant": "github",
              "versionName": "1.0.9",
              "versionCode": 1003003000,
              "minManagerVersionCode": 1003003000,
              "sha256": "abc",
              "publishedAt": "2026-10-16T00:00:00Z",
              "notes": {"channel": "beta"}
            }
        """.trimIndent()

        val manifest = XmsfRuntimeManifest.parse(json)

        assertEquals(PINNED_VERSION, manifest.versionName)
        assertEquals("abc", manifest.sha256)
        assertNull(manifest.validate(HOST_VERSION_CODE))
    }

    @Test
    fun `malformed json fails loudly`() {
        val error = runCatching { XmsfRuntimeManifest.parse("{not json") }.exceptionOrNull()

        assertTrue(error != null, "malformed manifest must not parse silently")
    }

    private fun manifest() = XmsfRuntimeManifest(
        schema = XmsfRuntimeManifest.SCHEMA,
        variant = XmsfRuntimeManifest.VARIANT,
        versionName = PINNED_VERSION,
        versionCode = 1003003000L,
        minManagerVersionCode = 1003002000L,
        sha256 = "0".repeat(64),
    )

    private companion object {
        const val PINNED_VERSION = "1.0.9"
        const val HOST_VERSION_CODE = 1003003000L
    }
}
