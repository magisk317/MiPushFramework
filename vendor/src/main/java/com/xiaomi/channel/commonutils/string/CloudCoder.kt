package com.xiaomi.channel.commonutils.string

import android.net.Uri
import android.text.TextUtils
import android.util.Log
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.InvalidParameterException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import javax.crypto.Cipher
import javax.crypto.NoSuchPaddingException
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CloudCoder {
    private const val RC4_ALGORITHM_NAME = "RC4"
    private const val TAG = "CloudCoder"

    @JvmStatic
    fun generateSignature(
        method: String?,
        url: String?,
        params: Map<String, String>?,
        security: String
    ): String {
        if (TextUtils.isEmpty(security)) {
            throw InvalidParameterException("security is not nullable")
        }
        val arrayList = ArrayList<String>()
        if (method != null) {
            arrayList.add(method.uppercase())
        }
        if (url != null) {
            arrayList.add(Uri.parse(url).encodedPath ?: "")
        }
        if (params != null && params.isNotEmpty()) {
            for (entry in TreeMap(params).entries) {
                arrayList.add(String.format("%s=%s", entry.key, entry.value))
            }
        }
        arrayList.add(security)
        var z = true
        val sb = StringBuilder()
        for (str4 in arrayList) {
            if (!z) {
                sb.append('&')
            }
            sb.append(str4)
            z = false
        }
        return hash4SHA1(sb.toString())
    }

    @JvmStatic
    fun hash4SHA1(str: String): String {
        return try {
            String(
                Base64Coder.encode(
                    MessageDigest.getInstance("SHA1").digest(str.toByteArray(charset("UTF-8")))
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "CloudCoder.hash4SHA1", e)
            throw IllegalStateException("failed to SHA1")
        }
    }

    @JvmStatic
    fun newAESCipher(key: String, mode: Int): Cipher? {
        val secretKeySpec = SecretKeySpec(Base64Coder.decode(key), "AES")
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(mode, secretKeySpec, IvParameterSpec("0102030405060708".toByteArray()))
            cipher
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @JvmStatic
    fun newRC4Cipher(key: ByteArray, mode: Int): Cipher? {
        val secretKeySpec = SecretKeySpec(key, RC4_ALGORITHM_NAME)
        return try {
            val cipher = Cipher.getInstance(RC4_ALGORITHM_NAME)
            cipher.init(mode, secretKeySpec)
            cipher
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
