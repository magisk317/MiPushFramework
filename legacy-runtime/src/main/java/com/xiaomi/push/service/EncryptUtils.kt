package com.xiaomi.push.service

import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.string.Base64Coder
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class EncryptUtils private constructor() {
    companion object {
        private val iv = byteArrayOf(100, 23, 84, 114, 72, 0, 4, 97, 73, 97, 2, 52, 84, 102, 18, 32)

        @JvmStatic
        fun decrypt(secret: String, encrypted: String): String? {
            return try {
                val secretKey = SecretKeySpec(Base64Coder.decode(secret), "AES")
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
                String(cipher.doFinal(Base64Coder.decode(encrypted)), StandardCharsets.UTF_8)
            } catch (e: Exception) {
                MyLog.e(e)
                null
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun encrypt(secret: String, raw: String): String {
            val secretKey = SecretKeySpec(Base64Coder.decode(secret), "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
            return String(Base64Coder.encode(cipher.doFinal(raw.toByteArray(StandardCharsets.UTF_8))))
        }
    }
}
