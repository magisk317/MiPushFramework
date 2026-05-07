package io.github.magisk317.mipush.service

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
