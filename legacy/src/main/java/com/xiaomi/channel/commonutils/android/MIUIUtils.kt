package com.xiaomi.channel.commonutils.android

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import java.util.Locale

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/channel/commonutils/android/MIUIUtils.java
 */
object MIUIUtils {
    private const val ANDROID_SYSTEM_PROPERTIES = "android.os.SystemProperties"
    private const val XMSF_PACKAGE_NAME = "com.xiaomi.xmsf"
    const val IS_MIUI = 1
    private const val KEY_MIUI_VERSION_CODE = "ro.miui.ui.version.code"
    private const val KEY_MIUI_VERSION_NAME = "ro.miui.ui.version.name"
    const val MIUI_OS_VERSION_ALPHA = "alpha"
    const val MIUI_OS_VERSION_DEVELOPMENT = "development"
    const val MIUI_OS_VERSION_STABLE = "stable"
    const val NOT_MIUI = 2
    const val UNKNOWN = 0

    @Volatile
    private var isMIUI = UNKNOWN

    @Volatile
    private var isInXMS = -1
    private var locale2RegionMap: Map<String, Region>? = null

    private fun findServerRegionByLocale(value: String?): Region? {
        initLocale2RegionMap()
        return if (value == null) {
            null
        } else {
            locale2RegionMap?.get(value.uppercase(Locale.getDefault()))
        }
    }

    @JvmStatic
    fun getCountryCode(): String {
        val regionKeys = arrayOf(
            "ro.miui.region",
            "persist.sys.oppo.region",
            "ro.oppo.regionmark",
            "ro.hw.country",
            "ro.csc.countryiso_code",
            "ro.product.country.region",
            "gsm.vivo.countrycode",
            "persist.sys.oem.region",
            "ro.product.locale.region",
            "persist.sys.country",
        )
        var country = ""
        for (key in regionKeys) {
            country = SystemProperties.get(key, "")
            if (!TextUtils.isEmpty(country)) {
                break
            }
        }
        if (!TextUtils.isEmpty(country)) {
            MyLog.w("get region from system, region = $country")
        }
        if (TextUtils.isEmpty(country)) {
            country = Locale.getDefault().country
            MyLog.w("locale.default.country = $country")
        }
        return country
    }

    @JvmStatic
    @Synchronized
    fun getIsMIUI(): Int {
        if (isMIUI == UNKNOWN) {
            try {
                isMIUI = if (
                    !TextUtils.isEmpty(getProperty(KEY_MIUI_VERSION_CODE)) ||
                    !TextUtils.isEmpty(getProperty(KEY_MIUI_VERSION_NAME))
                ) {
                    IS_MIUI
                } else {
                    NOT_MIUI
                }
            } catch (throwable: Throwable) {
                MyLog.e("get isMIUI failed", throwable)
                isMIUI = UNKNOWN
            }
            MyLog.i("isMIUI's value is: $isMIUI")
        }
        return isMIUI
    }

    @JvmStatic
    @Synchronized
    fun getMIUIType(): String {
        val miuiType = SystemUtils.getMIUIType()
        return if (!isMIUI() || miuiType <= 0) {
            ""
        } else if (miuiType < 2) {
            MIUI_OS_VERSION_ALPHA
        } else if (miuiType < 3) {
            MIUI_OS_VERSION_DEVELOPMENT
        } else {
            MIUI_OS_VERSION_STABLE
        }
    }

    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun getMiuiVersionCode(context: Context): Int {
        val property = getProperty(KEY_MIUI_VERSION_CODE)
        return if (TextUtils.isEmpty(property) || !TextUtils.isDigitsOnly(property)) {
            0
        } else {
            property!!.toInt()
        }
    }

    @JvmStatic
    fun getProperty(str: String): String? {
        return try {
            try {
                JavaCalls.callStaticMethod(ANDROID_SYSTEM_PROPERTIES, "get", str, "") as? String
            } catch (e: Exception) {
                MyLog.e("fail to get property. $e")
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    @JvmStatic
    fun getRegion(str: String?): Region {
        return findServerRegionByLocale(str) ?: Region.Global
    }

    private fun initLocale2RegionMap() {
        if (locale2RegionMap != null) {
            return
        }
        locale2RegionMap = hashMapOf(
            "CN" to Region.China,
            "FI" to Region.Europe,
            "SE" to Region.Europe,
            "NO" to Region.Europe,
            "FO" to Region.Europe,
            "EE" to Region.Europe,
            "LV" to Region.Europe,
            "LT" to Region.Europe,
            "BY" to Region.Europe,
            "MD" to Region.Europe,
            "UA" to Region.Europe,
            "PL" to Region.Europe,
            "CZ" to Region.Europe,
            "SK" to Region.Europe,
            "HU" to Region.Europe,
            "DE" to Region.Europe,
            "AT" to Region.Europe,
            "CH" to Region.Europe,
            "LI" to Region.Europe,
            "GB" to Region.Europe,
            "IE" to Region.Europe,
            "NL" to Region.Europe,
            "BE" to Region.Europe,
            "LU" to Region.Europe,
            "FR" to Region.Europe,
            "RO" to Region.Europe,
            "BG" to Region.Europe,
            "RS" to Region.Europe,
            "MK" to Region.Europe,
            "AL" to Region.Europe,
            "GR" to Region.Europe,
            "SI" to Region.Europe,
            "HR" to Region.Europe,
            "IT" to Region.Europe,
            "SM" to Region.Europe,
            "MT" to Region.Europe,
            "ES" to Region.Europe,
            "PT" to Region.Europe,
            "AD" to Region.Europe,
            "CY" to Region.Europe,
            "DK" to Region.Europe,
            "RU" to Region.Russia,
            "IN" to Region.India,
        )
    }

    @JvmStatic
    fun isGlobalRegion(): Boolean {
        return !Region.China.name.equals(getRegion(getCountryCode()).name, ignoreCase = true)
    }

    @JvmStatic
    @Synchronized
    fun isMIUI(): Boolean {
        return getIsMIUI() == IS_MIUI
    }

    @JvmStatic
    @Synchronized
    fun isNotMIUI(): Boolean {
        return getIsMIUI() == NOT_MIUI
    }

    @JvmStatic
    fun isXMS(): Boolean {
        if (isInXMS < 0) {
            val result = JavaCalls.callStaticMethod("miui.external.SdkHelper", "isMiuiSystem")
            isInXMS = 0
            if (result is Boolean && !result) {
                isInXMS = 1
            }
        }
        return isInXMS > 0
    }

    @JvmStatic
    fun isXMSF(context: Context?): Boolean {
        return context != null && isXMSF(context.packageName)
    }

    @JvmStatic
    fun isXMSF(str: String?): Boolean {
        return XMSF_PACKAGE_NAME == str
    }
}
