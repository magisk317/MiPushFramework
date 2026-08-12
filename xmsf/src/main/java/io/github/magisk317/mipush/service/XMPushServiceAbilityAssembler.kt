package io.github.magisk317.mipush.service

import android.os.Build
import android.service.notification.StatusBarNotification
import com.xiaomi.push.revival.NotificationsRevivalForSelfUpdated
import com.xiaomi.push.service.XMPushServiceCore
import com.xiaomi.push.service.XMPushServiceMessenger
import io.github.magisk317.mipush.common.utils.Utils

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
                NotificationsRevivalForSelfUpdated(pushService) { sbn ->
                    shouldReviveXmsfNotification(sbn, pushService.packageName)
                }
            )
        }
        // Stock XMSF 7.4.67-C has no fake_pull connection listener. The older product assembler
        // inherited this extension and sent a pull notification for every registered package after
        // each reconnect; exclude it because notification-pull and telemetry collection are off.
        return listeners
    }
}

/**
 * The vendor revival implementation republishes through the XMSF context and cannot preserve the
 * original notification package. Only revive notifications that were owned by XMSF originally.
 */
internal fun shouldReviveXmsfNotification(
    sbn: StatusBarNotification,
    ownerPackageName: String,
): Boolean {
    if (
        sbn.packageName != ownerPackageName ||
        sbn.userId != Utils.myUserId() ||
        sbn.tag != null
    ) return false

    // A target post can fall back to the XMSF package when notifyAsPackage is unavailable. The
    // notification publisher marks that case before the fallback, so do not revive it locally.
    val targetPackage = sbn.notification.extras?.getString("xmsf_target_package")
    return targetPackage.isNullOrBlank()
}
