package com.xiaomi.mipush.sdk;

import android.content.Context;
import com.google.protobuf.micro.CodedOutputStreamMicro;
import com.xiaomi.channel.commonutils.android.PermissionUtils;
import com.xiaomi.channel.commonutils.logger.LoggerInterface;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.log.MIPushDebugLog;
import com.xiaomi.push.log.MIPushLog2File;
import java.io.File;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/Logger.class */
public class Logger {
    private static boolean sDisablePushLog = false;
    private static LoggerInterface sUserLogger = null;

    public static void disablePushFileLog(Context context) {
        sDisablePushLog = true;
        setPushLog(context);
    }

    public static void enablePushFileLog(Context context) {
        sDisablePushLog = false;
        setPushLog(context);
    }

    @Deprecated
    public static File getLogFile(String str) {
        return null;
    }

    protected static LoggerInterface getUserLogger() {
        return sUserLogger;
    }

    private static boolean hasWritePermission(Context context) {
        try {
            String[] strArr = context.getPackageManager().getPackageInfo(context.getPackageName(), CodedOutputStreamMicro.DEFAULT_BUFFER_SIZE).requestedPermissions;
            if (strArr == null) {
                return false;
            }
            for (String str : strArr) {
                if (PermissionUtils.writeExternalStorage.equals(str)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static void setLogger(Context context, LoggerInterface loggerInterface) {
        sUserLogger = loggerInterface;
        setPushLog(context);
    }

    public static void setPushLog(Context context) {
        boolean z;
        boolean z2 = sUserLogger != null;
        boolean z3 = false;
        if (sDisablePushLog) {
            z = false;
        } else {
            z = z2;
            if (hasWritePermission(context)) {
                z3 = true;
                z = z2;
            }
        }
        MIPushLog2File mIPushLog2File = null;
        LoggerInterface loggerInterface = z ? sUserLogger : null;
        if (z3) {
            mIPushLog2File = new MIPushLog2File(context);
        }
        MyLog.setLogger(new MIPushDebugLog(loggerInterface, mIPushLog2File));
    }

    @Deprecated
    public static void uploadLogFile(Context context, boolean z) {
    }
}
