package com.xiaomi.push.service

import android.content.Context
import com.nihility.Global
import com.nihility.Hooked
import com.xiaomi.push.service.MIPushNotificationHelper.NotifyPushMessageInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import org.aspectj.lang.ProceedingJoinPoint

class MIPushNotificationHelperAspect {
    fun notifyPushMessage(
        joinPoint: ProceedingJoinPoint,
        context: Context,
        container: XmPushActionContainer,
        decryptedContent: ByteArray
    ): NotifyPushMessageInfo {
        Hooked.mark("MIPushNotificationHelper.NotifyPushMessageInfo")
        Global.MiPushEventListener().transferToApplication(container)
        MyMIPushNotificationHelper.notifyPushMessage(context, decryptedContent)
        return NotifyPushMessageInfo()
    }
}
