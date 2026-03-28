package com.xiaomi.mipush.sdk.stat.util;

import android.content.Context;
import android.os.Build;
import android.system.Os;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.mipush.sdk.stat.db.DataBaseConfig;
import com.xiaomi.push.service.OnlineConfig;
import com.xiaomi.xmpush.thrift.ConfigKey;
import java.io.File;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/stat/util/FileUtil.class */
public class FileUtil {
    public static long getFileSize(String str) {
        long j = 0;
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                j = 0;
                if (new File(str).exists()) {
                    j = Os.stat(str).st_size;
                }
            } catch (Exception e) {
                MyLog.e(e);
                j = 0;
            }
        }
        return j;
    }

    public static int getSuitableLimit(Context context) {
        int intValue = OnlineConfig.getInstance(context).getIntValue(ConfigKey.StatDataProcessFrequency.getValue(), DataBaseConfig.DEFAULT_NUM);
        long jFreeMemory = Runtime.getRuntime().freeMemory() >> 20;
        long j = jFreeMemory;
        if (jFreeMemory <= 0) {
            j = 1;
        }
        return (int) (((long) intValue) * j);
    }
}
