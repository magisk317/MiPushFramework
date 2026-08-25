package io.github.magisk317.mipush.push.pipeline

import android.content.Context
import android.content.pm.PackageManager
import com.xiaomi.push.service.MIPushAppAbsentManager
import com.xiaomi.push.service.MIPushHelper
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.PacketHelper
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import com.xiaomi.xmsf.stock.StockProfileIdStore
import co.touchlab.kermit.Logger
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.notification.NativeNotificationFeatureBuilder
import io.github.magisk317.mipush.notification.SweetNotificationCoordinator
import io.github.magisk317.mipush.notification.TopNotificationCoordinator
import io.github.magisk317.mipush.notification.VoipNotificationHelper
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.PushRuntimePendingPacketStore
import io.github.magisk317.mipush.runtime.PushRuntimeRegistrationTaskStore
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge
import io.github.magisk317.mipush.service.runtime.RegistrationRecordDeduper
import io.github.magisk317.mipush.service.runtime.ExtensionNotificationCoordinator
import io.github.magisk317.mipush.service.runtime.KeepAliveRuntimeAdapter
import io.github.magisk317.mipush.service.runtime.MyMIPushNotificationStyleSupport
import io.github.magisk317.mipush.service.runtime.MyMIPushNotificationHelper
import io.github.magisk317.mipush.service.runtime.StockMiPushPayloadDeduper
import io.github.magisk317.xposed.logging.MagiskOtel

internal fun interface AppDataClearedPacketDispatcher {
    fun dispatch(packageName: String, payload: ByteArray): Boolean
}

internal data class PackageDataClearedResult(
    val appIdPresent: Boolean,
    val packetAccepted: Boolean,
    val cleanupFailureCount: Int,
)

internal object PackageDataClearedCoordinator {
    private const val TAG = "PackageDataClearedCoordinator"

    // Stock 7.4.67-C s0.d uses Notification.type=app_data_cleared. The pinned SDK 3.7.9
    // NotificationType enum predates this value, so the newer wire value stays product-owned here
    // instead of modifying the frozen pinned surface.
    internal const val APP_DATA_CLEARED_TYPE = "app_data_cleared"

    private val runtimeDispatcher = AppDataClearedPacketDispatcher { packageName, payload ->
        XMPushServiceLifecycleBridge.withService { service ->
            service.sendMessage(packageName, payload, true)
            true
        } ?: false
    }

    fun handle(
        context: Context,
        packageName: String,
        dispatcher: AppDataClearedPacketDispatcher = runtimeDispatcher,
    ): PackageDataClearedResult {
        val appContext = context.applicationContext ?: context
        val userId = resolveTargetUserId(appContext, packageName)
        val appId = MIPushAppAbsentManager.getRememberedAppId(appContext, packageName)
        val payload = appId?.let { buildPayload(packageName, it) }
        var cleanupFailureCount = 0

        fun cleanup(name: String, block: () -> Unit) {
            runCatching(block).onFailure {
                cleanupFailureCount += 1
                Logger.withTag(TAG).w(it) { "package data clear cleanup failed step=$name pkg=$packageName" }
            }
        }

        // Stock 7.4.67-C clears confirmed registration, notify type, notifications, profile IDs,
        // and channel-derived state before emitting AppDataCleared. Project-owned caches must also
        // be invalidated so an old registration payload cannot be replayed after app data reset.
        cleanup("confirmed_registration") {
            MIPushAppAbsentManager.forgetRegisteredPackage(appContext, packageName)
        }
        cleanup("pending_registration") {
            MIPushAppAbsentManager.forgetPendingRegistration(appContext, packageName)
        }
        cleanup("notify_type") {
            if (MIPushNotificationHelper.hasLocalNotifyType(appContext, packageName)) {
                MIPushNotificationHelper.clearLocalNotifyType(appContext, packageName)
            }
        }
        cleanup("profile_ids") { StockProfileIdStore.clear(appContext, packageName) }
        cleanup("application_db") { RegisteredApplicationDb.markUnregistered(packageName, userId) }
        cleanup("registration_secret") { Utils.removeRegSec(packageName, userId) }
        cleanup("last_receive_time") { Utils.removeLastReceiveTime(packageName, userId) }
        cleanup("registration_record_deduper") { RegistrationRecordDeduper.reset(packageName, userId) }
        cleanup("registration_tasks") { PushRuntimeRegistrationTaskStore.clear(packageName, userId) }
        cleanup("pending_packets") { PushRuntimePendingPacketStore.discardPackage(packageName, userId) }
        cleanup("runtime_state") {
            PushRuntime.clearPackageTransientState(packageName, userId)
            PushRuntime.observeUnregistration(
                packageName = packageName,
                source = "PackageDataClearedCoordinator",
                reason = "package_data_cleared",
            )
        }
        cleanup("payload_deduplication") {
            StockMiPushPayloadDeduper.clearPackageState(packageName, userId)
            MyMIPushNotificationHelper.clearPackageTransientState(packageName, userId)
        }
        cleanup("notification_dispatch_allowance") {
            MiPushRuntimeBridge.clearPackageTransientState(packageName, userId)
        }
        cleanup("top_notification_state") {
            TopNotificationCoordinator.clearPackageState(appContext, packageName, userId)
        }
        cleanup("sweet_notification_state") {
            SweetNotificationCoordinator.clearPackageState(appContext, packageName, userId)
        }
        cleanup("voip_notification_state") {
            VoipNotificationHelper.clearPackageState(packageName, userId)
        }
        cleanup("conversation_history") {
            MyMIPushNotificationStyleSupport.clearConversationHistories(packageName, userId)
        }
        cleanup("media_sessions") {
            NativeNotificationFeatureBuilder.clearPackageState(packageName, userId)
        }
        cleanup("extension_notification_state") {
            ExtensionNotificationCoordinator.clearPackageState(packageName, userId)
        }
        cleanup("keep_alive_state") {
            KeepAliveRuntimeAdapter.clearPackageState(appContext, packageName, userId)
        }

        val packetAccepted = payload?.let {
            runCatching { dispatcher.dispatch(packageName, it) }
                .onFailure { error ->
                    Logger.withTag(TAG).w(error) { "app-data-cleared dispatch failed pkg=$packageName" }
                }
                .getOrDefault(false)
        } ?: false

        MagiskOtel.event(
            name = "push.package",
            attributes = mapOf(
                "result" to if (cleanupFailureCount == 0) "ok" else "partial",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "data_clear",
                "target_package" to packageName,
                "reason" to when {
                    appId == null -> "local_cleanup_no_confirmed_app_id"
                    packetAccepted -> "app_data_cleared_accepted"
                    else -> "app_data_cleared_not_accepted"
                },
                "cleanup_failures" to cleanupFailureCount.toString(),
            ),
            statusOk = cleanupFailureCount == 0 && (appId == null || packetAccepted),
        )

        return PackageDataClearedResult(
            appIdPresent = appId != null,
            packetAccepted = packetAccepted,
            cleanupFailureCount = cleanupFailureCount,
        )
    }

    private fun resolveTargetUserId(context: Context, packageName: String): Int {
        return runCatching {
            val uid = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0),
                ).uid
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, 0).uid
            }
            (uid.toLong() / 100_000L).toInt().coerceAtLeast(0)
        }.getOrElse {
            Utils.myUserId().coerceAtLeast(0)
        }
    }

    internal fun buildPayload(packageName: String, appId: String): ByteArray {
        val notification = XmPushActionNotification().apply {
            setAppId(appId)
            setType(APP_DATA_CLEARED_TYPE)
            setId(PacketHelper.generatePacketID())
            setRequireAck(false)
        }
        val container = MIPushHelper.generateRequestContainer(
            packageName,
            appId,
            notification,
            ActionType.Notification,
        )
        return XmPushThriftSerializeUtils.convertThriftObjectToBytes(container) ?: ByteArray(0)
    }
}
