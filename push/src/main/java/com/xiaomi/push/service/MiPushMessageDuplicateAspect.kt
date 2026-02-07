package com.xiaomi.push.service

import android.text.TextUtils
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.utils.ConvertUtils
import org.apache.thrift.TBase
import org.aspectj.lang.ProceedingJoinPoint

class MiPushMessageDuplicateAspect {
    fun isDuplicateMessage(
        joinPoint: ProceedingJoinPoint,
        pushService: XMPushService,
        packageName: String,
        messageId: String?
    ): Boolean {
        if (messageId == mockId) {
            mockId = null
            return false
        }
        return joinPoint.proceed() as Boolean
    }

    companion object {
        @JvmField
        var mockId: String? = null

        @JvmStatic
        fun markAsMock(container: XmPushActionContainer) {
            mockId = getMessageId(container)
        }

        @JvmStatic
        fun getMessageId(container: XmPushActionContainer): String? {
            val metaInfo: PushMetaInfo? = container.metaInfo
            if (metaInfo == null) {
                return getMessageIdFromPushAction(container)
            }
            val extra = metaInfo.extra
            if (extra != null) {
                val jobId = extra[PushConstants.EXTRA_JOB_KEY]
                if (!TextUtils.isEmpty(jobId)) {
                    return jobId
                }
            }
            return metaInfo.id
        }

        private fun getMessageIdFromPushAction(container: XmPushActionContainer): String? {
            return try {
                val pushAction = ConvertUtils.getResponseMessageBodyFromContainer(container, null) as TBase<*, *>
                JavaCalls.getField(pushAction, "id")
            } catch (_: Throwable) {
                null
            }
        }

        @JvmStatic
        fun isMockMessage(container: XmPushActionContainer): Boolean {
            return TextUtils.equals(getMessageId(container), mockId)
        }
    }
}
