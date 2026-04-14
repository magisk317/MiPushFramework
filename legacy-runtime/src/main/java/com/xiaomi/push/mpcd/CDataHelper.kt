package com.xiaomi.push.mpcd

import android.content.Context
import android.os.Build
import com.xiaomi.channel.commonutils.android.DataCryptUtils
import com.xiaomi.channel.commonutils.misc.JobMutualExclusor
import com.xiaomi.channel.commonutils.string.Base64Coder
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.xmpush.thrift.ConfigKey

object CDataHelper {
    @JvmStatic
    fun checkDataCollectionJobMutual(context: Context, tag: String, period: Long): Boolean {
        if (!OnlineConfig.getInstance(context).getBooleanValue(ConfigKey.DCJobMutualSwitch.value, false)) {
            return false
        }
        if (Build.VERSION.SDK_INT >= 29 || context.applicationInfo.targetSdkVersion >= 29) {
            return false
        }
        return !JobMutualExclusor.checkPeriodAndRecordWithFileLock(context, tag, period)
    }

    @JvmStatic
    fun decryptData(str: String, bArr: ByteArray): ByteArray? {
        val decoded = Base64Coder.decode(str)
        return try {
            parseKey(decoded)
            DataCryptUtils.mipushDecrypt(decoded, bArr)
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun encryptData(str: String, bArr: ByteArray): ByteArray? {
        val decoded = Base64Coder.decode(str)
        return try {
            parseKey(decoded)
            DataCryptUtils.mipushEncrypt(decoded, bArr)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseKey(bArr: ByteArray) {
        if (bArr.size >= 2) {
            bArr[0] = 99
            bArr[1] = 100
        }
    }
}
