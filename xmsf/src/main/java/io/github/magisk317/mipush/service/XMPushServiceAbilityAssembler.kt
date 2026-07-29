package io.github.magisk317.mipush.service

import android.os.Build
import com.xiaomi.push.revival.NotificationsRevivalForSelfUpdated
import com.xiaomi.push.service.XMPushServiceCore
import com.xiaomi.push.service.XMPushServiceMessenger

object XMPushServiceAbilityAssembler {
    @JvmStatic
    fun createListeners(pushService: XMPushServiceCore): List<XMPushServiceListener> {
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
        // Stock XMSF 7.4.67-C has no fake_pull connection listener. The older product assembler
        // inherited this extension and sent a pull notification for every registered package after
        // each reconnect; exclude it because notification-pull and telemetry collection are off.
        return listeners
    }
}
