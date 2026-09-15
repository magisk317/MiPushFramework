package com.xiaomi.channel.commonutils.android

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/*
 */
object DataCryptUtils {
    private val DEFAULT_IV = byteArrayOf(100, 23, 84, 114, 72, 0, 4, 97, 73, 97, 2, 52, 84, 102, 18, 32)

    private fun createCipher(key: ByteArray, mode: Int): Cipher {
        val secretKeySpec = SecretKeySpec(key, "AES")
        val ivParameterSpec = IvParameterSpec(DEFAULT_IV)
        return Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(mode, secretKeySpec, ivParameterSpec)
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun mipushDecrypt(key: ByteArray, payload: ByteArray): ByteArray {
        return createCipher(key, Cipher.DECRYPT_MODE).doFinal(payload)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun mipushEncrypt(key: ByteArray, payload: ByteArray): ByteArray {
        return createCipher(key, Cipher.ENCRYPT_MODE).doFinal(payload)
    }
}
