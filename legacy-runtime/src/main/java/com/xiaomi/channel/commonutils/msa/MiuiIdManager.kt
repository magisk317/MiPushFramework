package com.xiaomi.channel.commonutils.msa

import android.content.Context
import android.provider.Settings
import com.xiaomi.channel.commonutils.android.SystemUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import java.lang.reflect.Method

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/channel/commonutils/msa/MiuiIdManager.java
 */
class MiuiIdManager(private val mContext: Context) : IdManager {
    private var mClass: Class<*>? = null
    private var mIdProivderImpl: Any? = null
    private var mGetUDID: Method? = null
    private var mGetOAID: Method? = null
    private var mGetVAID: Method? = null
    private var mGetAAID: Method? = null

    init {
        loadClass(mContext)
    }

    private fun invokeMethod(context: Context, method: Method?): String? {
        val impl = mIdProivderImpl
        if (impl == null || method == null) {
            return null
        }
        return try {
            method.invoke(impl, context) as? String
        } catch (e: Exception) {
            MyLog.e("miui invoke error", e)
            null
        }
    }

    private fun loadClass(context: Context) {
        try {
            val cls = SystemUtils.loadClass(context, CLASS_NAME)
            mClass = cls
            mIdProivderImpl = cls.getDeclaredConstructor().newInstance()
            mGetUDID = cls.getMethod(METHOD_UDID, Context::class.java)
            mGetOAID = cls.getMethod(METHOD_OAID, Context::class.java)
            mGetVAID = cls.getMethod(METHOD_VAID, Context::class.java)
            mGetAAID = cls.getMethod(METHOD_AAID, Context::class.java)
        } catch (e: Exception) {
            MyLog.e("miui load class error", e)
        }
    }

    override fun getAAID(): String? = invokeMethod(mContext, mGetAAID)

    override fun getOAID(): String? = invokeMethod(mContext, mGetOAID)

    override fun getUDID(): String? = null

    override fun getVAID(): String? = invokeMethod(mContext, mGetVAID)

    override fun isAllowOAID(): Boolean {
        return try {
            Settings.Secure.getInt(mContext.contentResolver, KEY_ALLOW_OAID_USED, 1) == 1
        } catch (e: Exception) {
            MyLog.w("miui is allow oaid error$e")
            false
        }
    }

    override fun isSupported(): Boolean = mClass != null && mIdProivderImpl != null

    companion object {
        private const val CLASS_NAME = "com.android.id.impl.IdProviderImpl"
        private const val KEY_ALLOW_OAID_USED = "allow_oaid_used"
        private const val METHOD_AAID = "getAAID"
        private const val METHOD_OAID = "getOAID"
        private const val METHOD_UDID = "getUDID"
        private const val METHOD_VAID = "getVAID"
        private const val MIUI_PLATFORM_PKG = "com.xiaomi.xmsf"

        @JvmStatic
        fun isMiuiPhone(context: Context): Boolean = MIUI_PLATFORM_PKG == context.packageName
    }
}
