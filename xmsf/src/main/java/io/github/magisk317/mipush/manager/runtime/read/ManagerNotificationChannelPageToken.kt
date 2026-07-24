package io.github.magisk317.mipush.manager.runtime.read

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

internal object ManagerNotificationChannelPageToken {
    private const val FORMAT_VERSION = 1
    private const val FINGERPRINT_SIZE = 16
    private const val MAX_CHANNEL_ID_LENGTH = 512

    fun encode(packageName: String, lastChannelId: String): String {
        val packageBytes = packageName.toByteArray(StandardCharsets.UTF_8)
        val channelBytes = lastChannelId.toByteArray(StandardCharsets.UTF_8)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { stream ->
            stream.writeByte(FORMAT_VERSION)
            stream.write(fingerprint(packageName))
            stream.writeShort(channelBytes.size)
            stream.write(channelBytes)
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray())
    }

    fun decode(packageName: String, token: String): String {
        val decoded = runCatching { Base64.getUrlDecoder().decode(token) }
            .getOrElse { throw IllegalArgumentException("Invalid notification channel page token") }
        try {
            DataInputStream(decoded.inputStream()).use { stream ->
                if (stream.readUnsignedByte() != FORMAT_VERSION) {
                    throw IllegalArgumentException("Unsupported notification channel page token version")
                }
                val expected = fingerprint(packageName)
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

    private fun fingerprint(packageName: String): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest(packageName.toByteArray(StandardCharsets.UTF_8))
            .copyOf(FINGERPRINT_SIZE)
}
