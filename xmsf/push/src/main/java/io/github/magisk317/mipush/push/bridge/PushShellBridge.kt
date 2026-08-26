package io.github.magisk317.mipush.push.bridge

import android.content.Context
import android.content.Intent
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.runtime.core.ConnectionStatus
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow
import org.apache.thrift.TBase

interface PushShellLifecycleBridge {
    fun ensurePushServiceCreated(pushService: com.xiaomi.push.service.XMPushServiceCore)
    fun onPushServiceDestroy(pushService: com.xiaomi.push.service.XMPushServiceCore?)
    fun onPushConnectionStatusChanged(connectionStatus: ConnectionStatus)
    fun observePushConnectionState(newStatus: Int, reason: Int, source: String)
}

interface PushShellHookBridge {
    fun markHook(point: String)
    fun isPushDebugEnabled(): Boolean
    fun formatContainerForDebug(container: XmPushActionContainer?): String
    fun formatIntentForDebug(intent: Intent?): String
}

interface PushShellEventBridge {
    fun receiveFromApplication(intent: Intent)
    fun recordRegisterRequest(context: Context, intent: Intent)
    fun transferToServer(intent: Intent)
    fun receiveFromServer(container: XmPushActionContainer)
    fun transferToApplication(container: XmPushActionContainer)
    fun recordEvent(context: Context, container: XmPushActionContainer)
}

interface PushShellPackageStateBridge {
    fun dispatchAppDataCleared(packageName: String, payload: ByteArray): Boolean
    fun clearPackageDataShellState(context: Context, packageName: String, userId: Int): Int
    fun clearPackageAbsentShellState(context: Context, packageName: String, userId: Int): Int
}

interface PushShellPayloadBridge {
    fun resolveTargetPackage(container: XmPushActionContainer): String?
    fun packToContainer(payload: ByteArray?): XmPushActionContainer?
    fun packToBytes(container: XmPushActionContainer): ByteArray
    fun dispatchToApplication(context: Context, packageName: String, payload: ByteArray): Boolean
    fun isProfileAllowed(context: Context, container: XmPushActionContainer): Boolean
    fun decodeMessageBody(container: XmPushActionContainer, regSec: String?): TBase<*, *>?
    fun getRegSec(container: XmPushActionContainer): String?
}

interface PushShellRegistrationBridge {
    fun updateRegistrationState(application: RuntimeRegisteredApplicationRow, nextType: Int)
    fun forgetPendingRegistration(context: Context, packageName: String)
    fun queuePendingAppAbsent(context: Context, packageName: String, appId: String)
    fun forgetRegisteredPackage(context: Context, packageName: String)
    fun rememberRegisteredPackage(context: Context, packageName: String, appId: String)
    fun setRegSec(context: Context, packageName: String, regSecret: String)
}

interface PushShellPolicyBridge {
    fun applyConfigurations(packageName: String, container: XmPushActionContainer)
    fun shouldDropInbound(context: Context, container: XmPushActionContainer, source: String): Boolean
    fun shouldDropNotification(context: Context, container: XmPushActionContainer, source: String): Boolean
}

/** Composite implementation retained for the shell installation boundary. */
interface PushShellBridge :
    PushShellLifecycleBridge,
    PushShellHookBridge,
    PushShellEventBridge,
    PushShellPackageStateBridge,
    PushShellPayloadBridge,
    PushShellRegistrationBridge,
    PushShellPolicyBridge

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

    fun lifecycle(): PushShellLifecycleBridge = require()

    fun hooks(): PushShellHookBridge = require()

    fun events(): PushShellEventBridge = require()

    fun packageState(): PushShellPackageStateBridge = require()

    fun payload(): PushShellPayloadBridge = require()

    fun registration(): PushShellRegistrationBridge = require()

    fun policy(): PushShellPolicyBridge = require()
}
