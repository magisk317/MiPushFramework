package com.xiaomi.mipush.sdk

import android.content.Context
import android.text.TextUtils
import androidx.core.os.BundleCompat
import com.xiaomi.channel.commonutils.android.PermissionUtils
import com.xiaomi.channel.commonutils.logger.LoggerInterface
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.log.MIPushDebugLog
import com.xiaomi.push.log.MIPushLog2File
import com.google.protobuf.micro.CodedOutputStreamMicro
import java.io.File
import java.util.HashMap

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/Logger.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
object Logger {
    private var sDisablePushLog = false
    private var sUserLogger: LoggerInterface? = null

    @JvmStatic
    fun disablePushFileLog(context: Context) {
        sDisablePushLog = true
        setPushLog(context)
    }

    @JvmStatic
    fun enablePushFileLog(context: Context) {
        sDisablePushLog = false
        setPushLog(context)
    }

    @JvmStatic
    @Deprecated("")
    fun getLogFile(p0: String?): File? = null

    @JvmStatic
    fun getUserLogger(): LoggerInterface? = sUserLogger

    private fun hasWritePermission(context: Context): Boolean {
        return try {
            val requestedPermissions = context.packageManager.getPackageInfo(
                context.packageName,
                CodedOutputStreamMicro.DEFAULT_BUFFER_SIZE
            ).requestedPermissions
            if (requestedPermissions == null) {
                false
            } else {
                requestedPermissions.any { it == PermissionUtils.writeExternalStorage }
            }
        } catch (e: Exception) {
            false
        }
    }

    @JvmStatic
    fun setLogger(context: Context, loggerInterface: LoggerInterface?) {
        sUserLogger = loggerInterface
        setPushLog(context)
    }

    @JvmStatic
    fun setPushLog(context: Context) {
        val hasUserLogger = sUserLogger != null
        val hasWritePermission = if (sDisablePushLog) false else hasWritePermission(context)

        val userLogger = if (sDisablePushLog) null else if (hasUserLogger) sUserLogger else null
        val logFile = if (hasWritePermission) MIPushLog2File(context) else null

        MyLog.setLogger(MIPushDebugLog(userLogger, logFile))
    }

    @JvmStatic
    @Deprecated("")
    fun uploadLogFile(context: Context, z: Boolean) {
    }
}
