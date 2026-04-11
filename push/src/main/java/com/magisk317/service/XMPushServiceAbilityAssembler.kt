package com.magisk317.service

import android.os.Build
import com.magisk317.Global
import com.oasisfeng.condom.CondomContext
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.push.revival.NotificationsRevivalForSelfUpdated
import com.xiaomi.push.service.XMPushService
import com.xiaomi.push.service.XMPushServiceMessenger
import com.xiaomi.xmsf.push.control.XMOutbound
import io.github.magisk317.mipush.common.Constants.TAG_CONDOM

object XMPushServiceAbilityAssembler {
    @JvmStatic
    fun prepare(pushService: XMPushService) {
        Global.RegistrationRecorder().initContext(pushService)
        wrapCondomContext(pushService)
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

    private fun wrapCondomContext(pushService: XMPushService) {
        val base = pushService.baseContext
        JavaCalls.setField(
            pushService,
            "mBase",
            CondomContext.wrap(
                base,
                TAG_CONDOM,
                XMOutbound.create(base, XMPushServiceAbilityAssembler::class.java.simpleName)
            )
        )
    }
}
