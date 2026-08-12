package io.github.magisk317.mipush.push.pipeline

import android.content.Context
import com.xiaomi.push.service.MIPushAppInfo
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.notification.NativeNotificationFeatureBuilder
import io.github.magisk317.mipush.notification.SweetNotificationCoordinator
import io.github.magisk317.mipush.notification.TopNotificationCoordinator
import io.github.magisk317.mipush.notification.VoipNotificationHelper
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.service.runtime.RegistrationRecordDeduper
import io.github.magisk317.mipush.service.runtime.ExtensionNotificationCoordinator
import io.github.magisk317.mipush.service.runtime.KeepAliveRuntimeAdapter
import io.github.magisk317.mipush.service.runtime.MyMIPushNotificationHelper
import io.github.magisk317.mipush.service.runtime.MyMIPushNotificationStyleSupport
import io.github.magisk317.mipush.service.runtime.StockMiPushPayloadDeduper
import com.xiaomi.xmsf.stock.StockProfileIdStore
import io.github.magisk317.xposed.logging.MagiskOtel

object StalePackagePushGuard {
    private const val TAG = "StalePackagePushGuard"

    @JvmStatic
    fun onPackageRemoved(
        context: Context,
        packageName: String,
        source: String,
        userId: Int = Utils.myUserId().coerceAtLeast(0),
    ) {
        if (!shouldGuardPackage(context, packageName)) return
        markPackageAbsent(context, packageName, source, clearLastReceiveTime = true, userId = userId)
    }

    @JvmStatic
    fun shouldDropInbound(context: Context, container: XmPushActionContainer, source: String): Boolean {
        val targetPackage = resolveTargetPackage(container) ?: return false
        if (!isPackageAbsent(context, targetPackage)) return false
        markPackageAbsent(context, targetPackage, source, clearLastReceiveTime = false)
        MagiskOtel.event(
            name = "push.package",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "stale_drop",
                "target_package" to targetPackage,
                "source" to source,
                "reason" to "drop_inbound",
            ),
            statusOk = true,
        )
        return true
    }

    @JvmStatic
    fun shouldDropNotification(context: Context, container: XmPushActionContainer, source: String): Boolean {
        val targetPackage = resolveTargetPackage(container) ?: return false
        if (!isPackageAbsent(context, targetPackage)) return false
        markPackageAbsent(context, targetPackage, source, clearLastReceiveTime = false)
        MagiskOtel.event(
            name = "push.package",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "stale_drop",
                "target_package" to targetPackage,
                "source" to source,
                "reason" to "drop_notification",
            ),
            statusOk = true,
        )
        return true
    }

    @JvmStatic
    internal fun resolveTargetPackage(container: XmPushActionContainer): String? {
        val miuiTargetPackage = container.metaInfo?.extra
            ?.get(MIPushNotificationHelper.MIUI_PACKAGE_NAME)
            ?.takeIf { it.isNotBlank() }
        if (miuiTargetPackage != null) {
            return miuiTargetPackage
        }
        return runCatching { MIPushNotificationHelper.getTargetPackage(container) }
            .getOrNull()
            ?: container.packageName
    }

    private fun isPackageAbsent(context: Context, packageName: String?): Boolean {
        if (!shouldGuardPackage(context, packageName)) return false
        return !Utils.isAppInstalled(context, packageName!!)
    }

    private fun shouldGuardPackage(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (packageName == context.packageName) return false
        if (packageName == PushConstants.PUSH_SERVICE_PACKAGE_NAME) return false
        return true
    }

    private fun markPackageAbsent(
        context: Context,
        packageName: String,
        source: String,
        clearLastReceiveTime: Boolean,
        userId: Int = Utils.myUserId().coerceAtLeast(0),
    ) {
        val normalizedUserId = userId.coerceAtLeast(0)
        fun cleanup(name: String, block: () -> Unit) {
            runCatching(block).onFailure {
                Napier.w(
                    "package absent cleanup failed step=$name pkg=$packageName user=$normalizedUserId",
                    it,
                    tag = TAG,
                )
            }
        }
        cleanup("absent_registry") {
            MIPushAppInfo.getInstance(context).addUnRegisteredPkg(packageName)
            RegisteredApplicationDb.markUnregistered(packageName, normalizedUserId)
        }
        cleanup("profile_ids") { StockProfileIdStore.clear(context, packageName) }
        cleanup("registration_secret") { Utils.removeRegSec(packageName, normalizedUserId) }
        if (clearLastReceiveTime) {
            cleanup("last_receive_time") { Utils.removeLastReceiveTime(packageName, normalizedUserId) }
        }
        cleanup("registration_record_deduper") {
            RegistrationRecordDeduper.reset(packageName, normalizedUserId)
        }
        cleanup("stock_notifications") { MIPushNotificationHelper.clearNotification(context, packageName) }
        // Package removal can bypass the package-data-cleared callback. Clear every product-owned
        // transient surface here as well, otherwise an old notification/session can outlive the
        // package and be reused if the same package name is later installed again.
        cleanup("payload_deduplication") {
            StockMiPushPayloadDeduper.clearPackageState(packageName, normalizedUserId)
            MyMIPushNotificationHelper.clearPackageTransientState(packageName, normalizedUserId)
        }
        cleanup("notification_dispatch_allowance") {
            MiPushRuntimeBridge.clearPackageTransientState(packageName, normalizedUserId)
        }
        cleanup("runtime_state") {
            PushRuntime.clearPackageTransientState(packageName, normalizedUserId)
        }
        cleanup("top_notification_state") {
            TopNotificationCoordinator.clearPackageState(context, packageName, normalizedUserId)
        }
        cleanup("sweet_notification_state") {
            SweetNotificationCoordinator.clearPackageState(context, packageName, normalizedUserId)
        }
        cleanup("voip_notification_state") {
            VoipNotificationHelper.clearPackageState(packageName, normalizedUserId)
        }
        cleanup("conversation_history") {
            MyMIPushNotificationStyleSupport.clearConversationHistories(packageName, normalizedUserId)
        }
        cleanup("media_sessions") {
            NativeNotificationFeatureBuilder.clearPackageState(packageName, normalizedUserId)
        }
        cleanup("extension_notification_state") {
            ExtensionNotificationCoordinator.clearPackageState(packageName, normalizedUserId)
        }
        cleanup("keep_alive_state") {
            KeepAliveRuntimeAdapter.clearPackageState(context, packageName, normalizedUserId)
        }
        cleanup("runtime_observation") {
            PushRuntime.observeUnregistration(
                packageName = packageName,
                source = source,
                reason = "package_absent",
            )
        }
        Napier.i("marked absent package pkg=$packageName source=$source", tag = TAG)
        MagiskOtel.event(
            name = "push.package",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "stale",
                "target_package" to packageName,
                "source" to source,
                "reason" to "package_absent",
            ),
            statusOk = true,
        )
    }
}
