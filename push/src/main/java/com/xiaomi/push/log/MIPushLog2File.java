package com.xiaomi.push.log;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import com.xiaomi.channel.commonutils.file.SDCardUtils;
import com.xiaomi.channel.commonutils.logger.LoggerInterface;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/log/MIPushLog2File.class */
public class MIPushLog2File implements LoggerInterface {
    private static final long FILE_MAX_SIZE = 1048576;
    private static final String LOCK_FILE = "log.lock";
    private static final int LOGS_MAX_LINE = 20000;
    private static final String NEW_FILE_NAME = "log1.txt";
    private static final String OLD_FILE_NAME = "log0.txt";
    private Handler mHandler;
    private String mSDCardRootPath = "";
    private String mTag;
    private Context sAppContext;
    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss aaa");
    public static String MIPUSH_LOG_PATH = "/MiPushLog";
    private static List<Pair<String, Throwable>> logs = Collections.synchronizedList(new ArrayList());

    public MIPushLog2File(Context context) {
        this.sAppContext = context;
        if (context.getApplicationContext() != null) {
            this.sAppContext = context.getApplicationContext();
        }
        this.mTag = this.sAppContext.getPackageName();
        HandlerThread handlerThread = new HandlerThread("Log2FileHandlerThread");
        handlerThread.start();
        this.mHandler = new Handler(handlerThread.getLooper());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void writeLog2File() {
        File externalFilesDir;
        RandomAccessFile randomAccessFile = null;
        FileLock fileLock = null;
        BufferedWriter bufferedWriter = null;
        try {
            if (TextUtils.isEmpty(this.mSDCardRootPath) && (externalFilesDir = this.sAppContext.getExternalFilesDir(null)) != null) {
                this.mSDCardRootPath = externalFilesDir.getAbsolutePath();
            }
            File file = new File(this.mSDCardRootPath + MIPUSH_LOG_PATH);
            if ((!file.exists() || !file.isDirectory()) && !file.mkdirs()) {
                Log.w(this.mTag, "Create mipushlog directory fail.");
                return;
            }
            File file2 = new File(file, LOCK_FILE);
            if (!file2.exists() || file2.isDirectory()) {
                file2.createNewFile();
            }
            randomAccessFile = new RandomAccessFile(file2, "rw");
            fileLock = randomAccessFile.getChannel().lock();
            bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(file, NEW_FILE_NAME), true)));
            while (!logs.isEmpty()) {
                Pair<String, Throwable> pairRemove = logs.remove(0);
                String str = (String) pairRemove.first;
                if (pairRemove.second != null) {
                    str = str + "\n" + Log.getStackTraceString((Throwable) pairRemove.second);
                }
                bufferedWriter.write(str);
                bufferedWriter.write("\n");
            }
            bufferedWriter.flush();
            File file3 = new File(file, NEW_FILE_NAME);
            if (file3.length() >= FILE_MAX_SIZE) {
                File file4 = new File(file, OLD_FILE_NAME);
                if (file4.exists() && file4.isFile()) {
                    file4.delete();
                }
                file3.renameTo(file4);
            }
        } catch (IOException e) {
            Log.e(this.mTag, "", e);
        } catch (Exception e2) {
            Log.e(this.mTag, "", e2);
        } finally {
            if (bufferedWriter != null) {
                try {
                    bufferedWriter.close();
                } catch (IOException e3) {
                    Log.e(this.mTag, "", e3);
                }
            }
            if (fileLock != null && fileLock.isValid()) {
                try {
                    fileLock.release();
                } catch (IOException e4) {
                    Log.e(this.mTag, "", e4);
                }
            }
            if (randomAccessFile != null) {
                try {
                    randomAccessFile.close();
                } catch (IOException e5) {
                    Log.e(this.mTag, "", e5);
                }
            }
        }
    }

    @Override // com.xiaomi.channel.commonutils.logger.LoggerInterface
    public final void log(String str) {
        log(str, null);
    }

    @Override // com.xiaomi.channel.commonutils.logger.LoggerInterface
    public final void log(final String str, final Throwable th) {
        this.mHandler.post(new Runnable() { // from class: com.xiaomi.push.log.MIPushLog2File.1
            @Override // java.lang.Runnable
            public void run() {
                MIPushLog2File.logs.add(new Pair(String.format("%1$s %2$s %3$s ", MIPushLog2File.dateFormatter.format(new Date()), MIPushLog2File.this.mTag, str), th));
                if (MIPushLog2File.logs.size() > MIPushLog2File.LOGS_MAX_LINE) {
                    int size = (MIPushLog2File.logs.size() - MIPushLog2File.LOGS_MAX_LINE) + 50;
                    for (int i = 0; i < size; i++) {
                        try {
                            if (MIPushLog2File.logs.size() > 0) {
                                MIPushLog2File.logs.remove(0);
                            }
                        } catch (IndexOutOfBoundsException e) {
                        }
                    }
                    MIPushLog2File.logs.add(new Pair(String.format("%1$s %2$s %3$s ", MIPushLog2File.dateFormatter.format(new Date()), MIPushLog2File.this.mTag, "flush " + size + " lines logs."), null));
                }
                try {
                    if (SDCardUtils.isSDCardUseful()) {
                        MIPushLog2File.this.writeLog2File();
                    } else {
                        Log.w(MIPushLog2File.this.mTag, "SDCard is unavailable.");
                    }
                } catch (Exception e2) {
                    Log.e(MIPushLog2File.this.mTag, "", e2);
                }
            }
        });
    }

    @Override // com.xiaomi.channel.commonutils.logger.LoggerInterface
    public final void setTag(String str) {
        this.mTag = str;
    }
}
