package com.xiaomi.channel.commonutils.android

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/channel/commonutils/android/DataCryptUtils.java
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
