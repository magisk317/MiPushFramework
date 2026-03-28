package com.xiaomi.mipush.sdk;

import android.content.Context;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.file.IOUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/CrashStorage.class */
public class CrashStorage {
    public static final String CRASH_FILE_PATH = "/crash";
    private static final Object mCrashFileLock = new Object();
    private static volatile CrashStorage sInstance;
    private Context mContext;

    private CrashStorage(Context context) {
        this.mContext = context;
    }

    private File getCrashFileBySummary(String str) {
        File file = new File(this.mContext.getFilesDir() + CRASH_FILE_PATH);
        if (!file.exists()) {
            file.mkdirs();
            return null;
        }
        File[] fileArrListFiles = file.listFiles();
        for (int i = 0; i < fileArrListFiles.length; i++) {
            if (fileArrListFiles[i].isFile() && fileArrListFiles[i].getName().startsWith(str)) {
                return fileArrListFiles[i];
            }
        }
        return null;
    }

    public static CrashStorage getInstance(Context context) {
        if (sInstance == null) {
            synchronized (CrashStorage.class) {
                try {
                    if (sInstance == null) {
                        sInstance = new CrashStorage(context);
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
        return sInstance;
    }

    public ArrayList<File> getAllCrashFile() {
        ArrayList<File> arrayList = new ArrayList<>();
        File file = new File(this.mContext.getFilesDir() + CRASH_FILE_PATH);
        if (!file.exists()) {
            file.mkdirs();
            return arrayList;
        }
        File[] fileArrListFiles = file.listFiles();
        for (int i = 0; i < fileArrListFiles.length; i++) {
            String[] strArrSplit = fileArrListFiles[i].getName().split(":");
            if (strArrSplit.length >= 2 && Integer.parseInt(strArrSplit[1]) >= 1 && fileArrListFiles[i].isFile()) {
                arrayList.add(fileArrListFiles[i]);
            }
        }
        return arrayList;
    }

    public String getCrashSummary(File file) {
        if (file == null) {
            return "";
        }
        String[] strArrSplit = file.getName().split(":");
        return strArrSplit.length != 2 ? "" : strArrSplit[0];
    }

    public void writeCrash2File(String str, String str2) {
        if (TextUtils.isEmpty(str2) || TextUtils.isEmpty(str)) {
            return;
        }
        synchronized (mCrashFileLock) {
            File crashFileBySummary = getCrashFileBySummary(str2);
            if (crashFileBySummary != null) {
                String[] strArrSplit = crashFileBySummary.getName().split(":");
                if (strArrSplit.length < 2) {
                    return;
                }
                crashFileBySummary.renameTo(new File(this.mContext.getFilesDir() + CRASH_FILE_PATH + "/" + str2 + ":" + String.valueOf(Integer.parseInt(strArrSplit[1]) + 1)));
            } else {
                FileOutputStream fileOutputStream = null;
                FileOutputStream fileOutputStream2 = null;
                try {
                    try {
                        StringBuilder sb = new StringBuilder();
                        sb.append(this.mContext.getFilesDir());
                        sb.append(CRASH_FILE_PATH);
                        sb.append("/");
                        sb.append(str2);
                        sb.append(":");
                        sb.append("1");
                        FileOutputStream fileOutputStream3 = new FileOutputStream(new File(sb.toString()));
                        fileOutputStream3.write(str.getBytes());
                        fileOutputStream = fileOutputStream3;
                        fileOutputStream2 = fileOutputStream3;
                        fileOutputStream3.flush();
                        IOUtils.closeQuietly(fileOutputStream3);
                    } catch (Throwable th) {
                        IOUtils.closeQuietly(fileOutputStream);
                        throw th;
                    }
                } catch (Exception e) {
                    fileOutputStream = fileOutputStream2;
                    MyLog.e(e);
                    IOUtils.closeQuietly(fileOutputStream2);
                }
            }
        }
    }
}
