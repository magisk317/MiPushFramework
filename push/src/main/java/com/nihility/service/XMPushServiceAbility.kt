package com.nihility.service

import android.content.Context
import android.os.Build
import com.nihility.Global
import com.oasisfeng.condom.CondomContext
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.push.revival.NotificationsRevivalForSelfUpdated
import com.xiaomi.push.service.BackgroundActivityStartEnabler
import com.xiaomi.push.service.PullAllApplicationDataFromServerJob
import com.xiaomi.push.service.XMPushService
import com.xiaomi.push.service.XMPushServiceMessenger
import com.xiaomi.xmsf.push.control.XMOutbound
import top.trumeet.common.Constants.TAG_CONDOM

class XMPushServiceAbility(pushService: XMPushService) : XMPushServiceListenerNotifier() {

    init {
        xmPushService = pushService
        Global.RegistrationRecorder().initContext(pushService)
        condomContext(pushService)
        initListeners(pushService)
    }

    private fun initListeners(pushService: XMPushService) {
        addListener(RegisterRecordAbility(RegisterRecorder(pushService)))
        addListener(ForegroundAbility(ForegroundHelper(pushService)))
        addListener(MessengerAbility(XMPushServiceMessenger(pushService)))
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            addListener(object : XMPushServiceListener {
                override fun created() {
                    BackgroundActivityStartEnabler.initialize(pushService)
                }
            })
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            addListener(
                NotificationsRevivalAbility(
                    NotificationsRevivalForSelfUpdated(pushService) { sbn -> sbn.tag == null }
                )
            )
        }
        addListener(object : XMPushServiceListener {
            override fun connectionStatusChanged(connectionStatus: XMPushServiceListener.ConnectionStatus) {
                if (connectionStatus == XMPushServiceListener.ConnectionStatus.connected) {
                    pushService.executeJob(PullAllApplicationDataFromServerJob(pushService))
                }
            }
        })
    }

    private fun condomContext(pushService: XMPushService) {
        val base = pushService.baseContext
        JavaCalls.setField(
            pushService,
            "mBase",
            CondomContext.wrap(
                base,
                TAG_CONDOM,
                XMOutbound.create(base, XMPushServiceAbility::class.java.simpleName)
            )
        )
    }

    companion object {
        @JvmField
        var xmPushService: XMPushService? = null
    }
}
