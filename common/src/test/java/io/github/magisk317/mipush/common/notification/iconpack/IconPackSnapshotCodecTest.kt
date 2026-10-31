package io.github.magisk317.mipush.common.notification.iconpack

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IconPackSnapshotCodecTest {

    @Test
    fun `round trips every entry and its bytes`() {
        val snapshot = IconPackSnapshot(
            releaseStamp = 1_700_000_000_000L,
            generatedAtMs = 1_700_000_000_500L,
            entries = listOf(
                IconPackSnapshotEntry("com.example.app", byteArrayOf(1, 2, 3, 4)),
                IconPackSnapshotEntry("com.other\u00e9app", byteArrayOf()),
            ),
        )
        val output = ByteArrayOutputStream()
        assertEquals(2, IconPackSnapshotCodec.write(snapshot, output))

        val decoded = IconPackSnapshotCodec.read(ByteArrayInputStream(output.toByteArray()))!!
        assertEquals(snapshot.releaseStamp, decoded.releaseStamp)
        assertEquals(snapshot.generatedAtMs, decoded.generatedAtMs)
        val index = decoded.indexOfPackage()
        assertEquals(listOf("com.example.app", "com.other\u00e9app"), index.keys.toList())
        assertTrue(index.getValue("com.example.app").contentEquals(byteArrayOf(1, 2, 3, 4)))
        assertTrue(index.getValue("com.other\u00e9app").isEmpty())
    }

    @Test
    fun `reads the release stamp without decoding entries`() {
        val output = ByteArrayOutputStream()
        IconPackSnapshotCodec.write(
            IconPackSnapshot(42L, 1L, listOf(IconPackSnapshotEntry("a", byteArrayOf(9, 9)))),
            output,
        )
        assertEquals(42L, IconPackSnapshotCodec.readReleaseStamp(ByteArrayInputStream(output.toByteArray())))
    }

    @Test
    fun `rejects a truncated or foreign payload`() {
        val output = ByteArrayOutputStream()
        IconPackSnapshotCodec.write(
            IconPackSnapshot(7L, 1L, listOf(IconPackSnapshotEntry("a", byteArrayOf(1, 2, 3, 4, 5)))),
            output,
        )
        val bytes = output.toByteArray()
        assertNull(IconPackSnapshotCodec.read(ByteArrayInputStream(bytes.copyOf(bytes.size - 3))))
        val foreign = bytes.copyOf().also { it[0] = 0 }
        assertNull(IconPackSnapshotCodec.read(ByteArrayInputStream(foreign)))
        assertNull(IconPackSnapshotCodec.readReleaseStamp(ByteArrayInputStream(foreign)))
    }
}
