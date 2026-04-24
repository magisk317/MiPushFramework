package com.xiaomi.push.service

import com.xiaomi.channel.commonutils.string.Base64Coder
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RC4CryptionTest {
    @Test
    fun `byte hex conversion round trips`() {
        val source = byteArrayOf(0x00, 0x12, 0x7f, 0x80.toByte(), 0xff.toByte())

        val hex = RC4Cryption.byte2string(source)

        assertEquals("00127f80ff", hex)
        assertArrayEquals(source, RC4Cryption.string2byte(hex))
    }

    @Test
    fun `buildkey fills target length with both inputs`() {
        val built = RC4Cryption.buildkey(
            byteArrayOf(1, 2, 3),
            byteArrayOf(4, 5, 6, 7, 8),
        )

        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), built)
    }

    @Test
    fun `encrypt and decrypt round trip through string helper`() {
        val key = "challenge-key".toByteArray()
        val payload = "hello mipush"

        val encoded = RC4Cryption.encrypt(key, payload)
        val decoded = RC4Cryption.decrypt(key, encoded)

        assertEquals(payload, decoded.decodeToString())
        assertEquals(
            encoded,
            Base64Coder.encode(RC4Cryption.encrypt(key, payload.toByteArray())).concatToString(),
        )
    }

    @Test
    fun `partial encrypt supports copy and in place modes`() {
        val key = "key".toByteArray()
        val payload = byteArrayOf(1, 2, 3, 4, 5)

        val copied = RC4Cryption.encrypt(key, payload, false, 1, 3)
        val inPlace = payload.copyOf()
        val mutated = RC4Cryption.encrypt(key, inPlace, true, 1, 3)

        assertArrayEquals(copied, mutated.copyOfRange(1, 4))
        assertEquals(1, mutated[0].toInt())
        assertEquals(5, mutated[4].toInt())
    }

    @Test
    fun `partial encrypt rejects invalid ranges`() {
        val key = "key".toByteArray()
        val payload = byteArrayOf(1, 2, 3)

        assertThrows(IllegalArgumentException::class.java) {
            RC4Cryption.encrypt(key, payload, false, -1, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RC4Cryption.encrypt(key, payload, false, 2, 2)
        }
    }

    @Test
    fun `generateKeyForRC4 inserts underscore separator`() {
        val generated = RC4Cryption.generateKeyForRC4("YWJj", "packet-1")

        assertEquals("abc_packet-1", generated.decodeToString())
    }
}
