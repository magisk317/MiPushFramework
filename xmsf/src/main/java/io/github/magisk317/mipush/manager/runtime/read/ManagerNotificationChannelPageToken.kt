package io.github.magisk317.mipush.manager.runtime.read

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

internal object ManagerNotificationChannelPageToken {
    private const val FORMAT_VERSION = 2
    private const val FINGERPRINT_SIZE = 16
    private const val MAX_CHANNEL_ID_LENGTH = 512

    fun encode(packageName: String, lastChannelId: String, userId: Int): String {
        require(userId >= 0) { "Invalid notification channel cursor user" }
        val channelBytes = lastChannelId.toByteArray(StandardCharsets.UTF_8)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { stream ->
            stream.writeByte(FORMAT_VERSION)
            stream.write(fingerprint(packageName, userId))
            stream.writeShort(channelBytes.size)
            stream.write(channelBytes)
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray())
    }

    fun decode(packageName: String, token: String, userId: Int): String {
        require(userId >= 0) { "Invalid notification channel cursor user" }
        val decoded = runCatching { Base64.getUrlDecoder().decode(token) }
            .getOrElse { throw IllegalArgumentException("Invalid notification channel page token") }
        try {
            DataInputStream(decoded.inputStream()).use { stream ->
                if (stream.readUnsignedByte() != FORMAT_VERSION) {
                    throw IllegalArgumentException("Unsupported notification channel page token version")
                }
                val expected = fingerprint(packageName, userId)
                val actual = ByteArray(FINGERPRINT_SIZE).also(stream::readFully)
                if (!MessageDigest.isEqual(expected, actual)) {
                    throw IllegalArgumentException("Notification channel page token does not match package")
                }
                val length = stream.readUnsignedShort()
                if (length == 0 || length > MAX_CHANNEL_ID_LENGTH || length != stream.available()) {
                    throw IllegalArgumentException("Invalid notification channel page cursor")
                }
                return ByteArray(length).also(stream::readFully).toString(StandardCharsets.UTF_8)
            }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: IOException) {
            throw IllegalArgumentException("Invalid notification channel page token", error)
        }
    }

    private fun fingerprint(packageName: String, userId: Int): ByteArray =
        ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { it.writeInt(userId) }
            output.write(packageName.toByteArray(StandardCharsets.UTF_8))
        }.let { bytes ->
            MessageDigest.getInstance("SHA-256")
                .digest(bytes.toByteArray())
                .copyOf(FINGERPRINT_SIZE)
        }
}
