package io.github.magisk317.mipush.manager.runtime.read

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

/**
 * A versioned keyset cursor. The package name is encrypted only by opacity (not secrecy): callers
 * must treat the value as an uninspectable token and may use it only with the same query.
 */
internal object ManagerApplicationPageToken {
    private const val FORMAT_VERSION = 1
    private const val FINGERPRINT_SIZE = 16
    private const val MAX_PACKAGE_NAME_LENGTH = 255

    fun encode(query: ManagerApplicationReadQuery, lastPackageName: String): String {
        require(isValidPackageName(lastPackageName)) { "Invalid application page cursor package" }
        val packageBytes = lastPackageName.toByteArray(StandardCharsets.UTF_8)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { stream ->
            stream.writeByte(FORMAT_VERSION)
            stream.write(fingerprint(query))
            stream.writeShort(packageBytes.size)
            stream.write(packageBytes)
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray())
    }

    fun decode(query: ManagerApplicationReadQuery, token: String): String {
        val decoded = runCatching { Base64.getUrlDecoder().decode(token) }
            .getOrElse { throw IllegalArgumentException("Invalid application page token") }
        try {
            DataInputStream(decoded.inputStream()).use { stream ->
                if (stream.readUnsignedByte() != FORMAT_VERSION) {
                    throw IllegalArgumentException("Unsupported application page token version")
                }
                val expected = fingerprint(query)
                val actual = ByteArray(FINGERPRINT_SIZE).also(stream::readFully)
                if (!MessageDigest.isEqual(expected, actual)) {
                    throw IllegalArgumentException("Application page token does not match query")
                }
                val length = stream.readUnsignedShort()
                if (length == 0 || length > MAX_PACKAGE_NAME_LENGTH || length != stream.available()) {
                    throw IllegalArgumentException("Invalid application page cursor")
                }
                val packageName = ByteArray(length).also(stream::readFully)
                    .toString(StandardCharsets.UTF_8)
                if (!isValidPackageName(packageName)) {
                    throw IllegalArgumentException("Invalid application page cursor package")
                }
                return packageName
            }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid application page token", error)
        }
    }

    private fun fingerprint(query: ManagerApplicationReadQuery): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { stream ->
            stream.writeInt(query.schemaVersion)
            stream.writeUTF(query.query)
            stream.writeInt(query.filterMode)
            stream.writeBoolean(query.includeSystemApps)
            stream.writeInt(query.pageSize)
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes.toByteArray())
            .copyOf(FINGERPRINT_SIZE)
    }

    private fun isValidPackageName(packageName: String): Boolean =
        packageName.length in 1..MAX_PACKAGE_NAME_LENGTH &&
            packageName.all { it.isLetterOrDigit() || it == '.' || it == '_' }
}
