package com.xiaomi.push.service.awake

import android.net.Uri
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.string.XMStringUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Base64

/*
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
    fun decode(str: String): String {
        val bytes = runCatching { Base64.getDecoder().decode(str) }
            .getOrElse { runCatching { Base64.getMimeDecoder().decode(str) }.getOrDefault(ByteArray(0)) }
        return XMStringUtils.bytesToString(bytes).orEmpty()
    }

    @JvmStatic
    fun encode(str: String): String = Base64.getEncoder().encodeToString(XMStringUtils.getBytes(str))

    @JvmStatic
    fun getContentUri(str: String, str2: String): Uri {
        return Uri.parse("content://$str").buildUpon().appendPath(str2).build()
    }

    @JvmStatic
    fun getString(map: Map<String, String>?): String {
        if (map == null) return ""
        return buildJsonObject {
            for ((key, value) in map) {
                put(key, value)
            }
        }.toString()
    }

    @JvmStatic
    fun obfuscateLogContent(map: Map<String, String>?): String {
        val map2 = HashMap<String, String>()
        if (map != null) {
            map2[AwakeUploadHelper.KEY_EVENT_TYPE] = "${map[AwakeUploadHelper.KEY_EVENT_TYPE]}"
            map2[AwakeUploadHelper.KEY_DESCRIPTION] = "${map[AwakeUploadHelper.KEY_DESCRIPTION]}"
            val str = map[AwakeUploadHelper.KEY_AWAKE_INFO]
            if (!str.isNullOrEmpty()) {
                try {
                    val root = Json.parseToJsonElement(str).jsonObject
                    map2[PLAN_ID] = root[PLAN_ID]?.jsonPrimitive?.content ?: "null"
                    map2[FLOW_ID] = root[FLOW_ID]?.jsonPrimitive?.content ?: "null"
                    map2["jobkey"] = root["jobkey"]?.jsonPrimitive?.content ?: "null"
                    map2[MSG_ID] = root[MSG_ID]?.jsonPrimitive?.content ?: "null"
                    map2["A"] = root[AWAKE_APP]?.jsonPrimitive?.content ?: "null"
                    map2["B"] = root[AWAKENED_APP]?.jsonPrimitive?.content ?: "null"
                    map2["module"] = root[AWAKE_TYPE]?.jsonPrimitive?.content ?: "null"
                } catch (e: Exception) {
                    MyLog.e(e)
                }
            }
        }
        return getString(map2)
    }
}
