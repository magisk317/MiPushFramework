package io.github.magisk317.mipush.bridge

import android.content.Context
import android.content.Intent
import com.xiaomi.slim.Blob

internal class MiPushRuntimeCompatibilityAdapter(
    private val appContext: Context,
) {
    fun getMIID(): String? = "0"

    fun sendBroadcast(intent: Intent) {
        appContext.sendBroadcast(intent)
    }

    fun constructBindBlob(client: Any): Blob? = null

    fun constructUnbindBlob(chid: String, userId: String): Blob? = null

    fun removeCachedMsgId(msgId: String) {
        // Managed dynamically via Dispatcher.
    }
}
