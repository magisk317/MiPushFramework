package com.xiaomi.push.service.awake

import android.net.Uri
import android.text.TextUtils
import android.util.Base64
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.string.XMStringUtils
import org.json.JSONException
import org.json.JSONObject

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/push/service/awake/AwakeDataHelper.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
object AwakeDataHelper {
    private const val AWAKENED_APP = "awakened_app"
    private const val AWAKE_APP = "awake_app"
    private const val AWAKE_TYPE = "awake_type"
    private const val FLOW_ID = "flow_id"
    private const val JOB_KEY = "jobkey"
    private const val MSG_ID = "msg_id"
    private const val PLAN_ID = "__planId__"

    @JvmStatic
    fun decode(str: String): String = XMStringUtils.bytesToString(Base64.decode(str, 2) ?: ByteArray(0)).orEmpty()

    @JvmStatic
    fun encode(str: String): String = Base64.encodeToString(XMStringUtils.getBytes(str), 2)

    @JvmStatic
    fun getContentUri(str: String, str2: String): Uri {
        return Uri.parse("content://$str").buildUpon().appendPath(str2).build()
    }

    @JvmStatic
    fun getString(map: Map<String, String>?): String {
        if (map == null) return ""
        val jsonObject = JSONObject()
        try {
            for (key in map.keys) {
                jsonObject.put(key, map[key])
            }
        } catch (e: JSONException) {
            MyLog.e(e)
        }
        return jsonObject.toString()
    }

    @JvmStatic
    fun obfuscateLogContent(map: Map<String, String>?): String {
        val map2 = HashMap<String, String>()
        if (map != null) {
            map2[AwakeUploadHelper.KEY_EVENT_TYPE] = "${map[AwakeUploadHelper.KEY_EVENT_TYPE]}"
            map2[AwakeUploadHelper.KEY_DESCRIPTION] = "${map[AwakeUploadHelper.KEY_DESCRIPTION]}"
            val str = map[AwakeUploadHelper.KEY_AWAKE_INFO]
            if (!TextUtils.isEmpty(str)) {
                try {
                    val jsonObject = JSONObject(str!!)
                    map2[PLAN_ID] = "${jsonObject.opt(PLAN_ID)}"
                    map2[FLOW_ID] = "${jsonObject.opt(FLOW_ID)}"
                    map2["jobkey"] = "${jsonObject.opt("jobkey")}"
                    map2[MSG_ID] = "${jsonObject.opt(MSG_ID)}"
                    map2["A"] = "${jsonObject.opt(AWAKE_APP)}"
                    map2["B"] = "${jsonObject.opt(AWAKENED_APP)}"
                    map2["module"] = "${jsonObject.opt(AWAKE_TYPE)}"
                } catch (e: JSONException) {
                    MyLog.e(e)
                }
            }
        }
        return getString(map2)
    }
}
