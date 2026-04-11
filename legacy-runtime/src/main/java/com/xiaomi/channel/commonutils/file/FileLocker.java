package com.xiaomi.channel.commonutils.file;

import android.content.Context;
import com.xiaomi.channel.commonutils.logger.MyLog;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/file/FileLocker.class */
public final class FileLocker {
    private static final String LOCK = ".LOCK";
    private static final Set<String> LOCK_HELD = Collections.synchronizedSet(new HashSet<>());
    private Context mContext;
    private FileLock mLock;
    private RandomAccessFile mLockFile;
    private String mLockFileName;

    private FileLocker(Context context) {
        this.mContext = context;
    }

    public static FileLocker lock(Context context, File file) throws IOException {
        MyLog.v("Locking: " + file.getAbsolutePath());
        String str = file.getAbsolutePath() + LOCK;
        File file2 = new File(str);
        if (!file2.exists()) {
            file2.getParentFile().mkdirs();
            file2.createNewFile();
        }
        Set<String> set = LOCK_HELD;
        if (!set.add(str)) {
            throw new IOException("abtain lock failure");
        }
        FileLocker fileLocker = new FileLocker(context);
        fileLocker.mLockFileName = str;
        try {
            RandomAccessFile randomAccessFile = new RandomAccessFile(file2, "rw");
            fileLocker.mLockFile = randomAccessFile;
            fileLocker.mLock = randomAccessFile.getChannel().lock();
            MyLog.v("Locked: " + str + " :" + fileLocker.mLock);
            if (fileLocker.mLock == null) {
                RandomAccessFile randomAccessFile2 = fileLocker.mLockFile;
                if (randomAccessFile2 != null) {
                    IOUtils.closeQuietly(randomAccessFile2);
                }
                set.remove(fileLocker.mLockFileName);
            }
            return fileLocker;
        } catch (Throwable th) {
            if (fileLocker.mLock == null) {
                RandomAccessFile randomAccessFile3 = fileLocker.mLockFile;
                if (randomAccessFile3 != null) {
                    IOUtils.closeQuietly(randomAccessFile3);
                }
                LOCK_HELD.remove(fileLocker.mLockFileName);
            }
            throw th;
        }
    }

    public void unlock() {
        MyLog.v("unLock: " + this.mLock);
        FileLock fileLock = this.mLock;
        if (fileLock != null && fileLock.isValid()) {
            try {
                this.mLock.release();
            } catch (IOException e) {
            }
            this.mLock = null;
        }
        RandomAccessFile randomAccessFile = this.mLockFile;
        if (randomAccessFile != null) {
            IOUtils.closeQuietly(randomAccessFile);
        }
        LOCK_HELD.remove(this.mLockFileName);
    }
}
