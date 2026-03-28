package com.xiaomi.channel.commonutils.file;

import android.content.Context;
import android.text.TextUtils;
import java.io.File;
import java.io.IOException;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/file/FileLockerWorker.class */
public abstract class FileLockerWorker implements Runnable {
    private static final String DEFAULT_LOCKER = "default_locker";
    private Context mContext;
    private File mFile;
    private Runnable mRunnable;

    private FileLockerWorker(Context context, File file) {
        this.mContext = context;
        this.mFile = file;
    }

    public static void runMutiProcessJob(Context context, File file, final Runnable runnable) {
        new FileLockerWorker(context, file) { // from class: com.xiaomi.channel.commonutils.file.FileLockerWorker.1
            @Override // com.xiaomi.channel.commonutils.file.FileLockerWorker
            protected void doWork(Context context2) {
                Runnable runnable2 = runnable;
                if (runnable2 != null) {
                    runnable2.run();
                }
            }
        }.run();
    }

    public static void runMutiProcessJob(Context context, String str, Runnable runnable) {
        File file = null;
        if (!TextUtils.isEmpty(str)) {
            file = new File(context.getFilesDir(), str);
        }
        runMutiProcessJob(context, file, runnable);
    }

    protected abstract void doWork(Context context);

    @Override // java.lang.Runnable
    public final void run() {
        FileLocker fileLocker = null;
        try {
            if (this.mFile == null) {
                this.mFile = new File(this.mContext.getFilesDir(), DEFAULT_LOCKER);
            }
            fileLocker = FileLocker.lock(this.mContext, this.mFile);
            Runnable runnable = this.mRunnable;
            if (runnable != null) {
                runnable.run();
            }
            doWork(this.mContext);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Throwable th) {
            if (fileLocker != null) {
                fileLocker.unlock();
            }
            throw th;
        }
        if (fileLocker != null) {
            fileLocker.unlock();
        }
    }
}
