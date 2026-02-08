package com.magisk317.service

import android.os.Build
import androidx.annotation.RequiresApi
import com.xiaomi.push.revival.NotificationsRevivalForSelfUpdated

@RequiresApi(api = Build.VERSION_CODES.M)
class NotificationsRevivalAbility(
    private val notificationsRevival: NotificationsRevivalForSelfUpdated
) : XMPushServiceListener {
    override fun created() {
        notificationsRevival.initialize()
    }

    override fun destroy() {
        notificationsRevival.close()
    }
}
