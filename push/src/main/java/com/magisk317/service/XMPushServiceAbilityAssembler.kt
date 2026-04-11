package com.magisk317.service

import android.os.Build
import com.magisk317.Global
import com.xiaomi.push.revival.NotificationsRevivalForSelfUpdated
import com.xiaomi.push.service.XMPushService
import com.xiaomi.push.service.XMPushServiceMessenger

object XMPushServiceAbilityAssembler {
    @JvmStatic
    fun prepare(pushService: XMPushService) {
        Global.RegistrationRecorder().initContext(pushService)
    }

    @JvmStatic
    fun createListeners(pushService: XMPushService): List<XMPushServiceListener> {
        val listeners = ArrayList<XMPushServiceListener>()
        listeners += RegisterRecordAbility(RegisterRecorder(pushService))
        listeners += ForegroundAbility(ForegroundHelper(pushService))
        listeners += MessengerAbility(XMPushServiceMessenger(pushService))
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            listeners += BackgroundActivityStartAbility(pushService)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            listeners += NotificationsRevivalAbility(
                NotificationsRevivalForSelfUpdated(pushService) { sbn -> sbn.tag == null }
            )
        }
        listeners += PullAllApplicationDataAbility(pushService)
        return listeners
    }
}
