package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.string.Base64Coder

class RC4Cryption() {
    private var state: ByteArray = ByteArray(256)
    private var nextJ: Int = UNINITIALIZED_NEXT_J
    private var theJ: Int = 0
    private var theI: Int = 0

    constructor(state: ByteArray) : this() {
        this.state = state
    }

    companion object {
        private const val KEY_LENGTH = 8
        private const val HEX_DIGITS = "0123456789abcdef"
        private const val UNINITIALIZED_NEXT_J = -666

        @JvmStatic
        fun buildkey(first: ByteArray, second: ByteArray): ByteArray {
            val combined = ByteArray(KEY_LENGTH)
            for (index in first.indices) {
                combined[index] = first[index]
            }
            for (index in first.size until KEY_LENGTH) {
                combined[index] = second[index - first.size]
            }
            return combined
        }

        @JvmStatic
        fun byte2string(value: Byte): String {
            return buildString(2) {
                append(HEX_DIGITS[(value.toInt() shr 4) and 15])
                append(HEX_DIGITS[value.toInt() and 15])
            }
        }

        @JvmStatic
        fun byte2string(values: ByteArray): String {
            return buildString(values.size * 2) {
                values.forEach { append(byte2string(it)) }
            }
        }

        @JvmStatic
        fun decrypt(key: ByteArray, payload: String): ByteArray {
            return encrypt(key, Base64Coder.decode(payload))
        }

        @JvmStatic
        fun encrypt(key: ByteArray, payload: String): String {
            return Base64Coder.encode(encrypt(key, payload.toByteArray())).concatToString()
        }

        @JvmStatic
        fun encrypt(key: ByteArray, payload: ByteArray): ByteArray {
            val encrypted = ByteArray(payload.size)
            val rc4 = RC4Cryption()
            rc4.ksa(key)
            rc4.init()
            for (index in payload.indices) {
                encrypted[index] = (payload[index].toInt() xor rc4.nextVal().toInt()).toByte()
            }
            return encrypted
        }

        @JvmStatic
        fun encrypt(
            key: ByteArray,
            payload: ByteArray,
            inPlace: Boolean,
            start: Int,
            length: Int,
        ): ByteArray {
            if (start < 0 || start > payload.size || start + length > payload.size) {
                throw IllegalArgumentException("start = $start len = $length")
            }
            val target = if (inPlace) payload else ByteArray(length)
            val targetStart = if (inPlace) start else 0
            val rc4 = RC4Cryption()
            rc4.ksa(key)
            rc4.init()
            repeat(length) { offset ->
                target[targetStart + offset] =
                    (payload[start + offset].toInt() xor rc4.nextVal().toInt()).toByte()
            }
            return target
        }

        @JvmStatic
        fun generateKeyForRC4(base64Secret: String, packetId: String): ByteArray {
            val decoded = Base64Coder.decode(base64Secret)
            val packetBytes = packetId.toByteArray()
            val combined = ByteArray(decoded.size + 1 + packetBytes.size)
            decoded.copyInto(combined, endIndex = decoded.size)
            combined[decoded.size] = '_'.code.toByte()
            packetBytes.copyInto(combined, destinationOffset = decoded.size + 1)
            return combined
        }

        @JvmStatic
        fun posify(value: Byte): Int = if (value >= 0) value.toInt() else value + 256

        @JvmStatic
        fun string2byte(value: String): ByteArray {
            val bytes = ByteArray(value.length / 2)
            for (index in bytes.indices) {
                bytes[index] = ((hexval(value[index * 2]) * 16) + hexval(value[index * 2 + 1])).toByte()
            }
            return bytes
        }

        private fun hexval(value: Char): Int {
            return when (value) {
                in '0'..'9' -> value - '0'
                in 'a'..'f' -> value - 'a' + 10
                in 'A'..'F' -> value - 'A' + 10
                else -> 0
            }
        }

        private fun sswap(values: ByteArray, first: Int, second: Int) {
            val original = values[first]
            values[first] = values[second]
            values[second] = original
        }
    }

    fun S(index: Int): Int = posify(state[index])

    fun inverse(value: Byte): Byte {
        for (index in 0 until 256) {
            if (value == state[index]) {
                return index.toByte()
            }
        }
        return 0
    }

    fun nextVal(): Byte {
        val nextIndex = (theI + 1) % 256
        theI = nextIndex
        val nextSwapIndex = (theJ + posify(state[nextIndex])) % 256
        theJ = nextSwapIndex
        sswap(state, theI, nextSwapIndex)
        return state[(posify(state[theI]) + posify(state[theJ])) % 256]
    }

    fun next_j(): Int = nextJ

    fun the_i(): Int = theI

    fun the_j(): Int = theJ

    private fun init() {
        theJ = 0
        theI = 0
    }

    private fun ksa(key: ByteArray) {
        ksa(256, key, false)
    }

    private fun ksa(limit: Int, key: ByteArray, logState: Boolean) {
        val length = key.size
        for (index in 0 until 256) {
            state[index] = index.toByte()
        }
        var cursor = 0
        theJ = 0
        while (true) {
            theI = cursor
            val currentIndex = theI
            if (currentIndex >= limit) break
            val next = (theJ + posify(state[currentIndex]) + posify(key[theI % length])) % 256
            theJ = next
            sswap(state, theI, next)
            cursor = theI + 1
        }
        if (limit != 256) {
            nextJ = (theJ + posify(state[limit]) + posify(key[limit % length])) % 256
        }
        if (logState) {
            val message = buildString {
                append("S_${limit - 1}:")
                for (index in 0..limit) {
                    append(" ")
                    append(posify(state[index]))
                }
                append("   j_${limit - 1}=")
                append(theJ)
                append("   j_$limit=")
                append(nextJ)
                append("   S_${limit - 1}[j_${limit - 1}]=")
                append(posify(state[theJ]))
                append("   S_${limit - 1}[j_$limit]=")
                append(posify(state[nextJ]))
                if (state[1].toInt() != 0) {
                    append("   S[1]!=0")
                }
            }
            MyLog.w(message)
        }
    }
}
