package com.xiaomi.push.mpcd

import android.content.Context
import android.os.Build
import com.xiaomi.channel.commonutils.android.DataCryptUtils
import com.xiaomi.channel.commonutils.misc.JobMutualExclusor
import com.xiaomi.channel.commonutils.string.Base64Coder
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.xmpush.thrift.ConfigKey

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/u9/c.java
 * Current override same-path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/push/mpcd/CDataHelper.java
 * Stock class name is obfuscated as u9.c; this file keeps the deobfuscated CDataHelper API.
 */
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
