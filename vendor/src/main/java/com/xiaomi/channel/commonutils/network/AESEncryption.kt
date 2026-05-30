package com.xiaomi.channel.commonutils.network

import com.xiaomi.channel.commonutils.string.UrlBase64Coder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/channel/commonutils/network/AESEncryption.java
 */
object AESEncryption {
    private const val HEX_PREFIX = "0"

    class AESDecodeException : Exception {
        constructor(str: String?) : super(str)
        constructor(str: String?, exc: Exception?) : super(str, exc)

        companion object {
            private const val serialVersionUID = 8671822568355001199L
        }
    }

    class AESEncodeException : Exception {
        constructor(str: String?) : super(str)
        constructor(str: String?, exc: Exception?) : super(str, exc)

        companion object {
            private const val serialVersionUID = 4826692804389845727L
        }
    }

    @JvmStatic
    fun byte2hex(bArr: ByteArray): String {
        val sb = StringBuilder()
        for (b in bArr) {
            val hexString = Integer.toHexString(b.toInt() and 255)
            if (hexString.length == 1) {
                sb.append(HEX_PREFIX)
            }
            sb.append(hexString)
        }
        return sb.toString().uppercase()
    }

    @JvmStatic
    @Throws(AESDecodeException::class)
    fun decrypt(data: ByteArray, key: ByteArray?): ByteArray? {
        return try {
            if (key == null) {
                throw AESEncodeException("Key为空null")
            }
            if (key.size != 16) {
                throw AESEncodeException("Key长度不是16位")
            }
            val secretKeySpec = SecretKeySpec(key, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val messageDigest = MessageDigest.getInstance("MD5")
            messageDigest.update(key, 0, key.size)
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, IvParameterSpec(messageDigest.digest()))
            cipher.doFinal(data)
        } catch (e: Exception) {
            throw AESDecodeException("AES加密错误", e)
        }
    }

    @JvmStatic
    @Throws(AESEncodeException::class)
    fun encrypt(str: String, key: ByteArray?): ByteArray? {
        return try {
            if (key == null) {
                throw AESEncodeException("Key为空null")
            }
            if (key.size != 16) {
                throw AESEncodeException("Key长度不是16位")
            }
            val secretKeySpec = SecretKeySpec(key, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val messageDigest = MessageDigest.getInstance("MD5")
            messageDigest.update(key, 0, key.size)
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, IvParameterSpec(messageDigest.digest()))
            cipher.doFinal(str.toByteArray())
        } catch (e: Exception) {
            throw AESEncodeException("AES加密错误", e)
        }
    }

    @JvmStatic
    fun hex2byte(str: String?): ByteArray? {
        if (str == null) {
            return null
        }
        val length = str.length
        if (length % 2 == 1) {
            return null
        }
        val bytes = ByteArray(length / 2)
        for (i in 0 until length / 2) {
            bytes[i] = Integer.parseInt(str.substring(i * 2, (i * 2) + 2), 16).toByte()
        }
        return bytes
    }

    @JvmStatic
    @Throws(AESDecodeException::class, java.io.UnsupportedEncodingException::class)
    fun hexDecrypt(str: String, str2: String): String {
        return String(decrypt(UrlBase64Coder.decode(str), hex2byte(str2))!!)
    }

    @JvmStatic
    @Throws(AESEncodeException::class, java.io.UnsupportedEncodingException::class)
    fun hexEncrypt(str: String, str2: String): String {
        return String(UrlBase64Coder.encode(encrypt(str, hex2byte(str2))!!))
    }

    @JvmStatic
    @Throws(AESDecodeException::class, java.io.UnsupportedEncodingException::class)
    fun test() {
    }
}
