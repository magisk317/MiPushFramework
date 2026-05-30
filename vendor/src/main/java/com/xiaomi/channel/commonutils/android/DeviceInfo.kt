package com.xiaomi.channel.commonutils.android

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.TextUtils
import com.xiaomi.channel.commonutils.file.FileLocker
import com.xiaomi.channel.commonutils.file.IOUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.msa.MsaIdManager
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.channel.commonutils.string.XMStringUtils
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.ArrayList

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/channel/commonutils/android/DeviceInfo.java
 */
object DeviceInfo {
    private const val COMMA_SEPARATOR = ","
    private const val DEFAULT_IMEI_BLOCK_COUNT = 10
    private const val MAX_VDEVID_LEN = 128
    private const val RULE_1_IMIE = 1
    private const val RULE_2_UDID = 2
    private const val RULE_3_SDCARD = 3
    private const val RULE_4_OAID = 4
    private const val RULE_5_ANDROIDID = 5
    private const val STR_NULL = "null"
    private const val STR_UNKNOWN = "unknown"
    const val VIRTUAL_DEVICE_DIR = "/.vdevdir/"
    private const val VIRTUAL_DEVICE_FILE = ".vdevid"
    private const val VIRTUAL_DEVICE_LOCAL_FILE = ".vdevidlocal"
    private var sCachedIMEI: String? = null
    private var sCachedSubIMEIS = ""
    private var sCachedDeviceId: String? = null
    private var sCachedSimpleDeviceId: String? = null
    private val SPLIT_CHAR = (2.toChar()).toString()
    const val OLD_DEVICE_PREFIX = "a-"
    private val DEV_PREFIX_ARRAY = arrayOf("--", OLD_DEVICE_PREFIX, "u-", "v-", "o-", "g-")
    private var sVirtDevId: String? = null
    @Volatile
    private var sVirtDevIDChecked = false

    @JvmStatic
    fun blockingGetIMEI(context: Context): String? {
        var imei = quicklyGetIMEI(context)
        var count = getImeiBlockCount()
        while (imei == null && count > 0) {
            try {
                Thread.sleep(500L)
            } catch (_: InterruptedException) {
            }
            imei = quicklyGetIMEI(context)
            count--
        }
        return imei
    }

    private fun blockingGetIMEIWhenDeviceRegister(context: Context): String? {
        var imei = quicklyGetIMEI(context)
        var count = getImeiBlockCount()
        while (TextUtils.isEmpty(imei) && count > 0) {
            try {
                Thread.sleep(500L)
            } catch (_: InterruptedException) {
            }
            imei = quicklyGetIMEI(context)
            count--
        }
        return imei
    }

    @JvmStatic
    fun blockingGetSubIMEIS(context: Context): String? {
        var subImei = quicklyGetSubIMEIS(context)
        var count = getImeiBlockCount()
        while (subImei == null && count > 0) {
            try {
                Thread.sleep(500L)
            } catch (_: InterruptedException) {
            }
            subImei = quicklyGetSubIMEIS(context)
            count--
        }
        return subImei
    }

    @JvmStatic
    fun blockingGetSubIMEISMd5(context: Context): String? {
        var subImeiMd5 = quicklyGetSubIMEISMd5(context)
        var count = getImeiBlockCount()
        while (subImeiMd5 == null && count > 0) {
            try {
                Thread.sleep(500L)
            } catch (_: InterruptedException) {
            }
            subImeiMd5 = quicklyGetSubIMEISMd5(context)
            count--
        }
        return subImeiMd5
    }

    @JvmStatic
    fun blockinggetIMEIList(context: Context): ArrayList<String>? {
        var imeiList = getIMEIList(context)
        var count = getImeiBlockCount()
        while (imeiList == null && count > 0) {
            try {
                Thread.sleep(500L)
            } catch (_: InterruptedException) {
            }
            imeiList = getIMEIList(context)
            count--
        }
        return imeiList
    }

    private fun canReadPhoneState(context: Context): Boolean {
        val packageName = context.packageName
        return context.packageManager.checkPermission(PermissionUtils.readPhoneState, packageName) == 0 ||
            context.packageManager.checkPermission("android.permission.READ_PRIVILEGED_PHONE_STATE", packageName) == 0
    }

    @JvmStatic
    fun checkVirtDevId(context: Context): String? {
        if (!isSupportVDevid(context) || sVirtDevIDChecked) {
            return null
        }
        sVirtDevIDChecked = true
        val localValue = IOUtils.fileToStr(File(context.filesDir, VIRTUAL_DEVICE_FILE))
        var locker: FileLocker? = null
        var externalValue: String? = null
        try {
            try {
                val file = File(File(Environment.getExternalStorageDirectory(), VIRTUAL_DEVICE_DIR), VIRTUAL_DEVICE_FILE)
                locker = FileLocker.lock(context, file)
                externalValue = IOUtils.fileToStr(file)
            } catch (_: IOException) {
                MyLog.w("check id failure.")
            } finally {
                locker?.unlock()
                locker = null
            }
            if (TextUtils.isEmpty(localValue)) {
                MyLog.w("empty local vid")
                return "F*"
            }
            sVirtDevId = localValue
            if (TextUtils.isEmpty(externalValue) || externalValue!!.length > MAX_VDEVID_LEN) {
                MyLog.w("recover vid :$externalValue")
                updateVirtDevId(context, localValue)
            } else if (!TextUtils.equals(localValue, externalValue)) {
                MyLog.w("vid changed, need sync")
                return externalValue
            }
            MyLog.v("vdevid = $sVirtDevId $externalValue")
            return null
        } finally {
            locker?.unlock()
        }
    }

    @JvmStatic
    fun fillLocalVirtDevId(context: Context?, map: MutableMap<String, String>?) {
        if (map == null || context == null) {
            return
        }
        val localVirtDevId = readLocalVirtDevId(context)
        if (TextUtils.isEmpty(localVirtDevId)) {
            return
        }
        map["local_virt_devid"] = localVirtDevId!!
    }

    private fun formatRamFromProcMeminfo(i: Int): Float {
        val value = ((((((102400 + i) / 524288) + 1) * 512) * 1024) / 1024.0f) / 1024.0f
        return if (value > 0.5) kotlin.math.ceil(value.toDouble()).toFloat() else value
    }

    @JvmStatic
    fun getAndroidId(context: Context): String? {
        return try {
            Settings.Secure.getString(context.contentResolver, "android_id")
        } catch (throwable: Throwable) {
            MyLog.w("failure to get androidId: " + throwable.message)
            null
        }
    }

    private fun getDevPrefix(i: Int): String {
        return if (i > 0 && i < DEV_PREFIX_ARRAY.size) DEV_PREFIX_ARRAY[i] else DEV_PREFIX_ARRAY[0]
    }

    @JvmStatic
    fun getDeviceId(context: Context, z: Boolean): String {
        if (sCachedDeviceId == null) {
            val androidId = getAndroidId(context)
            var source = ""
            var rule = RULE_1_IMIE
            run {
                val imei = if (MIUIUtils.isGlobalRegion()) "" else if (z) blockingGetIMEI(context) else blockingGetIMEIWhenDeviceRegister(context)
                val serialNum = getSerialNum(context)
                if (Build.VERSION.SDK_INT < 26 || !isInvalidStr(imei) || !isInvalidStr(serialNum)) {
                    source = imei + androidId + serialNum
                    rule = RULE_1_IMIE
                    return@run
                }
                val udid = MsaIdManager.getInstance(context).getUDID()
                if (!TextUtils.isEmpty(udid)) {
                    source = udid + androidId
                    rule = RULE_2_UDID
                    return@run
                }
                source = readLocalVirtDevId(context).orEmpty()
                if (!TextUtils.isEmpty(source)) {
                    rule = RULE_3_SDCARD
                    return@run
                }
                val oaid = MsaIdManager.getInstance(context).getOAID()
                if (!TextUtils.isEmpty(oaid)) {
                    source = oaid
                    rule = RULE_4_OAID
                    return@run
                }
                source = androidId.orEmpty()
                rule = RULE_5_ANDROIDID
            }
            MyLog.i("devid rule select:$rule")
            sCachedDeviceId = if (rule == RULE_3_SDCARD) {
                source
            } else {
                getDevPrefix(rule) + XMStringUtils.getSHA1Digest(source)
            }
            writeLocalVirtDevIdIfNeed(context, sCachedDeviceId)
        }
        return sCachedDeviceId.orEmpty()
    }

    @JvmStatic
    fun getDeviceId1(context: Context): String {
        return OLD_DEVICE_PREFIX + XMStringUtils.getSHA1Digest(null.toString() + getAndroidId(context) + null.toString())
    }

    @JvmStatic
    fun getGaid(context: Context): String? {
        return try {
            GoogleAdvertisingClient.getAdvertisingIdInfo(context).getId()
        } catch (e: Exception) {
            MyLog.w("failure to get gaid:" + e.message)
            null
        }
    }

    @JvmStatic
    fun getIMEIList(context: Context): ArrayList<String>? {
        quicklyGetIMEI(context)
        quicklyGetSubIMEIS(context)
        if (TextUtils.isEmpty(sCachedIMEI)) {
            return null
        }
        return ArrayList<String>().apply {
            add(sCachedIMEI!!)
            if (!TextUtils.isEmpty(sCachedSubIMEIS)) {
                sCachedSubIMEIS.split(COMMA_SEPARATOR).forEach { add(it) }
            }
        }
    }

    private fun getImeiBlockCount(): Int {
        return if (Build.VERSION.SDK_INT < 29) DEFAULT_IMEI_BLOCK_COUNT else 0
    }

    @JvmStatic
    @Synchronized
    fun getInstanceId(context: Context): String {
        return XMStringUtils.getSHA1Digest(getAndroidId(context) + null.toString()).orEmpty()
    }

    private fun getLocalVirtDevIdHashCode(str: String?): Int {
        if (TextUtils.isEmpty(str)) {
            return 0
        }
        var hash = 0
        for (i in str!!.indices) {
            hash = hash * 31 + str[i].code
        }
        return hash
    }

    @JvmStatic
    fun getMacAddress(context: Context): String {
        return ""
    }

    private fun getNum(d: Double): Double {
        var value = 1
        while (value < d) {
            value = value shl 1
        }
        return value.toDouble()
    }

    fun getPhoneInfoHash(): String {
        val primaryAbi = if (Build.SUPPORTED_ABIS.isNotEmpty()) Build.SUPPORTED_ABIS[0] else ""
        return "35" +
            (Build.BOARD.length % 10) +
            (Build.BRAND.length % 10) +
            (primaryAbi.length % 10) +
            (Build.DEVICE.length % 10) +
            (Build.DISPLAY.length % 10) +
            (Build.HOST.length % 10) +
            (Build.MANUFACTURER.length % 10) +
            (Build.MODEL.length % 10) +
            (Build.PRODUCT.length % 10)
    }

    @JvmStatic
    fun getRamFromProcMeminfo(): Int {
        val file = File("/proc/meminfo")
        if (!file.exists()) {
            return 0
        }
        var reader: BufferedReader? = null
        return try {
            reader = BufferedReader(FileReader(file), 8192)
            val line = reader.readLine()
            if (TextUtils.isEmpty(line)) {
                0
            } else {
                val parts = line.split("\\s+".toRegex())
                if (parts.size >= 2 && TextUtils.isDigitsOnly(parts[1])) {
                    parts[1].toInt()
                } else {
                    0
                }
            }
        } catch (_: Exception) {
            0
        } finally {
            try {
                reader?.close()
            } catch (_: IOException) {
            }
        }
    }

    @JvmStatic
    fun getRamSize(): String {
        return formatRamFromProcMeminfo(getRamFromProcMeminfo()).toString() + "GB"
    }

    @JvmStatic
    fun getRamSizeOriginal(): String {
        return getRamFromProcMeminfo().toString() + "KB"
    }

    @JvmStatic
    fun getRomSize(): String {
        return getNum(getSize(Environment.getDataDirectory()) / 1024.0 / 1024.0 / 1024.0).toInt().toString() + "GB"
    }

    @JvmStatic
    fun getRomSizeOriginal(): String {
        return (getSize(Environment.getDataDirectory()) / 1024).toString() + "KB"
    }

    @JvmStatic
    fun getSerialNum(context: Context): String? {
        if (!canReadPhoneState(context)) {
            return null
        }
        return JavaCalls.callStaticMethod("android.os.Build", "getSerial", *(null as Array<Any?>? ?: emptyArray())) as? String
    }

    @JvmStatic
    fun getSimOperatorName(context: Context): String? {
        return (context.getSystemService("phone") as TelephonyManager).simOperatorName
    }

    @JvmStatic
    @Synchronized
    fun getSimpleDeviceId(context: Context): String {
        if (sCachedSimpleDeviceId != null) {
            return sCachedSimpleDeviceId.orEmpty()
        }
        val value = XMStringUtils.getSHA1Digest(getAndroidId(context) + getSerialNum(context)).orEmpty()
        sCachedSimpleDeviceId = value
        return value
    }

    private fun getSize(file: File): Long {
        val statFs = StatFs(file.path)
        return statFs.blockSizeLong * statFs.blockCountLong
    }

    @JvmStatic
    fun getSpaceId(): Int {
        val myUserId = if (Build.VERSION.SDK_INT >= 17) {
            JavaCalls.callStaticMethod("android.os.UserHandle", "myUserId")
        } else {
            null
        }
        return (myUserId as? Number)?.toInt() ?: -1
    }

    @JvmStatic
    fun getVirtDevId(context: Context): String? {
        if (!isSupportVDevid(context)) {
            return null
        }
        if (!TextUtils.isEmpty(sVirtDevId)) {
            return sVirtDevId
        }
        sVirtDevId = IOUtils.fileToStr(File(context.filesDir, VIRTUAL_DEVICE_FILE))
        if (!TextUtils.isEmpty(sVirtDevId)) {
            return sVirtDevId
        }
        var locker: FileLocker? = null
        return try {
            try {
                val file = File(File(Environment.getExternalStorageDirectory(), VIRTUAL_DEVICE_DIR), VIRTUAL_DEVICE_FILE)
                locker = FileLocker.lock(context, file)
                sVirtDevId = IOUtils.fileToStr(file) ?: ""
                sVirtDevId
            } catch (_: IOException) {
                MyLog.w("getVDevID failure.")
                sVirtDevId
            } finally {
                locker?.unlock()
            }
        } finally {
            locker?.unlock()
        }
    }

    @JvmStatic
    fun isCharging(context: Context): Boolean {
        val batteryIntent = context.registerReceiver(null, IntentFilter("android.intent.action.BATTERY_CHANGED"))
        return if (batteryIntent != null) {
            val status = batteryIntent.getIntExtra("status", -1)
            status == 2 || status == 5
        } else {
            false
        }
    }

    private fun isInvalidStr(str: String?): Boolean {
        if (str == null) {
            return true
        }
        val trimmed = str.trim()
        return trimmed.isEmpty() || trimmed.equals(STR_NULL, true) || trimmed.equals(STR_UNKNOWN, true)
    }

    @JvmStatic
    fun isScreenOn(context: Context): Boolean {
        val powerManager = context.getSystemService("power") as PowerManager?
        return powerManager == null || powerManager.isInteractive
    }

    private fun isSupportVDevid(context: Context): Boolean {
        return !(
            (Build.VERSION.SDK_INT >= 29 && context.applicationInfo.targetSdkVersion >= 29) ||
                !PermissionUtils.checkSelfPermission(context, PermissionUtils.writeExternalStorage) ||
                MIUIUtils.isMIUI()
            )
    }

    @JvmStatic
    fun quicklyGetIMEI(context: Context): String? {
        if (MIUIUtils.isGlobalRegion()) {
            return ""
        }
        val cached = sCachedIMEI
        if (cached != null) {
            return cached
        }
        var deviceId: String? = null
        return try {
            if (canReadPhoneState(context)) {
                var miuiDeviceId: String? = null
                if (MIUIUtils.isMIUI()) {
                    val telephony = JavaCalls.callStaticMethod("miui.telephony.TelephonyManager", "getDefault")
                    val result = if (telephony != null) JavaCalls.callMethod(telephony, "getMiuiDeviceId") else null
                    if (result is String) {
                        miuiDeviceId = result
                    }
                }
                deviceId = miuiDeviceId
                if (miuiDeviceId == null) {
                    val telephonyManager = context.getSystemService("phone") as TelephonyManager?
                    deviceId = when {
                        telephonyManager == null -> null
                        telephonyManager.phoneType == 1 -> JavaCalls.callMethod(telephonyManager, "getImei") as? String
                        telephonyManager.phoneType == 2 -> JavaCalls.callMethod(telephonyManager, "getMeid") as? String
                        else -> miuiDeviceId
                    }
                }
            }
            if (!verifyImei(deviceId)) {
                return ""
            }
            sCachedIMEI = deviceId
            deviceId
        } catch (throwable: Throwable) {
            MyLog.w("failure to get id:$throwable")
            null
        }
    }

    @JvmStatic
    fun quicklyGetSubIMEIS(context: Context): String? {
        if (MIUIUtils.isGlobalRegion() || Build.VERSION.SDK_INT < 22) {
            return ""
        }
        if (!TextUtils.isEmpty(sCachedSubIMEIS)) {
            return sCachedSubIMEIS
        }
        quicklyGetIMEI(context)
        if (TextUtils.isEmpty(sCachedIMEI)) {
            return ""
        }
        return try {
            if (!canReadPhoneState(context)) {
                return ""
            }
            val telephonyManager = context.getSystemService("phone") as TelephonyManager
            val phoneCount = JavaCalls.callMethod(telephonyManager, "getPhoneCount") as? Int
            if (phoneCount == null || phoneCount <= 1) {
                return ""
            }
            var subId: String?
            for (i in 0 until phoneCount) {
                subId = when {
                    Build.VERSION.SDK_INT < 26 -> JavaCalls.callMethod(telephonyManager, "getDeviceId", i) as? String
                    telephonyManager.phoneType == 1 -> JavaCalls.callMethod(telephonyManager, "getImei", i) as? String
                    telephonyManager.phoneType == 2 -> JavaCalls.callMethod(telephonyManager, "getMeid", i) as? String
                    else -> null
                }
                if (!TextUtils.isEmpty(subId) && !TextUtils.equals(sCachedIMEI, subId) && verifyImei(subId)) {
                    sCachedSubIMEIS += "$subId,"
                }
            }
            val length = sCachedSubIMEIS.length
            if (length > 0) {
                sCachedSubIMEIS = sCachedSubIMEIS.substring(0, length - 1)
            }
            sCachedSubIMEIS
        } catch (e: Exception) {
            MyLog.w("failure to get ids: $e")
            ""
        }
    }

    @JvmStatic
    fun quicklyGetSubIMEISMd5(context: Context): String? {
        quicklyGetSubIMEIS(context)
        if (TextUtils.isEmpty(sCachedSubIMEIS)) {
            return ""
        }
        var value = ""
        for (subImei in sCachedSubIMEIS.split(COMMA_SEPARATOR)) {
            if (verifyImei(subImei)) {
                value += XMStringUtils.getMd5Digest(subImei) + COMMA_SEPARATOR
            }
        }
        return if (value.isNotEmpty()) value.substring(0, value.length - 1) else value
    }

    private fun readLocalVirtDevId(context: Context): String? {
        if (!isSupportVDevid(context)) {
            MyLog.w("not support read lvdd.")
            return null
        }
        var locker: FileLocker? = null
        return try {
            val file = File(File(Environment.getExternalStorageDirectory(), VIRTUAL_DEVICE_DIR), VIRTUAL_DEVICE_LOCAL_FILE)
            val value = if (file.exists() && file.isFile) {
                locker = FileLocker.lock(context, file)
                val content = IOUtils.fileToStr(file)
                var result: String? = null
                if (!TextUtils.isEmpty(content)) {
                    val parts = content!!.split(SPLIT_CHAR)
                    if (parts.size == 2) {
                        val body = parts[0]
                        result = try {
                            if (getLocalVirtDevIdHashCode(body) == parts[1].toInt()) body else null
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                if (TextUtils.isEmpty(result)) {
                    IOUtils.remove(file)
                    MyLog.i("lvdd content invalid, remove it.")
                }
                result
            } else {
                MyLog.i("lvdf not exists")
                null
            }
            value
        } catch (_: IOException) {
            MyLog.w("get lvdd failure.")
            null
        } finally {
            locker?.unlock()
        }
    }

    @JvmStatic
    fun startsWithDevPrefix(str: String?): Boolean {
        if (TextUtils.isEmpty(str)) {
            return false
        }
        return DEV_PREFIX_ARRAY.any { str!!.startsWith(it) }
    }

    @JvmStatic
    fun updateVirtDevId(context: Context, str: String?) {
        MyLog.v("update vdevid = $str")
        if (TextUtils.isEmpty(str)) {
            return
        }
        sVirtDevId = str
        var locker: FileLocker? = null
        try {
            if (isSupportVDevid(context)) {
                val dir = File(Environment.getExternalStorageDirectory(), VIRTUAL_DEVICE_DIR)
                if (dir.exists() && dir.isFile) {
                    dir.delete()
                }
                val file = File(dir, VIRTUAL_DEVICE_FILE)
                locker = FileLocker.lock(context, file)
                IOUtils.remove(file)
                IOUtils.strToFile(file, sVirtDevId)
            }
            IOUtils.strToFile(File(context.filesDir, VIRTUAL_DEVICE_FILE), sVirtDevId)
        } catch (_: IOException) {
            MyLog.w("update vdevid failure.")
        } finally {
            locker?.unlock()
        }
    }

    private fun verifyImei(str: String?): Boolean {
        return !TextUtils.isEmpty(str) &&
            str!!.length <= 15 &&
            str.length >= 14 &&
            XMStringUtils.isNumberAndLetter(str) &&
            !XMStringUtils.isTheSameChars(str)
    }

    private fun verifyMac(str: String?): Boolean {
        if (TextUtils.isEmpty(str) || str!!.length != 17) {
            return false
        }
        if (!str.matches("^([A-Fa-f0-9]{2}[-,:]){5}[A-Fa-f0-9]{2}$".toRegex())) {
            return false
        }
        val firstChar = str[0]
        if (firstChar != '0' && firstChar != 'f' && firstChar != 'F') {
            return true
        }
        for (i in 1 until str.length) {
            if (str[i] != firstChar && str[i] != '-' && str[i] != ':') {
                return true
            }
        }
        return false
    }

    private fun writeLocalVirtDevIdIfNeed(context: Context, str: String?) {
        MyLog.v("write lvdd = $str")
        if (TextUtils.isEmpty(str)) {
            return
        }
        var locker: FileLocker? = null
        try {
            if (isSupportVDevid(context)) {
                val dir = File(Environment.getExternalStorageDirectory(), VIRTUAL_DEVICE_DIR)
                if (dir.exists() && dir.isFile) {
                    dir.delete()
                }
                val file = File(dir, VIRTUAL_DEVICE_LOCAL_FILE)
                if (file.exists() && file.isFile) {
                    MyLog.i("vdr exists, not rewrite.")
                    return
                }
                locker = FileLocker.lock(context, file)
                IOUtils.remove(file)
                val content = StringBuilder().apply {
                    append(sCachedDeviceId)
                    append(SPLIT_CHAR)
                    append(getLocalVirtDevIdHashCode(sCachedDeviceId))
                }.toString()
                IOUtils.strToFile(file, content)
                MyLog.i("lvdd write succ.")
            } else {
                MyLog.w("not support write lvdd.")
            }
        } catch (_: IOException) {
            MyLog.w("write lvdd failure.")
        } finally {
            locker?.unlock()
        }
    }
}
