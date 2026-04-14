package io.github.magisk317.mipush.framework.network

import android.content.Context
import com.xiaomi.network.HostFilter
import com.xiaomi.network.HostManager
import com.xiaomi.push.service.PushHostManagerFactory

object PushHostManagerCompat {
    @JvmStatic
    fun create(
        context: Context,
        hostFilter: HostFilter?,
        httpGet: HostManager.HttpGet,
        userId: String
    ): HostManager {
        // Return the specific implementation used in legacy-runtime
        return PushHostManagerFactory.PushHostManager(context, hostFilter, httpGet, userId)
    }
}
