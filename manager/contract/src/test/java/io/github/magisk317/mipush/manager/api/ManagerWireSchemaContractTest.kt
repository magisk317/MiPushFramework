package io.github.magisk317.mipush.manager.api

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManagerWireSchemaContractTest {
    @Test
    fun `handshake frame remains append-only and size-delimited`() {
        val handshake = source("ManagerHandshake.kt")
        assertTrue(handshake.contains("destination.writeWireFrame"))
        assertInOrder(
            handshake,
            "writeInt(protocolMajor)",
            "writeInt(protocolMinor)",
            "writeString(runtimeVersionName)",
            "writeLong(runtimeVersionCode)",
            "writeStringList(supportedCapabilities)",
            "writeInt(maxPageSize)",
            "writeInt(maxPayloadBytes)",
            "writeString(compatibilityReason)",
        )
        assertTrue(handshake.contains("source.readWireFrame"))

        val framing = source("WireParcel.kt")
        assertTrue(framing.contains("size > ManagerProtocol.MAX_WIRE_FRAME_BYTES"))
        assertTrue(framing.contains("if (end > dataSize())"))
        assertTrue(framing.contains("finally {\n        setDataPosition(end)"))
    }

    @Test
    fun `configuration upload descriptor remains nullable framed tail field`() {
        val upload = source("ManagerConfigurationUploadRequestDto.kt")
        assertInOrder(
            upload,
            "writeInt(schemaVersion)",
            "writeString(path)",
            "writeInt(contentLength)",
            "writeWireNullableParcelable(parcelFileDescriptor, flags)",
        )
        assertTrue(upload.contains("readNullableParcelable(ParcelFileDescriptor.CREATOR)"))
        assertTrue(upload.contains("override fun describeContents(): Int = parcelFileDescriptor?.describeContents() ?: 0"))
    }

    private fun source(name: String): String =
        File("src/main/java/io/github/magisk317/mipush/manager/api/$name").readText()

    private fun assertInOrder(source: String, vararg fragments: String) {
        var offset = -1
        fragments.forEach { fragment ->
            val next = source.indexOf(fragment, startIndex = offset + 1)
            assertTrue(next >= 0, "Missing wire fragment: $fragment")
            assertTrue(next > offset, "Wire fragment is out of order: $fragment")
            offset = next
        }
    }
}
