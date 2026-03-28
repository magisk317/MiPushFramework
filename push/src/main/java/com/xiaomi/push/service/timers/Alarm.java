package com.xiaomi.push.service.timers;

import android.content.Context;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.mpcd.Constants;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.push.service.XMJobService;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/timers/Alarm.class */
public final class Alarm {
    public static final int HYBRID_ALARM = 2;
    public static final int SYSTEM_ALARM = 0;
    private static final String XMSERVICE_PERMISSION = "android.permission.BIND_JOB_SERVICE";
    private static IAlarm sAlarmInstance;
    private static final String XMSERVICE = XMJobService.class.getCanonicalName();
    private static int sLevel = 0;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/timers/Alarm$IAlarm.class */
    interface IAlarm {
        boolean isAlive();

        void registerPing(boolean z);

        void stop();
    }

    public static void changePolicy(Context context, int i) {
        synchronized (Alarm.class) {
            try {
                int i2 = sLevel;
                if (!PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(context.getPackageName())) {
                    if (i == 2) {
                        sLevel = 2;
                    } else {
                        sLevel = 0;
                    }
                }
                int i3 = sLevel;
                if (i2 != i3 && i3 == 2) {
                    stop();
                    sAlarmInstance = new HybridTimer(context);
                }
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public static void initialize(Context context) {
        Context applicationContext = context.getApplicationContext();
        if (PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(applicationContext.getPackageName())) {
            sAlarmInstance = new AlarmManagerTimer(applicationContext);
            return;
        }
        boolean z = false;
        try {
            android.content.pm.PackageInfo packageInfo = applicationContext.getPackageManager().getPackageInfo(applicationContext.getPackageName(), 4);
            if (packageInfo.services != null) {
                for (android.content.pm.ServiceInfo serviceInfo : packageInfo.services) {
                    if (XMSERVICE_PERMISSION.equals(serviceInfo.permission)) {
                        try {
                            if (XMSERVICE.equals(serviceInfo.name) || XMSERVICE.equals(com.xiaomi.channel.commonutils.android.SystemUtils.loadClass(applicationContext, serviceInfo.name).getSuperclass().getCanonicalName())) {
                                z = true;
                            }
                        } catch (Exception e) {
                        }
                        if (z) {
                            break;
                        }
                    }
                    if (XMSERVICE.equals(serviceInfo.name) && XMSERVICE_PERMISSION.equals(serviceInfo.permission)) {
                        z = true;
                        break;
                    }
                }
            }
        } catch (Exception e2) {
            MyLog.w("check service err : " + e2.getMessage());
        }
        if (!z && com.xiaomi.channel.commonutils.android.SystemUtils.isDebuggable(applicationContext)) {
            throw new RuntimeException("Should export service:" + XMSERVICE + " with permission " + XMSERVICE_PERMISSION + " in AndroidManifest.xml file");
        }
        if (android.os.Build.VERSION.SDK_INT >= 21 && z) {
            try {
                com.xiaomi.channel.commonutils.android.SystemUtils.loadClass(applicationContext, "android.app.job.JobService").getDeclaredField("mBinder");
                sAlarmInstance = new AlarmV21(applicationContext);
                return;
            } catch (Exception e3) {
                sAlarmInstance = new AlarmManagerTimer(applicationContext);
                return;
            }
        }
        sAlarmInstance = new AlarmManagerTimer(applicationContext);
    }

    public static boolean isAlive() {
        synchronized (Alarm.class) {
            try {
                IAlarm iAlarm = sAlarmInstance;
                if (iAlarm == null) {
                    return false;
                }
                return iAlarm.isAlive();
            } finally {
            }
        }
    }

    public static void registerPing(boolean z) {
        synchronized (Alarm.class) {
            try {
                if (sAlarmInstance == null) {
                    MyLog.w("timer is not initialized");
                    return;
                }
                MyLog.v("register alarm. (" + z + Constants.SEPARATOR_RIGHT_PARENTESIS);
                sAlarmInstance.registerPing(z);
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public static void stop() {
        synchronized (Alarm.class) {
            try {
                if (sAlarmInstance == null) {
                    return;
                }
                MyLog.v("stop alarm.");
                sAlarmInstance.stop();
            } finally {
            }
        }
    }
}
