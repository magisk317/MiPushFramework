package com.xiaomi.channel.commonutils.file;

import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;
import android.util.Log;
import com.xiaomi.channel.commonutils.logger.MyLog;
import java.io.File;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/file/SDCardUtils.class */
public class SDCardUtils {
    public static long getSDCardAvailableBytes() {
        File externalStorageDirectory;
        if (isSDCardBusy() || (externalStorageDirectory = Environment.getExternalStorageDirectory()) == null || TextUtils.isEmpty(externalStorageDirectory.getPath())) {
            return 0L;
        }
        try {
            StatFs statFs = new StatFs(externalStorageDirectory.getPath());
            return (statFs.getAvailableBlocksLong() - 4L) * statFs.getBlockSizeLong();
        } catch (Throwable th) {
            return 0L;
        }
    }

    public static String getSDCardPath() {
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    public static boolean isSDCardBusy() {
        try {
            return true ^ Environment.getExternalStorageState().equals("mounted");
        } catch (Exception e) {
            Log.e("XMPush-", "check SDCard is busy: " + e);
            return true;
        }
    }

    public static boolean isSDCardFull() {
        return getSDCardAvailableBytes() <= 102400;
    }

    public static boolean isSDCardUnavailable() {
        try {
            return Environment.getExternalStorageState().equals("removed");
        } catch (Exception e) {
            MyLog.e(e);
            return true;
        }
    }

    public static boolean isSDCardUseful() {
        return (isSDCardBusy() || isSDCardFull() || isSDCardUnavailable()) ? false : true;
    }
}
