package io.github.magisk317.mipush.push.pipeline

import android.content.Context
import com.xiaomi.push.service.MIPushAppAbsentManager
import com.xiaomi.push.service.MIPushHelper
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.PacketHelper
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import com.xiaomi.xmsf.stock.StockProfileIdStore
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.PushRuntimePendingPacketStore
import io.github.magisk317.mipush.runtime.PushRuntimeRegistrationTaskStore
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge
import io.github.magisk317.mipush.service.runtime.RegistrationRecordDeduper
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
        val appId = MIPushAppAbsentManager.getRememberedAppId(appContext, packageName)
        val payload = appId?.let { buildPayload(packageName, it) }
        var cleanupFailureCount = 0

        fun cleanup(name: String, block: () -> Unit) {
            runCatching(block).onFailure {
                cleanupFailureCount += 1
                Napier.w("package data clear cleanup failed step=$name pkg=$packageName", it, tag = TAG)
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
        cleanup("application_db") { RegisteredApplicationDb.markUnregistered(packageName) }
        cleanup("registration_secret") { Utils.removeRegSec(packageName) }
        cleanup("last_receive_time") { Utils.removeLastReceiveTime(packageName) }
        cleanup("registration_record_deduper") { RegistrationRecordDeduper.reset(packageName) }
        cleanup("registration_tasks") { PushRuntimeRegistrationTaskStore.clear(packageName) }
        cleanup("pending_packets") { PushRuntimePendingPacketStore.discardPackage(packageName) }
        cleanup("runtime_state") {
            PushRuntime.observeUnregistration(
                packageName = packageName,
                source = "PackageDataClearedCoordinator",
                reason = "package_data_cleared",
            )
        }

        val packetAccepted = payload?.let {
            runCatching { dispatcher.dispatch(packageName, it) }
                .onFailure { error ->
                    Napier.w("app-data-cleared dispatch failed pkg=$packageName", error, tag = TAG)
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
