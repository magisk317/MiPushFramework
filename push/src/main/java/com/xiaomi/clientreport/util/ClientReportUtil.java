package com.xiaomi.clientreport.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.file.IOUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.string.Base64Coder;
import com.xiaomi.channel.commonutils.string.XMStringUtils;
import com.xiaomi.clientreport.data.ClientReportConstants;
import com.xiaomi.clientreport.manager.ClientReportLogicManager;
import com.xiaomi.mipush.sdk.Constants;
import com.xiaomi.push.service.PushConstants;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.Arrays;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/clientreport/util/ClientReportUtil.class */
public class ClientReportUtil {
    public static String getEventKeyWithDefault(Context context) {
        String stringValue = SPManager.getInstance(context).getStringValue(ClientReportConstants.SP_FILE_STATUS, ClientReportConstants.SP_KEY_KEY, "");
        String strGenerateRandomString = stringValue;
        if (TextUtils.isEmpty(stringValue)) {
            strGenerateRandomString = XMStringUtils.generateRandomString(20);
            SPManager.getInstance(context).setStringnValue(ClientReportConstants.SP_FILE_STATUS, ClientReportConstants.SP_KEY_KEY, strGenerateRandomString);
        }
        return strGenerateRandomString;
    }

    public static String getOs() {
        return Build.VERSION.RELEASE + Constants.ACCEPT_TIME_SEPARATOR_SERVER + Build.VERSION.INCREMENTAL;
    }

    public static File[] getReadFileName(Context context, String str) {
        File externalFilesDir = context.getExternalFilesDir(str);
        if (externalFilesDir != null) {
            return externalFilesDir.listFiles(new FilenameFilter() { // from class: com.xiaomi.clientreport.util.ClientReportUtil.2
                @Override // java.io.FilenameFilter
                public boolean accept(File file, String str2) {
                    return (TextUtils.isEmpty(str2) || str2.toLowerCase().endsWith(".lock")) ? false : true;
                }
            });
        }
        return null;
    }

    public static boolean isFileCanBeUse(Context context, String str) {
        File file = new File(str);
        boolean z = true;
        long maxFileLength = ClientReportLogicManager.getInstance(context).getConfig().getMaxFileLength();
        if (file.exists()) {
            try {
                if (file.length() > maxFileLength) {
                    z = false;
                }
            } catch (Exception e) {
                MyLog.e(e);
                z = false;
            }
        } else {
            IOUtils.createFileQuietly(file);
            z = true;
        }
        return z;
    }

    public static boolean isSupportXMSFUpload(Context context) {
        boolean z = false;
        try {
            PackageInfo packageInfo = context.getApplicationContext().getPackageManager().getPackageInfo(PushConstants.PUSH_SERVICE_PACKAGE_NAME, 0);
            if (getVersionCode(packageInfo) >= 108) {
                z = true;
            }
            return z;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void moveFiles(Context context, String str, String str2) {
        File externalFilesDir = context.getExternalFilesDir(str);
        File externalFilesDir2 = context.getExternalFilesDir(str2);
        if (externalFilesDir == null || externalFilesDir2 == null || !externalFilesDir.exists()) {
            return;
        }
        if (!externalFilesDir2.exists()) {
            externalFilesDir2.mkdirs();
        }
        File[] listFiles = externalFilesDir.listFiles(new FilenameFilter() { // from class: com.xiaomi.clientreport.util.ClientReportUtil.1
            @Override // java.io.FilenameFilter
            public boolean accept(File file, String str3) {
                return !TextUtils.isEmpty(str3) && !str3.toLowerCase().endsWith(".lock");
            }
        });
        if (listFiles == null || listFiles.length == 0) {
            return;
        }
        for (File file : listFiles) {
            if (file == null || !file.exists() || !file.isFile()) {
                continue;
            }
            File file2 = new File(file.getAbsolutePath() + ".lock");
            RandomAccessFile randomAccessFile = null;
            FileLock fileLock = null;
            try {
                IOUtils.createFileQuietly(file2);
                randomAccessFile = new RandomAccessFile(file2, "rw");
                fileLock = randomAccessFile.getChannel().lock();
                File file3 = new File(externalFilesDir2, file.getName());
                int i = 0;
                while (file3.exists()) {
                    file3 = new File(externalFilesDir2, file.getName() + "_" + i);
                    i++;
                }
                if (!file.renameTo(file3)) {
                    IOUtils.copyFile(file, file3);
                    file.delete();
                }
            } catch (Exception e) {
                MyLog.e(e);
            } finally {
                if (fileLock != null && fileLock.isValid()) {
                    try {
                        fileLock.release();
                    } catch (IOException e2) {
                        MyLog.e(e2);
                    }
                }
                IOUtils.closeQuietly(randomAccessFile);
                if (file2.exists()) {
                    file2.delete();
                }
            }
        }
    }

    public static byte[] parseKey(String str) {
        byte[] bArrCopyOf = Arrays.copyOf(Base64Coder.decode(str), 16);
        bArrCopyOf[0] = (byte) 68;
        bArrCopyOf[15] = (byte) 84;
        return bArrCopyOf;
    }

    private static long getVersionCode(PackageInfo packageInfo) {
        try {
            return ((Number) PackageInfo.class.getMethod("getLongVersionCode").invoke(packageInfo)).longValue();
        } catch (ReflectiveOperationException unused) {
            try {
                return ((Number) PackageInfo.class.getField("versionCode").get(packageInfo)).longValue();
            } catch (ReflectiveOperationException e) {
                MyLog.e(e);
                return 0L;
            }
        }
    }

    public static void sendData(Context context, String str) {
        Intent intent = new Intent(ClientReportConstants.XMSF_UPLOAD);
        intent.putExtra("pkgname", context.getPackageName());
        intent.putExtra("category", "category_client_report_data");
        intent.putExtra("name", ClientReportConstants.NAME);
        intent.putExtra("data", str);
        context.sendBroadcast(intent, ClientReportConstants.XMSF_UPLOAD_PERMISSION);
    }

    public static void sendFile(Context context, List<String> list) {
        if (list == null || list.size() <= 0 || !isSupportXMSFUpload(context)) {
            return;
        }
        for (String str : list) {
            if (!TextUtils.isEmpty(str)) {
                sendData(context, str);
            }
        }
    }
}
