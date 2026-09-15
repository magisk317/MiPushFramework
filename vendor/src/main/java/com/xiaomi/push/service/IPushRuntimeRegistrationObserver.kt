package com.xiaomi.push.service

import android.content.Context
import android.content.Intent

/**
 * Registration-domain slice of [IPushRuntimeObserver]: account provisioning,
 * registration state/results, pending-registration persistence and channel
 * event bookkeeping that the transport layer reports into.
 */
interface IPushRuntimeRegistrationObserver {

    fun loadAccount(context: Context, source: String): MIPushAccount? = null
    fun registerAccount(context: Context, packageName: String, appId: String, appToken: String, source: String): MIPushAccount? = null
    fun resolveAccountUrl(region: String?, oneBoxBuild: Boolean, oneBoxHost: String, sandBoxBuild: Boolean): String
    fun onRegistrationStateChanged(packageName: String, state: PushRegistrationState, reason: String, message: String)
    fun onRegistrationResult(packageName: String, success: Boolean, source: String, reason: String) {}
    fun repairRegistrationPayload(context: Context, packageName: String): PushRegistrationPayloadRepairResult? = null
    // Product lifecycle metadata is separate from the stock transport's pending wire-payload map.
    fun rememberPendingRegistration(packageName: String, appId: String?) {}
    fun cacheRegistrationRequest(packageName: String, payload: ByteArray)
    fun clearAccount(context: Context, packageName: String) {}
    fun observeUnregistration(packageName: String, state: PushRegistrationState) {}
    fun cacheRegistrationTask(packageName: String, intent: Intent, source: String, reason: String, timestampMs: Long) {}
    fun dispatchRegistrationTasks(source: String, dispatcher: Any? = null) {}
    fun clearRegistrationTasks(packageName: String) {}
    fun onAccountEvent(packageName: String, event: String) {}
    fun attachAccountClient(client: Any) {}

    /**
     * Notifies the product layer about an intent received from an application.
     * This is used for recording events like registration requests and message sent events.
     */
    fun onApplicationIntentReceived(intent: Intent) {}
    
    fun onChannelEvent(packageName: String?, event: String, reason: String)
    fun onChannelStateChanged(packageName: String?, chid: String, userId: String?, session: String?, state: PushChannelState, reason: String, reasonCode: Int?, reasonMsg: String?)
    fun syncChannelTracker(reason: String)
    fun notifyRegisterError(errorCode: Int, errorMessage: String, notifier: IPendingPacketErrorNotifier)
    fun cachePendingMessage(packageName: String, payload: ByteArray)
    fun addPendingMessage(packageName: String, payload: ByteArray) {}
}
