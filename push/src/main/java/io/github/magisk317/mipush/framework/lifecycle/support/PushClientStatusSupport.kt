package io.github.magisk317.mipush.framework.lifecycle.support

import com.xiaomi.push.service.PushClientsManager

object PushClientStatusSupport {
    @JvmStatic
    fun isSpecialError(
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        type: Int,
        reason: Int,
        errorType: String?,
    ): Boolean {
        return io.github.magisk317.mipush.service.runtime.PushClientStatusSupport.isSpecialError(clientLoginInfo, type, reason, errorType)
    }

    @JvmStatic
    fun notifyClientStatus(
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        type: Int,
        reason: Int,
        reasonMessage: String?,
        errorType: String?,
    ) {
        io.github.magisk317.mipush.service.runtime.PushClientStatusSupport.notifyClientStatus(
            clientLoginInfo,
            type,
            reason,
            reasonMessage,
            errorType,
        )
    }

    @JvmStatic
    fun shouldNotifyClient(
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        type: Int,
        reason: Int,
        errorType: String?,
    ): Boolean {
        return io.github.magisk317.mipush.service.runtime.PushClientStatusSupport.shouldNotifyClient(
            clientLoginInfo,
            type,
            reason,
            errorType,
        )
    }

    @JvmStatic
    fun getDesc(type: Int): String {
        return io.github.magisk317.mipush.service.runtime.PushClientStatusSupport.getDesc(type)
    }

    @JvmStatic
    fun notifyStatusListeners(
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        clientStatus: PushClientsManager.ClientStatus,
        notifyType: Int,
    ) {
        io.github.magisk317.mipush.service.runtime.PushClientStatusSupport.notifyStatusListeners(clientLoginInfo, clientStatus, notifyType)
    }

    @JvmStatic
    fun computeNotifyDelay(clientLoginInfo: PushClientsManager.ClientLoginInfo): Int {
        return io.github.magisk317.mipush.service.runtime.PushClientStatusSupport.computeNotifyDelay(clientLoginInfo)
    }
}
