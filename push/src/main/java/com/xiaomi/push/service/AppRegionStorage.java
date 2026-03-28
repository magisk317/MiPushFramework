package com.xiaomi.push.service;

import android.content.Context;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.file.IOUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/AppRegionStorage.class */
public class AppRegionStorage {
    private static volatile AppRegionStorage sInstance;
    private Context mContext;
    private volatile String mCountryCode;
    private volatile String mRegion;
    private final Object mRegionLock = new Object();
    private final Object mCountryCodeLock = new Object();
    private final String REGION_CACHE_FILE_NAME = "mipush_region";
    private final String COUNTRY_CODE_CACHE_FILE_NAME = "mipush_country_code";
    private final String REGION_CACHE_FILE_LOCK = "mipush_region.lock";
    private final String COUNTRY_CODE_CACHE_FILE_LOCK = "mipush_country_code.lock";

    public AppRegionStorage(Context context) {
        this.mContext = context;
    }

    public static AppRegionStorage getInstance(Context context) {
        if (sInstance == null) {
            synchronized (AppRegionStorage.class) {
                try {
                    if (sInstance == null) {
                        sInstance = new AppRegionStorage(context);
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
        return sInstance;
    }

    private String readFromFileWithLock(Context context, String str, String str2, Object obj) {
        String strFileToStr;
        File file = new File(context.getFilesDir(), str);
        if (!file.exists()) {
            MyLog.w("No ready file to get data from " + str);
            return null;
        }
        synchronized (obj) {
            RandomAccessFile randomAccessFile = null;
            FileLock fileLock = null;
            RandomAccessFile randomAccessFile2 = null;
            FileLock fileLock2 = null;
            try {
                try {
                    File file2 = new File(context.getFilesDir(), str2);
                    IOUtils.createFileQuietly(file2);
                    RandomAccessFile randomAccessFile3 = new RandomAccessFile(file2, "rw");
                    FileLock fileLockLock = randomAccessFile3.getChannel().lock();
                    randomAccessFile2 = randomAccessFile3;
                    fileLock2 = fileLockLock;
                    strFileToStr = IOUtils.fileToStr(file);
                    if (fileLockLock != null && fileLockLock.isValid()) {
                        try {
                            fileLockLock.release();
                        } catch (IOException e) {
                            MyLog.e(e);
                        }
                    }
                    IOUtils.closeQuietly(randomAccessFile3);
                } catch (Exception e2) {
                    randomAccessFile = randomAccessFile2;
                    fileLock = fileLock2;
                    MyLog.e(e2);
                    if (fileLock2 != null && fileLock2.isValid()) {
                        try {
                            fileLock2.release();
                        } catch (IOException e3) {
                            MyLog.e(e3);
                        }
                    }
                    IOUtils.closeQuietly(randomAccessFile2);
                    return null;
                }
            } finally {
            }
        }
        return strFileToStr;
    }

    private void write2FileWithLock(Context context, String str, String str2, String str3, Object obj) {
        RandomAccessFile randomAccessFile;
        synchronized (obj) {
            RandomAccessFile randomAccessFile2 = null;
            FileLock fileLock = null;
            RandomAccessFile randomAccessFile3 = null;
            FileLock fileLock2 = null;
            try {
                try {
                    File file = new File(context.getFilesDir(), str3);
                    IOUtils.createFileQuietly(file);
                    RandomAccessFile randomAccessFile4 = new RandomAccessFile(file, "rw");
                    FileLock fileLockLock = randomAccessFile4.getChannel().lock();
                    randomAccessFile3 = randomAccessFile4;
                    fileLock2 = fileLockLock;
                    IOUtils.strToFile(new File(context.getFilesDir(), str2), str);
                    randomAccessFile = randomAccessFile4;
                    if (fileLockLock != null) {
                        randomAccessFile = randomAccessFile4;
                        if (fileLockLock.isValid()) {
                            try {
                                fileLockLock.release();
                                randomAccessFile = randomAccessFile4;
                            } catch (IOException e) {
                                MyLog.e(e);
                                randomAccessFile = randomAccessFile4;
                            }
                        }
                    }
                } catch (Exception e2) {
                    randomAccessFile2 = randomAccessFile3;
                    fileLock = fileLock2;
                    MyLog.e(e2);
                    randomAccessFile = randomAccessFile3;
                    if (fileLock2 != null) {
                        randomAccessFile = randomAccessFile3;
                        if (fileLock2.isValid()) {
                            try {
                                fileLock2.release();
                                randomAccessFile = randomAccessFile3;
                            } catch (IOException e3) {
                                MyLog.e(e3);
                                randomAccessFile = randomAccessFile3;
                            }
                        }
                    }
                }
                IOUtils.closeQuietly(randomAccessFile);
            } catch (Throwable th) {
                if (fileLock != null && fileLock.isValid()) {
                    try {
                        fileLock.release();
                    } catch (IOException e4) {
                        MyLog.e(e4);
                    }
                }
                IOUtils.closeQuietly(randomAccessFile2);
                throw th;
            }
        }
    }

    public String getCountryCode() {
        if (TextUtils.isEmpty(this.mCountryCode)) {
            this.mCountryCode = readFromFileWithLock(this.mContext, "mipush_country_code", "mipush_country_code.lock", this.mCountryCodeLock);
        }
        return this.mCountryCode;
    }

    public String getRegion() {
        if (TextUtils.isEmpty(this.mRegion)) {
            this.mRegion = readFromFileWithLock(this.mContext, "mipush_region", "mipush_region.lock", this.mRegionLock);
        }
        return this.mRegion;
    }

    public void setCountryCode(String str) {
        if (TextUtils.equals(str, this.mCountryCode)) {
            return;
        }
        this.mCountryCode = str;
        write2FileWithLock(this.mContext, this.mCountryCode, "mipush_country_code", "mipush_country_code.lock", this.mCountryCodeLock);
    }

    public void setRegion(String str) {
        if (TextUtils.equals(str, this.mRegion)) {
            return;
        }
        this.mRegion = str;
        write2FileWithLock(this.mContext, this.mRegion, "mipush_region", "mipush_region.lock", this.mRegionLock);
    }
}
