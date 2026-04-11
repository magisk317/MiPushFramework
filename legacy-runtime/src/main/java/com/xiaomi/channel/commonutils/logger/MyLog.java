package com.xiaomi.channel.commonutils.logger;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import android.os.Process;
import android.util.Log;
import com.xiaomi.channel.commonutils.string.XMStringUtils;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/logger/MyLog.class */
public abstract class MyLog {
    private static final String XMSF_PACKAGE_NAME = "com.xiaomi.xmsf";
    public static final int DEBUG = 1;
    public static final int ERROR = 4;
    public static final int FATAL = 5;
    public static final int INFO = 0;
    public static final int WARN = 2;
    private static Context sContext;
    private static int LOG_LEVEL = 2;
    private static boolean isXMSF = false;
    private static String DEFAULT_TAG = "XMPush-" + Process.myPid();
    private static LoggerInterface logger = new DefaultAndroidLogger();
    private static final HashMap<Integer, Long> mStartTimes = new HashMap<>();
    private static final HashMap<Integer, String> mActionNames = new HashMap<>();
    private static final Integer NEGATIVE_CODE = -1;
    private static AtomicInteger mCodeGenerator = new AtomicInteger(1);

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/logger/MyLog$DefaultAndroidLogger.class */
    static class DefaultAndroidLogger implements LoggerInterface {
        private String mTag = MyLog.DEFAULT_TAG;

        DefaultAndroidLogger() {
        }

        @Override // com.xiaomi.channel.commonutils.logger.LoggerInterface
        public void log(String str) {
            Log.v(this.mTag, str);
        }

        @Override // com.xiaomi.channel.commonutils.logger.LoggerInterface
        public void log(String str, Throwable th) {
            Log.v(this.mTag, str, th);
        }

        @Override // com.xiaomi.channel.commonutils.logger.LoggerInterface
        public void setTag(String str) {
            this.mTag = str;
        }
    }

    public static void e(String str) {
        log(4, wrapMessage(str));
    }

    public static void e(String str, Throwable th) {
        log(4, wrapMessage(str), th);
    }

    public static void e(Throwable th) {
        log(4, th);
    }

    public static int getLogLevel() {
        return LOG_LEVEL;
    }

    public static void heapInfo(Context context, String str) {
        Debug.MemoryInfo[] processMemoryInfo = ((ActivityManager) context.getSystemService("activity")).getProcessMemoryInfo(new int[]{Process.myPid()});
        i("+++Heap Info+++" + str + " total:" + processMemoryInfo[0].getTotalPss() + ", managed:" + processMemoryInfo[0].dalvikPss + ", native:" + processMemoryInfo[0].nativePss);
    }

    public static void i(String str) {
        log(0, wrapMessage(str));
    }

    public static void init(Context context) {
        sContext = context;
        if (context != null && XMSF_PACKAGE_NAME.equals(context.getPackageName())) {
            isXMSF = true;
        }
    }

    private static String jointThreadId() {
        return "[Tid:" + Process.myTid() + "] ";
    }

    public static void log(int i, String str) {
        if (i >= LOG_LEVEL) {
            logger.log(str);
        }
    }

    public static void log(int i, String str, Throwable th) {
        if (i >= LOG_LEVEL) {
            logger.log(str, th);
        }
    }

    public static void log(int i, Throwable th) {
        if (i >= LOG_LEVEL) {
            logger.log("", th);
        }
    }

    public static void pe(Integer num) {
        if (LOG_LEVEL <= 1) {
            HashMap<Integer, Long> map = mStartTimes;
            if (map.containsKey(num)) {
                long jLongValue = map.remove(num).longValue();
                String strRemove = mActionNames.remove(num);
                long jCurrentTimeMillis = System.currentTimeMillis();
                logger.log(strRemove + " ends in " + (jCurrentTimeMillis - jLongValue) + " ms");
            }
        }
    }

    public static void persist(String str) {
        if (isXMSF) {
            w(str);
        } else {
            Log.i(DEFAULT_TAG, wrapMessage(str));
        }
    }

    public static void printCallStack(String str) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        printWriter.println(str);
        printWriter.println(String.format("Current thread id (%s); thread name (%s)", Integer.valueOf(Process.myTid()), Thread.currentThread().getName()));
        new Throwable("Call stack").printStackTrace(printWriter);
        v(stringWriter.toString());
    }

    public static Integer ps(String str) {
        if (LOG_LEVEL > 1) {
            return NEGATIVE_CODE;
        }
        Integer numValueOf = Integer.valueOf(mCodeGenerator.incrementAndGet());
        mStartTimes.put(numValueOf, Long.valueOf(System.currentTimeMillis()));
        mActionNames.put(numValueOf, str);
        logger.log(str + " starts");
        return numValueOf;
    }

    public static void setLogLevel(int i) {
        if (i < 0 || i > 5) {
            log(2, "set log level as " + i);
        }
        LOG_LEVEL = i;
    }

    public static void setLogger(LoggerInterface loggerInterface) {
        logger = loggerInterface;
    }

    public static void v(String str) {
        log(1, wrapMessage(str));
    }

    public static void v(String str, String str2) {
        log(1, wrapMessage(str, str2));
    }

    public static void v(Object[] objArr) {
        log(1, XMStringUtils.join(objArr, ","));
    }

    public static void w(String str) {
        log(2, wrapMessage(str));
    }

    public static void w(String str, String str2) {
        log(2, wrapMessage(str, str2));
    }

    private static String wrapMessage(String str) {
        return jointThreadId() + str;
    }

    private static String wrapMessage(String str, String str2) {
        return jointThreadId() + "[" + str + "] " + str2;
    }
}
