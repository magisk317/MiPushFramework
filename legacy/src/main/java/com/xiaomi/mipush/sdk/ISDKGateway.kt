package com.xiaomi.mipush.sdk
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import android.content.Intent

/**
 * Interface to allow foundation components to interact with product-layer services/receivers
 * without direct circular dependencies.
 */
interface ISDKGateway {
    /**
     * Hand off an intent to the SDK's PushServiceReceiver
     */
    fun onReceivePushService(context: Context, intent: Intent)
}
