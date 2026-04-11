package com.magisk317.push.pipeline

import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.push.utils.RegSecUtils
import com.xiaomi.xmsf.utils.ConvertUtils

object MessageIdentity {
    @JvmStatic
    fun fromContainer(container: XmPushActionContainer?): String? {
        if (container == null) return null
        val metaInfo = container.metaInfo
        if (metaInfo != null) {
            val extra = metaInfo.extra
            if (extra != null) {
                val jobId = extra[PushConstants.EXTRA_JOB_KEY]
                if (!jobId.isNullOrEmpty()) {
                    return jobId
                }
            }
            fromMeta(metaInfo.id)?.let { return it }
        }
        return messageIdFromPushAction(container)
    }

    @JvmStatic
    fun fromMeta(metaId: String?): String? {
        if (!metaId.isNullOrEmpty()) return metaId
        return null
    }

    private fun messageIdFromPushAction(container: XmPushActionContainer): String? {
        return runCatching {
            val pushAction = ConvertUtils.getResponseMessageBodyFromContainer(container, RegSecUtils.getRegSec(container))
            JavaCalls.getField(pushAction, "id") as? String
        }.getOrNull()
    }
}
