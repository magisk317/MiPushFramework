package com.magisk317.push.pipeline

import android.text.TextUtils
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.XmPushActionContainer
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
                if (!TextUtils.isEmpty(jobId)) {
                    return jobId
                }
            }
            val metaId = metaInfo.id
            if (!TextUtils.isEmpty(metaId)) {
                return metaId
            }
        }
        return messageIdFromPushAction(container)
    }

    private fun messageIdFromPushAction(container: XmPushActionContainer): String? {
        return runCatching {
            val pushAction = ConvertUtils.getResponseMessageBodyFromContainer(container, null)
            JavaCalls.getField<String>(pushAction, "id")
        }.getOrNull()
    }
}

