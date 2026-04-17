package com.xiaomi.push.service.awake

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.push.service.awake.module.AwakeManager
import java.util.HashMap

object AwakeUploadHelper {
    const val A_AWAKE_B_END = 1006
    const val A_AWAKE_B_RESULT = 1005
    const val A_GET_MESSAGE = 1001
    const val A_WILL_AWAKE_B = 1004
    const val B_MISS_COMPONENT = 1003
    const val B_SATISFY_COMPONENT = 1002
    const val B_UPLOAD_RESULT = 1007
    const val ERROR = 1008
    const val KEY_AWAKE_INFO = "awake_info"
    const val KEY_DESCRIPTION = "description"
    const val KEY_EVENT_TYPE = "event_type"
    const val KEY_PING_FREQUENCY = "ping_frequency"
    const val KEY_PING_SWITCH = "ping_switch"
    const val PING = 9999
    const val SEND_BOTH_WAY = 3
    const val SEND_BY_TINY_DATA = 2
    const val SEND_DIRECTLY = 1

    private fun doLast(context: Context, map: HashMap<String, String>) {
        val sendDataIml = AwakeManager.getInstance(context).sendDataIml
        sendDataIml?.shouldDoLast(context, map)
    }

    private fun doUploadData(context: Context, str: String, i: Int, str2: String) {
        if (TextUtils.isEmpty(str)) return
        try {
            val map = HashMap<String, String>().apply {
                put(KEY_AWAKE_INFO, str)
                put(KEY_EVENT_TYPE, i.toString())
                put(KEY_DESCRIPTION, str2)
            }
            when (AwakeManager.getInstance(context).onLineCmd) {
                1 -> sendResponseDirectly(context, map)
                2 -> sendResultByTinyData(context, map)
                3 -> {
                    sendResponseDirectly(context, map)
                    sendResultByTinyData(context, map)
                }
            }
            doLast(context, map)
        } catch (e: Exception) {
            MyLog.e(e)
        }
    }

    private fun sendResponseDirectly(context: Context, map: HashMap<String, String>) {
        AwakeManager.getInstance(context).sendDataIml?.sendDirectly(context, map)
    }

    private fun sendResultByTinyData(context: Context, map: HashMap<String, String>) {
        AwakeManager.getInstance(context).sendDataIml?.sendByTinyData(context, map)
    }

    @JvmStatic
    fun uploadData(context: Context, str: String, i: Int, str2: String) {
        ScheduledJobManager.getInstance(context).addOneShootJob {
            doUploadData(context, str, i, str2)
        }
    }
}
