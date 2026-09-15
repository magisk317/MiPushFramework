package io.github.magisk317.mipush.push.bridge

import android.content.Context
import android.content.Intent
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.app.di.AppDependencies
import io.github.magisk317.mipush.common.notification.NotificationAvailabilityReader
import io.github.magisk317.mipush.common.notification.NotificationAvailabilityRequest
import io.github.magisk317.mipush.compat.RegistrationStateStore
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.push.pipeline.StalePackagePushGuard
import io.github.magisk317.mipush.service.RegisterRecorder
import io.github.magisk317.mipush.runtime.PushRuntimeChannelTracker
import io.github.magisk317.mipush.runtime.core.ConnectionStatus
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow
import io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge
import io.github.magisk317.mipush.utils.Configurations
import io.github.magisk317.mipush.utils.ConvertUtils
import io.github.magisk317.mipush.utils.RegSecUtils
import io.github.magisk317.mipush.common.utils.Utils
import com.xiaomi.xmsf.stock.StockSurfaceSupport
import org.apache.thrift.TBase

object DefaultPushShellBridge : PushShellBridge {
    override fun ensurePushServiceCreated(pushService: com.xiaomi.push.service.XMPushServiceCore) {
        XMPushServiceLifecycleBridge.ensureCreated(pushService)
    }

    override fun onPushServiceDestroy(pushService: com.xiaomi.push.service.XMPushServiceCore?) {
        XMPushServiceLifecycleBridge.onDestroy(pushService)
    }

    override fun onPushConnectionStatusChanged(connectionStatus: ConnectionStatus) {
        XMPushServiceLifecycleBridge.onConnectionStatusChanged(connectionStatus)
    }

    override fun observePushConnectionState(newStatus: Int, reason: Int, source: String) {
        PushRuntimeChannelTracker.observeConnectionState(newStatus, reason, source)
    }

    override fun markHook(point: String) {
        io.github.magisk317.mipush.hook.Hooked.mark(point)
    }

    override fun isPushDebugEnabled(): Boolean =
        kotlinx.coroutines.runBlocking { Global.configCenter().isDebugModeAsync() }

    override fun formatContainerForDebug(container: XmPushActionContainer?): String =
        io.github.magisk317.mipush.utils.ConvertUtils.toJson(container).toString()

    override fun formatIntentForDebug(intent: Intent?): String =
        io.github.magisk317.mipush.utils.ConvertUtils.toJson(intent).toString()

    override fun receiveFromApplication(intent: Intent) {
        Global.miPushEventListener().receiveFromApplication(intent)
    }

    override fun recordRegisterRequest(context: Context, intent: Intent) {
        RegisterRecorder(context).recordRegisterRequest(intent)
    }

    override fun recordEvent(context: Context, container: XmPushActionContainer) {
        val packageName = container.packageName
        if (packageName.isNullOrBlank()) return
        if (io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb.isBlocked(packageName)) return
        if (!io.github.magisk317.mipush.common.utils.Utils.isUserApplication(context.applicationContext, packageName)) return
        val eventType = io.github.magisk317.mipush.runtime.store.event.type.TypeFactory.createForStore(container)
        val application = io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb.registerApplication(packageName)
        io.github.magisk317.mipush.push.pipeline.MiPushRuntimeBridge.applyRegistrationStateFromContainer(
            context,
            container,
            application,
        )
        val result = runCatching {
            val disabled = AppDependencies.get<NotificationAvailabilityReader>(context)
                .isNotificationDisabled(
                    NotificationAvailabilityRequest(
                        packageName = packageName,
                        metaInfoExtra = container.metaInfo?.extra.orEmpty(),
                    ),
                )
            if (disabled) {
                io.github.magisk317.mipush.runtime.store.kmp.EventRowResultType.DENY_DISABLED
            } else {
                io.github.magisk317.mipush.runtime.store.kmp.EventRowResultType.OK
            }
        }.getOrDefault(io.github.magisk317.mipush.runtime.store.kmp.EventRowResultType.OK)
        kotlinx.coroutines.runBlocking {
            io.github.magisk317.mipush.runtime.store.db.EventDb.insertEventAsync(
                result,
                eventType,
            )
        }
    }

    override fun dispatchAppDataCleared(packageName: String, payload: ByteArray): Boolean {
        return io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge.withService { service ->
            service.sendMessage(packageName, payload, true)
            true
        } ?: false
    }

    override fun clearPackageDataShellState(context: Context, packageName: String, userId: Int): Int {
        return clearShellState(
            packageName = packageName,
            userId = userId,
            steps = listOf(
                "registration_record_deduper" to {
                    io.github.magisk317.mipush.service.runtime.RegistrationRecordDeduper.reset(packageName, userId)
                },
                "profile_ids" to {
                    com.xiaomi.xmsf.stock.StockProfileIdStore.clear(context, packageName)
                },
                "payload_deduplication" to {
                    io.github.magisk317.mipush.service.runtime.StockMiPushPayloadDeduper.clearPackageState(packageName, userId)
                    io.github.magisk317.mipush.service.runtime.MIPushNotificationPublishHelper.clearPackageTransientState(packageName, userId)
                },
                "top_notification_state" to {
                    io.github.magisk317.mipush.notification.TopNotificationCoordinator.clearPackageState(context, packageName, userId)
                },
                "sweet_notification_state" to {
                    io.github.magisk317.mipush.notification.SweetNotificationCoordinator.clearPackageState(context, packageName, userId)
                },
                "voip_notification_state" to {
                    io.github.magisk317.mipush.notification.VoipNotificationHelper.clearPackageState(packageName, userId)
                },
                "conversation_history" to {
                    io.github.magisk317.mipush.service.runtime.MIPushNotificationStyleSupport.clearConversationHistories(packageName, userId)
                },
                "media_sessions" to {
                    io.github.magisk317.mipush.notification.NativeNotificationFeatureBuilder.clearPackageState(packageName, userId)
                },
                "extension_notification_state" to {
                    io.github.magisk317.mipush.service.runtime.ExtensionNotificationCoordinator.clearPackageState(packageName, userId)
                },
                "keep_alive_state" to {
                    io.github.magisk317.mipush.service.runtime.KeepAliveRuntimeAdapter.clearPackageState(context, packageName, userId)
                },
            ),
        )
    }

    override fun clearPackageAbsentShellState(context: Context, packageName: String, userId: Int): Int {
        return clearShellState(
            packageName = packageName,
            userId = userId,
            steps = listOf(
                "registration_record_deduper" to {
                    io.github.magisk317.mipush.service.runtime.RegistrationRecordDeduper.reset(packageName, userId)
                },
                "profile_ids" to {
                    com.xiaomi.xmsf.stock.StockProfileIdStore.clear(context, packageName)
                },
                "stock_notifications" to {
                    com.xiaomi.push.service.MIPushNotificationHelper.clearNotification(context, packageName)
                },
                "payload_deduplication" to {
                    io.github.magisk317.mipush.service.runtime.StockMiPushPayloadDeduper.clearPackageState(packageName, userId)
                    io.github.magisk317.mipush.service.runtime.MIPushNotificationPublishHelper.clearPackageTransientState(packageName, userId)
                },
                "top_notification_state" to {
                    io.github.magisk317.mipush.notification.TopNotificationCoordinator.clearPackageState(context, packageName, userId)
                },
                "sweet_notification_state" to {
                    io.github.magisk317.mipush.notification.SweetNotificationCoordinator.clearPackageState(context, packageName, userId)
                },
                "voip_notification_state" to {
                    io.github.magisk317.mipush.notification.VoipNotificationHelper.clearPackageState(packageName, userId)
                },
                "conversation_history" to {
                    io.github.magisk317.mipush.service.runtime.MIPushNotificationStyleSupport.clearConversationHistories(packageName, userId)
                },
                "media_sessions" to {
                    io.github.magisk317.mipush.notification.NativeNotificationFeatureBuilder.clearPackageState(packageName, userId)
                },
                "extension_notification_state" to {
                    io.github.magisk317.mipush.service.runtime.ExtensionNotificationCoordinator.clearPackageState(packageName, userId)
                },
                "keep_alive_state" to {
                    io.github.magisk317.mipush.service.runtime.KeepAliveRuntimeAdapter.clearPackageState(context, packageName, userId)
                },
            ),
        )
    }

    private fun clearShellState(
        packageName: String,
        userId: Int,
        steps: List<Pair<String, () -> Unit>>,
    ): Int {
        var failures = 0
        for ((name, step) in steps) {
            runCatching(step).onFailure {
                failures += 1
                co.touchlab.kermit.Logger.withTag("PushShellBridge").w(it) {
                    "package cleanup failed step=$name pkg=$packageName user=$userId"
                }
            }
        }
        return failures
    }

    override fun transferToServer(intent: Intent) {
        Global.miPushEventListener().transferToServer(intent)
    }

    override fun receiveFromServer(container: XmPushActionContainer) {
        Global.miPushEventListener().receiveFromServer(container)
    }

    override fun transferToApplication(container: XmPushActionContainer) {
        Global.miPushEventListener().transferToApplication(container)
    }

    override fun resolveTargetPackage(container: XmPushActionContainer): String? =
        StalePackagePushGuard.resolveTargetPackage(container)

    override fun packToContainer(payload: ByteArray?): XmPushActionContainer? =
        XMPushUtils.packToContainer(payload)

    override fun packToBytes(container: XmPushActionContainer): ByteArray =
        XMPushUtils.packToBytes(container)

    override fun dispatchToApplication(context: Context, packageName: String, payload: ByteArray): Boolean =
        XMPushUtils.dispatchToApplication(context, packageName, payload)

    override fun isProfileAllowed(context: Context, container: XmPushActionContainer): Boolean =
        StockSurfaceSupport.isProfileAllowed(context, container)

    override fun decodeMessageBody(container: XmPushActionContainer, regSec: String?): TBase<*, *>? =
        ConvertUtils.getResponseMessageBodyFromContainer(container, regSec)

    override fun getRegSec(container: XmPushActionContainer): String? = RegSecUtils.getRegSec(container)

    override fun updateRegistrationState(application: RuntimeRegisteredApplicationRow, nextType: Int) {
        RegistrationStateStore.updateIfChanged(
            application = application,
            nextType = nextType,
            source = RegistrationStateStore.Source.SERVER_RESULT,
        )
    }

    override fun forgetPendingRegistration(context: Context, packageName: String) {
        com.xiaomi.push.service.MIPushAppAbsentManager.forgetPendingRegistration(context, packageName)
    }

    override fun queuePendingAppAbsent(context: Context, packageName: String, appId: String) {
        com.xiaomi.push.service.MIPushAppAbsentManager.queuePendingAppAbsent(context, packageName, appId)
    }

    override fun forgetRegisteredPackage(context: Context, packageName: String) {
        com.xiaomi.push.service.MIPushAppAbsentManager.forgetRegisteredPackage(context, packageName)
    }

    override fun rememberRegisteredPackage(context: Context, packageName: String, appId: String) {
        com.xiaomi.push.service.MIPushAppAbsentManager.rememberRegisteredPackage(context, packageName, appId)
    }

    override fun setRegSec(context: Context, packageName: String, regSecret: String) {
        Utils.setRegSec(context, packageName, regSecret)
    }

    override fun applyConfigurations(packageName: String, container: XmPushActionContainer) {
        Configurations.getInstance().handle(packageName, container)
    }

    override fun shouldDropInbound(context: Context, container: XmPushActionContainer, source: String): Boolean =
        StalePackagePushGuard.shouldDropInbound(context, container, source)

    override fun shouldDropNotification(context: Context, container: XmPushActionContainer, source: String): Boolean =
        StalePackagePushGuard.shouldDropNotification(context, container, source)
}
