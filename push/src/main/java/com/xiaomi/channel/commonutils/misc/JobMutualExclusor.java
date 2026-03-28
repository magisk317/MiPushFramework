package com.xiaomi.channel.commonutils.misc;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.AppInfoUtils;
import com.xiaomi.channel.commonutils.android.DeviceInfo;
import com.xiaomi.channel.commonutils.file.IOUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/misc/JobMutualExclusor.class */
public class JobMutualExclusor {
    public static final float CHECK_FRACTION = 0.9f;
    private static final String INFO_SEPARATOR = ",";
    private static final String LAST_COLLECT_FILE_PATH = "lcfp";
    private static final String LAST_COLLECT_FILE_PATH_LOCK = "lcfp.lock";
    private static final String WRITE_FILE_PERMISSION = "android.permission.WRITE_EXTERNAL_STORAGE";

    public static boolean checkPeriodAndRecordWithFileLock(Context context, String str, long j) {
        if (Build.VERSION.SDK_INT >= 23 && !AppInfoUtils.checkSelfPermission(context, "android.permission.WRITE_EXTERNAL_STORAGE")) {
            return true;
        }
        RandomAccessFile randomAccessFile = null;
        FileLock fileLock = null;
        RandomAccessFile randomAccessFile2 = null;
        FileLock fileLock2 = null;
        try {
            try {
                File file = new File(new File(Environment.getExternalStorageDirectory(), DeviceInfo.VIRTUAL_DEVICE_DIR), LAST_COLLECT_FILE_PATH_LOCK);
                IOUtils.createFileQuietly(file);
                RandomAccessFile randomAccessFile3 = new RandomAccessFile(file, "rw");
                FileLock fileLockLock = randomAccessFile3.getChannel().lock();
                randomAccessFile = randomAccessFile3;
                fileLock = fileLockLock;
                randomAccessFile2 = randomAccessFile3;
                fileLock2 = fileLockLock;
                boolean zCheckPeriodAndRecordWorking = checkPeriodAndRecordWorking(context, str, j);
                if (fileLockLock != null && fileLockLock.isValid()) {
                    try {
                        fileLockLock.release();
                    } catch (IOException e) {
                    }
                }
                IOUtils.closeQuietly(randomAccessFile3);
                return zCheckPeriodAndRecordWorking;
            } catch (IOException e2) {
                randomAccessFile = randomAccessFile2;
                fileLock = fileLock2;
                e2.printStackTrace();
                if (fileLock2 != null && fileLock2.isValid()) {
                    try {
                        fileLock2.release();
                    } catch (IOException e3) {
                    }
                }
                IOUtils.closeQuietly(randomAccessFile2);
                return true;
            }
        } catch (Throwable th) {
            if (fileLock != null && fileLock.isValid()) {
                try {
                    fileLock.release();
                } catch (IOException e4) {
                }
            }
            IOUtils.closeQuietly(randomAccessFile);
            if (th instanceof RuntimeException) {
                throw (RuntimeException) th;
            }
            throw new RuntimeException(th);
        }
    }

    private static boolean checkPeriodAndRecordWorking(Context context, String str, long j) throws Throwable {
        if (TextUtils.isEmpty(str) || j <= 0) {
            return true;
        }
        File file = new File(new File(Environment.getExternalStorageDirectory(), DeviceInfo.VIRTUAL_DEVICE_DIR), LAST_COLLECT_FILE_PATH);
        IOUtils.createFileQuietly(file);
        String strFileToStr = IOUtils.fileToStr(file);
        long jCurrentTimeMillis = System.currentTimeMillis();
        long j2 = (long) (j * CHECK_FRACTION);
        String packageName = context.getPackageName();
        List<String> arrayList = new ArrayList<>();
        boolean z = true;
        boolean z2 = false;
        if (!TextUtils.isEmpty(strFileToStr)) {
            String[] strArrSplit = strFileToStr.split("\n");
            for (String str2 : strArrSplit) {
                if (TextUtils.isEmpty(str2)) {
                    continue;
                }
                String[] strArrSplit2 = str2.split(INFO_SEPARATOR);
                if (strArrSplit2.length < 3) {
                    continue;
                }
                if (TextUtils.equals(str, strArrSplit2[0])) {
                    z2 = true;
                    try {
                        long j3 = Long.parseLong(strArrSplit2[2]);
                        if (Math.abs(jCurrentTimeMillis - j3) < j2) {
                            z = false;
                        }
                    } catch (NumberFormatException e) {
                        MyLog.e(e);
                    }
                    if (z) {
                        arrayList.add(str + INFO_SEPARATOR + packageName + INFO_SEPARATOR + jCurrentTimeMillis);
                    } else {
                        arrayList.add(str2);
                    }
                } else {
                    arrayList.add(str2);
                }
            }
        }
        if (z && !z2) {
            arrayList.add(str + INFO_SEPARATOR + packageName + INFO_SEPARATOR + jCurrentTimeMillis);
        }
        if (z) {
            StringBuilder sb = new StringBuilder();
            for (String str3 : arrayList) {
                if (!TextUtils.isEmpty(str3)) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(str3);
                }
            }
            IOUtils.strToFile(file, sb.toString());
        }
        return z;
    }
}
