package io.github.magisk317.mipush.runtime

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.Context
import android.os.Bundle
import io.github.magisk317.mipush.diagnostics.PushHealthSnapshotLogger
import io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge
import io.github.magisk317.mipush.utils.RegistrationHelper
import io.github.magisk317.mipush.platform.support.XMPushUtils
import com.xiaomi.mipush.sdk.AppInfoHolder
import com.xiaomi.mipush.sdk.MiPushClient
import com.xiaomi.mipush.sdk.PushServiceClient
import com.xiaomi.push.sdk.PushMessageProcessor
import com.xiaomi.push.service.ResetConnectJob
import com.xiaomi.xmsf.push.service.XMAccountManager
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.app.di.AppDependencies

object PushRuntimeExecutionBridge : PushRuntimeExecutionHost {

    @Volatile
    private var appContext: Context? = null

    @JvmStatic
    fun attach(context: Context) {
        appContext = context.applicationContext ?: context
        PushRuntime.attachExecutionHost(this)
    }

    @JvmStatic
    fun detach() {
        PushRuntime.detachExecutionHost(this)
        appContext = null
    }

    override fun requestFrameworkRegistration(reason: String): Boolean {
        val context = appContext ?: return false
        return runCatching {
            val appInfoHolder = AppInfoHolder.getInstance(context)
            PushHealthSnapshotLogger.log(
                context,
                "PushRuntimeExecutionBridge.frameworkRegister",
                "reason=$reason before=${appInfoHolder.registrationStateSummary(Constants.APP_ID, Constants.APP_KEY)}"
            )
            MiPushClient.registerPush(context, Constants.APP_ID, Constants.APP_KEY)
            val regIdPresent = MiPushClient.getRegId(context).isNotBlank()
            if (regIdPresent) {
                PushRuntime.observeRegistrationResult(
                    packageName = PushRuntimeComponents.SERVICE_PACKAGE,
                    success = true,
                    source = "PushRuntimeExecutionBridge.requestFrameworkRegistration",
                    reason = "reg_id_present"
                )
            }
            logD(
                "requestFrameworkRegistration reason=$reason regIdPresent=$regIdPresent " +
                    appInfoHolder.registrationStateSummary(Constants.APP_ID, Constants.APP_KEY)
            )
            true
        }.getOrElse {
            logE("requestFrameworkRegistration failed reason=$reason", it)
            false
        }
    }

    override fun requestApplicationRegistration(packageName: String, reason: String): Boolean {
        val context = appContext ?: return false
        return runCatching {
            PushHealthSnapshotLogger.log(
                context,
                "PushRuntimeExecutionBridge.appRegister",
                "pkg=$packageName reason=$reason"
            )
            val dispatched = RegistrationHelper.tryForceRegister(packageName)
            logD("requestApplicationRegistration pkg=$packageName reason=$reason dispatched=$dispatched")
            dispatched
        }.getOrElse {
            logE("requestApplicationRegistration failed pkg=$packageName reason=$reason", it)
            false
        }
    }

    override fun processPendingRegisterTasks(reason: String): Boolean {
        val context = appContext ?: return false
        return runCatching {
            PushHealthSnapshotLogger.log(
                context,
                "PushRuntimeExecutionBridge.processRegisterTask",
                "reason=$reason"
            )
            PushServiceClient.getInstance(context).processRegisterTask()
            logD("processPendingRegisterTasks reason=$reason")
            true
        }.getOrElse {
            logE("processPendingRegisterTasks failed reason=$reason", it)
            false
        }
    }

    override fun syncAccountAlias(reason: String): Boolean {
        val context = appContext ?: return false
        return runCatching {
            PushHealthSnapshotLogger.log(
                context,
                "PushRuntimeExecutionBridge.syncAccountAlias",
                "reason=$reason"
            )
            XMAccountManager.getInstance(context).setAccountAsAlias()
            logD("syncAccountAlias reason=$reason")
            true
        }.getOrElse {
            logE("syncAccountAlias failed reason=$reason", it)
            false
        }
    }

    override fun dispatchDownstreamPayload(
        payload: ByteArray,
        source: String,
        launchApp: Boolean
    ): PushRuntimeApplicationDispatchResult {
        val context = appContext ?: return PushRuntimeApplicationDispatchResult()
        return runCatching {
            val container = XMPushUtils.packToContainer(payload) ?: return@runCatching PushRuntimeApplicationDispatchResult()
            PushHealthSnapshotLogger.log(
                context,
                "PushRuntimeExecutionBridge.dispatchDownstream",
                "pkg=${container.packageName} source=$source launch=$launchApp"
            )
            val result = getProcessor(context).deliverToApplication(
                context = context,
                container = container,
                payload = payload,
                launchApp = launchApp,
                notifyRuntime = false
            )
            PushRuntimeApplicationDispatchResult(
                dispatched = result.dispatched,
                deliveredToService = result.deliveredToService,
                deliveredByBroadcastFallback = result.deliveredByBroadcastFallback
            )
        }.getOrElse {
            logE("dispatchDownstreamPayload failed source=$source", it)
            PushRuntimeApplicationDispatchResult()
        }
    }

    override fun cancelNotificationForPayload(
        payload: ByteArray,
        notificationId: Int,
        notificationGroup: String?,
        source: String
    ): Boolean {
        val context = appContext ?: return false
        return runCatching {
            val container = XMPushUtils.packToContainer(payload) ?: return@runCatching false
            val bundle = Bundle().apply {
                putByteArray(com.xiaomi.push.service.PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
                putInt(Constants.INTENT_NOTIFICATION_ID, notificationId)
                putString(Constants.INTENT_NOTIFICATION_GROUP, notificationGroup)
            }
            PushHealthSnapshotLogger.log(
                context,
                "PushRuntimeExecutionBridge.cancelNotification",
                "pkg=${container.packageName} id=$notificationId source=$source"
            )
            getProcessor(context).cancelNotification(context, bundle, container)
            true
        }.getOrElse {
            logE("cancelNotificationForPayload failed source=$source", it)
            false
        }
    }

    override fun ensureConnection(reason: String): Boolean {
        return runCatching {
            val service = XMPushServiceLifecycleBridge.withService { it } ?: return@runCatching false
            PushHealthSnapshotLogger.log(
                service,
                "PushRuntimeExecutionBridge.ensureConnection",
                "reason=$reason connected=${service.isConnected} connecting=${service.isConnecting}"
            )
            if (service.isConnected || service.isConnecting) {
                PushRuntimeChannelTracker.syncNow("PushRuntimeExecutionBridge.ensureConnection:already_active")
                true
            } else {
                service.scheduleConnect(true)
                PushRuntimeChannelTracker.syncNow("PushRuntimeExecutionBridge.ensureConnection:scheduled")
                true
            }
        }.getOrElse {
            logE("ensureConnection failed reason=$reason", it)
            false
        }
    }

    override fun resetConnection(reason: String): Boolean {
        return runCatching {
            val service = XMPushServiceLifecycleBridge.withService { it } ?: return@runCatching false
            PushHealthSnapshotLogger.log(
                service,
                "PushRuntimeExecutionBridge.resetConnection",
                "reason=$reason"
            )
            service.executeJob(ResetConnectJob(service))
            PushRuntime.observeConnectionState(
                state = PushConnectionState.Connecting,
                source = "PushRuntimeExecutionBridge.resetConnection",
                host = service.currentConnection?.host,
                reason = reason
            )
            true
        }.getOrElse {
            logE("resetConnection failed reason=$reason", it)
            false
        }
    }

    private fun getProcessor(context: Context): PushMessageProcessor {
        return AppDependencies.get<PushMessageProcessor>(context.applicationContext)
    }
}
