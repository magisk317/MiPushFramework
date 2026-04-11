package com.xiaomi.channel.commonutils.android;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.file.FileLocker;
import com.xiaomi.channel.commonutils.file.IOUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.msa.MsaIdManager;
import com.xiaomi.channel.commonutils.reflect.JavaCalls;
import com.xiaomi.channel.commonutils.string.XMStringUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/android/DeviceInfo.class */
public class DeviceInfo {
    private static final String COMMA_SEPARATOR = ",";
    private static final int DEFAULT_IMEI_BLOCK_COUNT = 10;
    private static final int MAX_VDEVID_LEN = 128;
    private static final int RULE_1_IMIE = 1;
    private static final int RULE_2_UDID = 2;
    private static final int RULE_3_SDCARD = 3;
    private static final int RULE_4_OAID = 4;
    private static final int RULE_5_ANDROIDID = 5;
    private static final String STR_NULL = "null";
    private static final String STR_UNKNOWN = "unknown";
    public static final String VIRTUAL_DEVICE_DIR = "/.vdevdir/";
    private static final String VIRTUAL_DEVICE_FILE = ".vdevid";
    private static final String VIRTUAL_DEVICE_LOCAL_FILE = ".vdevidlocal";
    private static String sCachedIMEI = null;
    private static String sCachedSubIMEIS = "";
    private static String sCachedDeviceId = null;
    private static String sCachedSimpleDeviceId = null;
    private static final String SPLIT_CHAR = String.valueOf((char) 2);
    public static final String OLD_DEVICE_PREFIX = "a-";
    private static final String[] DEV_PREFIX_ARRAY = {"--", OLD_DEVICE_PREFIX, "u-", "v-", "o-", "g-"};
    private static String sVirtDevId = null;
    private static volatile boolean sVirtDevIDChecked = false;

    public static String blockingGetIMEI(Context context) {
        String strQuicklyGetIMEI = quicklyGetIMEI(context);
        for (int imeiBlockCount = getImeiBlockCount(); strQuicklyGetIMEI == null && imeiBlockCount > 0; imeiBlockCount--) {
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
            }
            strQuicklyGetIMEI = quicklyGetIMEI(context);
        }
        return strQuicklyGetIMEI;
    }

    private static String blockingGetIMEIWhenDeviceRegister(Context context) {
        String strQuicklyGetIMEI = quicklyGetIMEI(context);
        for (int imeiBlockCount = getImeiBlockCount(); TextUtils.isEmpty(strQuicklyGetIMEI) && imeiBlockCount > 0; imeiBlockCount--) {
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
            }
            strQuicklyGetIMEI = quicklyGetIMEI(context);
        }
        return strQuicklyGetIMEI;
    }

    public static String blockingGetSubIMEIS(Context context) {
        String strQuicklyGetSubIMEIS = quicklyGetSubIMEIS(context);
        for (int imeiBlockCount = getImeiBlockCount(); strQuicklyGetSubIMEIS == null && imeiBlockCount > 0; imeiBlockCount--) {
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
            }
            strQuicklyGetSubIMEIS = quicklyGetSubIMEIS(context);
        }
        return strQuicklyGetSubIMEIS;
    }

    public static String blockingGetSubIMEISMd5(Context context) {
        String strQuicklyGetSubIMEISMd5 = quicklyGetSubIMEISMd5(context);
        for (int imeiBlockCount = getImeiBlockCount(); strQuicklyGetSubIMEISMd5 == null && imeiBlockCount > 0; imeiBlockCount--) {
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
            }
            strQuicklyGetSubIMEISMd5 = quicklyGetSubIMEISMd5(context);
        }
        return strQuicklyGetSubIMEISMd5;
    }

    public static ArrayList<String> blockinggetIMEIList(Context context) {
        ArrayList<String> iMEIList = getIMEIList(context);
        for (int imeiBlockCount = getImeiBlockCount(); iMEIList == null && imeiBlockCount > 0; imeiBlockCount--) {
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
            }
            iMEIList = getIMEIList(context);
        }
        return iMEIList;
    }

    private static boolean canReadPhoneState(Context context) {
        String packageName = context.getPackageName();
        return context.getPackageManager().checkPermission(PermissionUtils.readPhoneState, packageName) == 0 || context.getPackageManager().checkPermission("android.permission.READ_PRIVILEGED_PHONE_STATE", packageName) == 0;
    }

    /* JADX WARN: Finally extract failed */
    public static String checkVirtDevId(Context context) {
        if (!isSupportVDevid(context) || sVirtDevIDChecked) {
            return null;
        }
        sVirtDevIDChecked = true;
        String strFileToStr = IOUtils.fileToStr(new File(context.getFilesDir(), VIRTUAL_DEVICE_FILE));
        FileLocker fileLocker = null;
        String str = null;
        try {
            try {
                File file = new File(new File(Environment.getExternalStorageDirectory(), VIRTUAL_DEVICE_DIR), VIRTUAL_DEVICE_FILE);
                fileLocker = FileLocker.lock(context, file);
                str = IOUtils.fileToStr(file);
            } catch (IOException e) {
                MyLog.w("check id failure.");
            }
            if (fileLocker != null) {
                fileLocker.unlock();
                fileLocker = null;
            }
            if (TextUtils.isEmpty(strFileToStr)) {
                MyLog.w("empty local vid");
                return "F*";
            }
            sVirtDevId = strFileToStr;
            if (TextUtils.isEmpty(str) || str.length() > MAX_VDEVID_LEN) {
                MyLog.w("recover vid :" + str);
                updateVirtDevId(context, strFileToStr);
            } else if (!TextUtils.equals(strFileToStr, str)) {
                MyLog.w("vid changed, need sync");
                return str;
            }
            MyLog.v("vdevid = " + sVirtDevId + " " + str);
            return null;
        } catch (Throwable th) {
            if (fileLocker != null) {
                fileLocker.unlock();
            }
            throw th;
        }
    }

    public static void fillLocalVirtDevId(Context context, Map<String, String> map) {
        if (map == null || context == null) {
            return;
        }
        String localVirtDevId = readLocalVirtDevId(context);
        if (TextUtils.isEmpty(localVirtDevId)) {
            return;
        }
        map.put("local_virt_devid", localVirtDevId);
    }

    private static float formatRamFromProcMeminfo(int i) {
        float f = ((((((102400 + i) / 524288) + 1) * 512) * 1024) / 1024.0f) / 1024.0f;
        float fCeil = f;
        if (f > 0.5d) {
            fCeil = (float) Math.ceil(f);
        }
        return fCeil;
    }

    public static String getAndroidId(Context context) {
        String string;
        try {
            string = Settings.Secure.getString(context.getContentResolver(), "android_id");
        } catch (Throwable th) {
            MyLog.w("failure to get androidId: " + th.getMessage());
            string = null;
        }
        return string;
    }

    private static String getDevPrefix(int i) {
        if (i > 0) {
            String[] strArr = DEV_PREFIX_ARRAY;
            if (i < strArr.length) {
                return strArr[i];
            }
        }
        return DEV_PREFIX_ARRAY[0];
    }

    public static String getDeviceId(Context context, boolean z) {
        if (sCachedDeviceId == null) {
            String androidId = getAndroidId(context);
            String oaid = "";
            int i = 1;
            switch (1) {
                case 1:
                    String strBlockingGetIMEI = MIUIUtils.isGlobalRegion() ? "" : z ? blockingGetIMEI(context) : blockingGetIMEIWhenDeviceRegister(context);
                    String serialNum = getSerialNum(context);
                    if ((Build.VERSION.SDK_INT < 26) || !isInvalidStr(strBlockingGetIMEI) || !isInvalidStr(serialNum)) {
                        oaid = strBlockingGetIMEI + androidId + serialNum;
                        i = 1;
                        break;
                    }
                case 2:
                    String udid = MsaIdManager.getInstance(context).getUDID();
                    if (!TextUtils.isEmpty(udid)) {
                        oaid = udid + androidId;
                        i = 2;
                        break;
                    }
                case 3:
                    oaid = readLocalVirtDevId(context);
                    if (!TextUtils.isEmpty(oaid)) {
                        i = 3;
                        break;
                    }
                case 4:
                    oaid = MsaIdManager.getInstance(context).getOAID();
                    if (!TextUtils.isEmpty(oaid)) {
                        i = 4;
                        break;
                    }
                case 5:
                    oaid = androidId;
                    i = 5;
                    break;
            }
            MyLog.i("devid rule select:" + i);
            if (i == 3) {
                sCachedDeviceId = oaid;
            } else {
                sCachedDeviceId = getDevPrefix(i) + XMStringUtils.getSHA1Digest(oaid);
            }
            writeLocalVirtDevIdIfNeed(context, sCachedDeviceId);
        }
        return sCachedDeviceId;
    }

    public static String getDeviceId1(Context context) {
        return OLD_DEVICE_PREFIX + XMStringUtils.getSHA1Digest(((String) null) + getAndroidId(context) + ((String) null));
    }

    public static String getGaid(Context context) {
        try {
            return GoogleAdvertisingClient.getAdvertisingIdInfo(context).getId();
        } catch (Exception e) {
            MyLog.w("failure to get gaid:" + e.getMessage());
            return null;
        }
    }

    public static ArrayList<String> getIMEIList(Context context) {
        quicklyGetIMEI(context);
        quicklyGetSubIMEIS(context);
        if (TextUtils.isEmpty(sCachedIMEI)) {
            return null;
        }
        ArrayList<String> arrayList = new ArrayList<>();
        arrayList.add(sCachedIMEI);
        if (TextUtils.isEmpty(sCachedSubIMEIS)) {
            return arrayList;
        }
        for (String str : sCachedSubIMEIS.split(",")) {
            arrayList.add(str);
        }
        return arrayList;
    }

    private static int getImeiBlockCount() {
        int i = 0;
        if (Build.VERSION.SDK_INT < 29) {
            i = 10;
        }
        return i;
    }

    public static String getInstanceId(Context context) {
        String sHA1Digest;
        synchronized (DeviceInfo.class) {
            try {
                sHA1Digest = XMStringUtils.getSHA1Digest(getAndroidId(context) + ((String) null));
            } catch (Throwable th) {
                throw th;
            }
        }
        return sHA1Digest;
    }

    private static int getLocalVirtDevIdHashCode(String str) {
        if (TextUtils.isEmpty(str)) {
            return 0;
        }
        int iCharAt = 0;
        int length = str.length();
        for (int i = 0; i < length; i++) {
            iCharAt = (iCharAt * 31) + str.charAt(i);
        }
        return iCharAt;
    }

    public static String getMacAddress(Context context) {
        return "";
    }

    private static double getNum(double d) {
        int i = 1;
        while (true) {
            int i2 = i;
            if (i2 >= d) {
                return i2;
            }
            i = i2 << 1;
        }
    }

    static String getPhoneInfoHash() {
        String primaryAbi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "";
        return "35" + (Build.BOARD.length() % 10) + (Build.BRAND.length() % 10) + (primaryAbi.length() % 10) + (Build.DEVICE.length() % 10) + (Build.DISPLAY.length() % 10) + (Build.HOST.length() % 10) + (Build.MANUFACTURER.length() % 10) + (Build.MODEL.length() % 10) + (Build.PRODUCT.length() % 10);
    }

    public static int getRamFromProcMeminfo() {
        int i = 0;
        if (new File("/proc/meminfo").exists()) {
            BufferedReader bufferedReader = null;
            BufferedReader bufferedReader2 = null;
            try {
                try {
                    BufferedReader bufferedReader3 = new BufferedReader(new FileReader("/proc/meminfo"), 8192);
                    String line = bufferedReader3.readLine();
                    i = 0;
                    if (!TextUtils.isEmpty(line)) {
                        String[] strArrSplit = line.split("\\s+");
                        i = 0;
                        if (strArrSplit != null) {
                            i = 0;
                            if (strArrSplit.length >= 2) {
                                i = 0;
                                if (TextUtils.isDigitsOnly(strArrSplit[1])) {
                                    bufferedReader = bufferedReader3;
                                    bufferedReader2 = bufferedReader3;
                                    i = Integer.parseInt(strArrSplit[1]);
                                }
                            }
                        }
                    }
                    bufferedReader3.close();
                } catch (Exception e) {
                    i = 0;
                    if (bufferedReader2 != null) {
                        bufferedReader2.close();
                        i = 0;
                    }
                    return i;
                } catch (Throwable th) {
                    if (bufferedReader != null) {
                        try {
                            bufferedReader.close();
                        } catch (IOException e2) {
                        }
                    }
                    throw th;
                }
            } catch (IOException e3) {
                i = 0;
            }
        }
        return i;
    }

    public static String getRamSize() {
        return formatRamFromProcMeminfo(getRamFromProcMeminfo()) + "GB";
    }

    public static String getRamSizeOriginal() {
        return getRamFromProcMeminfo() + "KB";
    }

    public static String getRomSize() {
        return getNum(((getSize(Environment.getDataDirectory()) / 1024.0d) / 1024.0d) / 1024.0d) + "GB";
    }

    public static String getRomSizeOriginal() {
        return (getSize(Environment.getDataDirectory()) / 1024) + "KB";
    }

    public static String getSerialNum(Context context) {
        if (!canReadPhoneState(context)) {
            return null;
        }
        return (String) JavaCalls.callStaticMethod("android.os.Build", "getSerial", (Object[]) null);
    }

    public static String getSimOperatorName(Context context) {
        return ((TelephonyManager) context.getSystemService("phone")).getSimOperatorName();
    }

    public static String getSimpleDeviceId(Context context) {
        synchronized (DeviceInfo.class) {
            try {
                String str = sCachedSimpleDeviceId;
                if (str != null) {
                    return str;
                }
                String sHA1Digest = XMStringUtils.getSHA1Digest(getAndroidId(context) + getSerialNum(context));
                sCachedSimpleDeviceId = sHA1Digest;
                return sHA1Digest;
            } finally {
            }
        }
    }

    private static long getSize(File file) {
        StatFs statFs = new StatFs(file.getPath());
        return statFs.getBlockSizeLong() * statFs.getBlockCountLong();
    }

    public static int getSpaceId() {
        Object objCallStaticMethod;
        if (Build.VERSION.SDK_INT >= 17 && (objCallStaticMethod = JavaCalls.callStaticMethod("android.os.UserHandle", "myUserId", new Object[0])) != null) {
            return ((Integer) Integer.class.cast(objCallStaticMethod)).intValue();
        }
        return -1;
    }

    public static String getVirtDevId(Context context) {
        if (!isSupportVDevid(context)) {
            return null;
        }
        if (!TextUtils.isEmpty(sVirtDevId)) {
            return sVirtDevId;
        }
        String strFileToStr = IOUtils.fileToStr(new File(context.getFilesDir(), VIRTUAL_DEVICE_FILE));
        sVirtDevId = strFileToStr;
        if (!TextUtils.isEmpty(strFileToStr)) {
            return sVirtDevId;
        }
        FileLocker fileLocker = null;
        FileLocker fileLocker2 = null;
        try {
            try {
                File file = new File(new File(Environment.getExternalStorageDirectory(), VIRTUAL_DEVICE_DIR), VIRTUAL_DEVICE_FILE);
                FileLocker fileLockerLock = FileLocker.lock(context, file);
                sVirtDevId = "";
                String strFileToStr2 = IOUtils.fileToStr(file);
                if (strFileToStr2 != null) {
                    sVirtDevId = strFileToStr2;
                }
                fileLocker = fileLockerLock;
                fileLocker2 = fileLockerLock;
                String str = sVirtDevId;
                if (fileLockerLock != null) {
                    fileLockerLock.unlock();
                }
                return str;
            } catch (IOException e) {
                fileLocker = fileLocker2;
                MyLog.w("getVDevID failure.");
                if (fileLocker2 != null) {
                    fileLocker2.unlock();
                }
                return sVirtDevId;
            }
        } catch (Throwable th) {
            if (fileLocker != null) {
                fileLocker.unlock();
            }
            throw th;
        }
    }

    public static boolean isCharging(Context context) {
        Intent intentRegisterReceiver = context.registerReceiver(null, new IntentFilter("android.intent.action.BATTERY_CHANGED"));
        boolean z = false;
        if (intentRegisterReceiver != null) {
            int intExtra = intentRegisterReceiver.getIntExtra("status", -1);
            z = intExtra == 2 || intExtra == 5;
        }
        return z;
    }

    private static boolean isInvalidStr(String str) {
        boolean z = true;
        if (str == null) {
            return true;
        }
        String strTrim = str.trim();
        if (strTrim.length() != 0 && !strTrim.equalsIgnoreCase(STR_NULL) && !strTrim.equalsIgnoreCase(STR_UNKNOWN)) {
            z = false;
        }
        return z;
    }

    public static boolean isScreenOn(Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService("power");
        return powerManager == null || powerManager.isInteractive();
    }

    private static boolean isSupportVDevid(Context context) {
        if ((Build.VERSION.SDK_INT >= 29 && context.getApplicationInfo().targetSdkVersion >= 29) || !PermissionUtils.checkSelfPermission(context, PermissionUtils.writeExternalStorage) || MIUIUtils.isMIUI()) {
            return false;
        }
        return true;
    }

    public static String quicklyGetIMEI(Context context) {
        if (MIUIUtils.isGlobalRegion()) {
            return "";
        }
        String str = sCachedIMEI;
        if (str != null) {
            return str;
        }
        String deviceId = null;
        try {
            if (canReadPhoneState(context)) {
                String str2 = null;
                if (MIUIUtils.isMIUI()) {
                    Object objCallStaticMethod = JavaCalls.callStaticMethod("miui.telephony.TelephonyManager", "getDefault", new Object[0]);
                    str2 = null;
                    if (objCallStaticMethod != null) {
                        Object objCallMethod = JavaCalls.callMethod(objCallStaticMethod, "getMiuiDeviceId", new Object[0]);
                        str2 = null;
                        if (objCallMethod != null) {
                            str2 = null;
                            if (objCallMethod instanceof String) {
                                str2 = (String) String.class.cast(objCallMethod);
                            }
                        }
                    }
                }
                deviceId = str2;
                if (str2 == null) {
                    TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService("phone");
                    if (telephonyManager == null) {
                        deviceId = null;
                    } else if (1 == telephonyManager.getPhoneType()) {
                        deviceId = (String) JavaCalls.callMethod(telephonyManager, "getImei", (Object[]) null);
                    } else {
                        deviceId = str2;
                        if (2 == telephonyManager.getPhoneType()) {
                            deviceId = (String) JavaCalls.callMethod(telephonyManager, "getMeid", (Object[]) null);
                        }
                    }
                }
            }
            if (!verifyImei(deviceId)) {
                return "";
            }
            sCachedIMEI = deviceId;
            return deviceId;
        } catch (Throwable th) {
            MyLog.w("failure to get id:" + th);
            return null;
        }
    }

    public static String quicklyGetSubIMEIS(Context context) {
        if (MIUIUtils.isGlobalRegion() || Build.VERSION.SDK_INT < 22) {
            return "";
        }
        if (!TextUtils.isEmpty(sCachedSubIMEIS)) {
            return sCachedSubIMEIS;
        }
        quicklyGetIMEI(context);
        if (TextUtils.isEmpty(sCachedIMEI)) {
            return "";
        }
        try {
            if (!canReadPhoneState(context)) {
                return "";
            }
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService("phone");
            Integer num = (Integer) JavaCalls.callMethod(telephonyManager, "getPhoneCount", new Object[0]);
            if (num == null || num.intValue() <= 1) {
                return "";
            }
            String str = null;
            for (int i = 0; i < num.intValue(); i++) {
                if (Build.VERSION.SDK_INT < 26) {
                    str = (String) JavaCalls.callMethod(telephonyManager, "getDeviceId", Integer.valueOf(i));
                } else if (1 == telephonyManager.getPhoneType()) {
                    str = (String) JavaCalls.callMethod(telephonyManager, "getImei", Integer.valueOf(i));
                } else if (2 == telephonyManager.getPhoneType()) {
                    str = (String) JavaCalls.callMethod(telephonyManager, "getMeid", Integer.valueOf(i));
                }
                if (!TextUtils.isEmpty(str) && !TextUtils.equals(sCachedIMEI, str) && verifyImei(str)) {
                    sCachedSubIMEIS += str + ",";
                }
            }
            int length = sCachedSubIMEIS.length();
            if (length > 0) {
                sCachedSubIMEIS = sCachedSubIMEIS.substring(0, length - 1);
            }
            return sCachedSubIMEIS;
        } catch (Exception e) {
            MyLog.w("failure to get ids: " + e);
            return "";
        }
    }

    public static String quicklyGetSubIMEISMd5(Context context) {
        quicklyGetSubIMEIS(context);
        if (TextUtils.isEmpty(sCachedSubIMEIS)) {
            return "";
        }
        String str = "";
        for (String str2 : sCachedSubIMEIS.split(",")) {
            if (verifyImei(str2)) {
                str = str + XMStringUtils.getMd5Digest(str2) + ",";
            }
        }
        int length = str.length();
        String strSubstring = str;
        if (length > 0) {
            strSubstring = str.substring(0, length - 1);
        }
        return strSubstring;
    }

    private static String readLocalVirtDevId(Context context) {
        String str;
        String str2 = null;
        if (!isSupportVDevid(context)) {
            MyLog.w("not support read lvdd.");
            return null;
        }
        FileLocker fileLocker = null;
        FileLocker fileLockerLock = null;
        FileLocker fileLocker2 = null;
        try {
            try {
                File file = new File(new File(Environment.getExternalStorageDirectory(), VIRTUAL_DEVICE_DIR), VIRTUAL_DEVICE_LOCAL_FILE);
                if (file.exists() && file.isFile()) {
                    fileLockerLock = FileLocker.lock(context, file);
                    String strFileToStr = IOUtils.fileToStr(file);
                    str2 = null;
                    if (!TextUtils.isEmpty(strFileToStr)) {
                        String[] strArrSplit = strFileToStr.split(SPLIT_CHAR);
                        str2 = null;
                        if (strArrSplit.length == 2) {
                            String str3 = strArrSplit[0];
                            try {
                                str2 = null;
                                if (getLocalVirtDevIdHashCode(str3) == Integer.parseInt(strArrSplit[1])) {
                                    str2 = str3;
                                }
                            } catch (Exception e) {
                                str2 = null;
                            }
                        }
                    }
                    if (TextUtils.isEmpty(str2)) {
                        IOUtils.remove(file);
                        MyLog.i("lvdd content invalid, remove it.");
                    }
                } else {
                    MyLog.i("lvdf not exists");
                    str2 = null;
                }
                str = str2;
            } catch (IOException e2) {
                MyLog.w("get lvdd failure.");
                str = null;
                if (0 != 0) {
                    str2 = null;
                }
            }
            if (fileLockerLock != null) {
                fileLocker2 = fileLockerLock;
                fileLocker2.unlock();
                str = str2;
            }
            return str;
        } catch (Throwable th) {
            if (0 != 0) {
                fileLocker.unlock();
            }
            throw th;
        }
    }

    public static boolean startsWithDevPrefix(String str) {
        if (TextUtils.isEmpty(str)) {
            return false;
        }
        int i = 0;
        while (true) {
            String[] strArr = DEV_PREFIX_ARRAY;
            if (i >= strArr.length) {
                return false;
            }
            if (str.startsWith(strArr[i])) {
                return true;
            }
            i++;
        }
    }

    public static void updateVirtDevId(Context context, String str) {
        MyLog.v("update vdevid = " + str);
        if (TextUtils.isEmpty(str)) {
            return;
        }
        sVirtDevId = str;
        FileLocker fileLockerLock = null;
        FileLocker fileLocker = null;
        FileLocker fileLocker2 = null;
        try {
            try {
                if (isSupportVDevid(context)) {
                    File file = new File(Environment.getExternalStorageDirectory(), VIRTUAL_DEVICE_DIR);
                    if (file.exists() && file.isFile()) {
                        file.delete();
                    }
                    File file2 = new File(file, VIRTUAL_DEVICE_FILE);
                    fileLockerLock = FileLocker.lock(context, file2);
                    IOUtils.remove(file2);
                    IOUtils.strToFile(file2, sVirtDevId);
                }
                FileLocker fileLocker3 = fileLockerLock;
                FileLocker fileLocker4 = fileLockerLock;
                fileLocker = fileLockerLock;
                fileLocker2 = fileLockerLock;
                IOUtils.strToFile(new File(context.getFilesDir(), VIRTUAL_DEVICE_FILE), sVirtDevId);
            } catch (IOException e) {
                fileLocker = fileLocker2;
                MyLog.w("update vdevid failure.");
                if (fileLocker2 == null) {
                    return;
                }
            }
            if (fileLockerLock != null) {
                fileLocker2 = fileLockerLock;
                fileLocker2.unlock();
            }
        } catch (Throwable th) {
            if (fileLocker != null) {
                fileLocker.unlock();
            }
            throw th;
        }
    }

    private static boolean verifyImei(String str) {
        return !TextUtils.isEmpty(str) && str.length() <= 15 && str.length() >= 14 && XMStringUtils.isNumberAndLetter(str) && !XMStringUtils.isTheSameChars(str);
    }

    /* JADX WARN: Removed duplicated region for block: B:20:0x0042  */
    /* JADX WARN: Removed duplicated region for block: B:32:0x008a  */
    private static boolean verifyMac(String str) {
        if (TextUtils.isEmpty(str) || str.length() != 17) {
            return false;
        }
        if (!str.matches("^([A-Fa-f0-9]{2}[-,:]){5}[A-Fa-f0-9]{2}$")) {
            return false;
        }
        char charAt = str.charAt(0);
        if (charAt != '0' && charAt != 'f' && charAt != 'F') {
            return true;
        }
        for (int i = 1; i < str.length(); i++) {
            if (str.charAt(i) != charAt && str.charAt(i) != '-' && str.charAt(i) != ':') {
                return true;
            }
        }
        return false;
    }

    private static void writeLocalVirtDevIdIfNeed(Context context, String str) {
        FileLocker fileLockerLock;
        MyLog.v("write lvdd = " + str);
        if (TextUtils.isEmpty(str)) {
            return;
        }
        FileLocker fileLocker = null;
        try {
            try {
                if (isSupportVDevid(context)) {
                    File file = new File(Environment.getExternalStorageDirectory(), VIRTUAL_DEVICE_DIR);
                    if (file.exists() && file.isFile()) {
                        file.delete();
                    }
                    File file2 = new File(file, VIRTUAL_DEVICE_LOCAL_FILE);
                    if (file2.exists() && file2.isFile()) {
                        MyLog.i("vdr exists, not rewrite.");
                        if (0 != 0) {
                            throw new NullPointerException();
                        }
                        return;
                    }
                    fileLockerLock = FileLocker.lock(context, file2);
                    IOUtils.remove(file2);
                    StringBuilder sb = new StringBuilder();
                    sb.append(sCachedDeviceId);
                    sb.append(SPLIT_CHAR);
                    sb.append(getLocalVirtDevIdHashCode(sCachedDeviceId));
                    IOUtils.strToFile(file2, sb.toString());
                    MyLog.i("lvdd write succ.");
                } else {
                    MyLog.w("not support write lvdd.");
                    fileLockerLock = null;
                }
                if (fileLockerLock == null) {
                    return;
                }
            } catch (IOException e) {
                MyLog.w("write lvdd failure.");
                if (0 == 0) {
                    return;
                } else {
                    fileLockerLock = null;
                }
            }
            fileLockerLock.unlock();
        } catch (Throwable th) {
            if (0 != 0) {
                fileLocker.unlock();
            }
            throw th;
        }
    }
}
