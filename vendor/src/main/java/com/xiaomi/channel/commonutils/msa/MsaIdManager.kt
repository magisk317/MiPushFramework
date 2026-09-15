package com.xiaomi.channel.commonutils.msa

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog

/*
 */
class MsaIdManager private constructor(context: Context) : IdManager {
    private val mIdManager: IdManager = MsaIdFactory.instance(context)
    private val mManagerType: Int = MsaIdFactory.sType

    init {
        MyLog.w("create id manager is: $mManagerType")
    }

    private fun substringShort(str: String?): String {
        if (TextUtils.isEmpty(str)) {
            return ""
        }
        return if (str!!.length > SHORT_STRING_LENGTH) str.substring(str.length - SHORT_STRING_LENGTH) else str
    }

    private fun trim(str: String?): String = str ?: ""

    fun fillData(map: MutableMap<String, String>?) {
        if (map == null) {
            return
        }
        val udid = getUDID()
        if (!TextUtils.isEmpty(udid)) {
            map[KEY_UDID] = udid
        }
        val oaid = getOAID()
        if (!TextUtils.isEmpty(oaid)) {
            map[KEY_OAID] = oaid
        }
        val vaid = getVAID()
        if (!TextUtils.isEmpty(vaid)) {
            map[KEY_VAID] = vaid
        }
        val aaid = getAAID()
        if (!TextUtils.isEmpty(aaid)) {
            map[KEY_AAID] = aaid
        }
        map[KEY_TYPE] = mManagerType.toString()
    }

    override fun getAAID(): String = trim(mIdManager.getAAID())

    override fun getOAID(): String = trim(mIdManager.getOAID())

    override fun getUDID(): String = trim(mIdManager.getUDID())

    override fun getVAID(): String = trim(mIdManager.getVAID())

    fun init() {
    }

    override fun isAllowOAID(): Boolean = mIdManager.isAllowOAID()

    override fun isSupported(): Boolean = mIdManager.isSupported()

    fun toShortString(): String {
        return "t:$mManagerType s:${isSupported()} d:${substringShort(getUDID())} | " +
            "${substringShort(getOAID())} | ${substringShort(getVAID())} | ${substringShort(getAAID())}"
    }

    companion object {
        const val KEY_AAID = "aaid"
        const val KEY_OAID = "oaid"
        const val KEY_TYPE = "oaid_type"
        const val KEY_UDID = "udid"
        const val KEY_VAID = "vaid"
        private const val SHORT_STRING_LENGTH = 5

        @Volatile
        private var sInstance: MsaIdManager? = null

        @JvmStatic
        fun getInstance(context: Context): MsaIdManager {
            if (sInstance == null) {
                synchronized(MsaIdManager::class.java) {
                    if (sInstance == null) {
                        sInstance = MsaIdManager(context.applicationContext)
                    }
                }
            }
            return sInstance!!
        }
    }
}
