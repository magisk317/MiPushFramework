package io.github.magisk317.mipush.push.bridge

import android.content.Context
import android.content.Intent
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow
import org.apache.thrift.TBase

/** Shell-owned integrations required by the reusable push pipeline. */
interface PushShellBridge {
    fun receiveFromApplication(intent: Intent)
    fun recordRegisterRequest(context: Context, intent: Intent)
    fun transferToServer(intent: Intent)
    fun receiveFromServer(container: XmPushActionContainer)
    fun transferToApplication(container: XmPushActionContainer)
    fun recordEvent(context: Context, container: XmPushActionContainer)
    fun resolveTargetPackage(container: XmPushActionContainer): String?

    fun packToContainer(payload: ByteArray?): XmPushActionContainer?
    fun packToBytes(container: XmPushActionContainer): ByteArray
    fun dispatchToApplication(context: Context, packageName: String, payload: ByteArray): Boolean
    fun isProfileAllowed(context: Context, container: XmPushActionContainer): Boolean
    fun decodeMessageBody(container: XmPushActionContainer, regSec: String?): TBase<*, *>?
    fun getRegSec(container: XmPushActionContainer): String?

    fun updateRegistrationState(application: RuntimeRegisteredApplicationRow, nextType: Int)
    fun forgetPendingRegistration(context: Context, packageName: String)
    fun queuePendingAppAbsent(context: Context, packageName: String, appId: String)
    fun forgetRegisteredPackage(context: Context, packageName: String)
    fun rememberRegisteredPackage(context: Context, packageName: String, appId: String)
    fun setRegSec(context: Context, packageName: String, regSecret: String)

    fun applyConfigurations(packageName: String, container: XmPushActionContainer)
    fun shouldDropInbound(context: Context, container: XmPushActionContainer, source: String): Boolean
    fun shouldDropNotification(context: Context, container: XmPushActionContainer, source: String): Boolean
}

object PushShellBridgeHolder {
    @Volatile
    private var installed: PushShellBridge? = null

    @JvmStatic
    fun install(bridge: PushShellBridge) {
        installed = bridge
    }

    @JvmStatic
    fun peek(): PushShellBridge? = installed

    @JvmStatic
    fun require(): PushShellBridge = checkNotNull(installed) {
        "PushShellBridge has not been installed"
    }
}
