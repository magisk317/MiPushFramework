package com.xiaomi.channel.commonutils.msa;

import android.content.Context;
import com.xiaomi.channel.commonutils.android.SystemUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/msa/MdidJLibrary.class */
class MdidJLibrary {
    private static final String CORE_CLASS_JLIBRARY = "com.bun.miitmdid.core.JLibrary";
    private static volatile boolean sJLibraryInited = false;

    MdidJLibrary() {
    }

    private static void callInitEntry(Class<?> cls, Context context) {
        if (sJLibraryInited) {
            return;
        }
        try {
            sJLibraryInited = true;
            cls.getDeclaredMethod("InitEntry", Context.class).invoke(cls, context);
        } catch (Throwable th) {
            MyLog.w("mdid:load lib error " + th);
        }
    }

    public static boolean checkAndLoadMdidSdk(Context context) {
        try {
            Class<?> clsLoadClass = SystemUtils.loadClass(context, CORE_CLASS_JLIBRARY);
            if (clsLoadClass == null) {
                return false;
            }
            callInitEntry(clsLoadClass, context);
            return true;
        } catch (Throwable th) {
            MyLog.w("mdid:check error " + th);
            return false;
        }
    }
}
