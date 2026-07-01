package io.github.magisk317.mipush.platform.support

import io.github.magisk317.mipush.push.hook.HookTraceCompat
import com.xiaomi.push.service.MIPushEventProcessor
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.Target
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import io.github.magisk317.mipush.notification.NotificationManagerEx
import org.apache.thrift.TBase
import io.github.magisk317.mipush.common.utils.CustomConfiguration
import io.github.magisk317.mipush.common.utils.Utils

/**
 * XM 推送工具类
 * 
 * 提供推送容器的打包和解析功能
 */
object XMPushUtils {
    @JvmStatic
    fun getConfiguration(container: XmPushActionContainer?): CustomConfiguration {
        if (container == null) {
            return CustomConfiguration(null)
        }
        return getConfiguration(container.metaInfo)
    }

    @JvmStatic
    fun getConfiguration(metaInfo: PushMetaInfo?): CustomConfiguration {
        if (metaInfo == null) {
            return CustomConfiguration(null)
        }
        return CustomConfiguration(metaInfo.extra)
    }

    @JvmStatic
    fun getPackageContext(context: Context, packageName: String, flags: Int = 0): Context {
        if (!NotificationManagerEx.isHooked) {
            return context
        }
        return try {
            context.createPackageContext(packageName, flags)
        } catch (_: PackageManager.NameNotFoundException) {
            context
        }
    }

    @JvmStatic
    fun packToContainer(payload: ByteArray?): XmPushActionContainer? {
        if (payload == null) {
            return null
        }
        val container = MIPushEventProcessor.buildContainer(payload)
        HookTraceCompat.onBuildContainer(payload.size, container)
        return container
    }

    @JvmStatic
    fun packToContainer(
        action: XmPushActionNotification,
        packageName: String
    ): XmPushActionContainer = packToContainer(action, packageName, ActionType.Notification, action.appId)

    @JvmStatic
    fun <T : TBase<T, *>> packToContainer(
        action: T,
        packageName: String,
        actionType: ActionType,
        appId: String?
    ): XmPushActionContainer {
        val payload = XmPushThriftSerializeUtils.convertThriftObjectToBytes(action)
            ?: throw IllegalArgumentException("Unable to serialize push action: ${action.javaClass.name}")
        val container = XmPushActionContainer().apply {
            target = Target().apply {
                channelId = 5L
                userId = "fakeid"
            }
            setPushAction(payload)
            this.action = actionType
            isRequest = true
            this.packageName = packageName
            setEncryptAction(false)
            appid = appId
        }
        HookTraceCompat.onBuildContainer(0, container)
        return container
    }

    @JvmStatic
    fun <T : TBase<T, *>> packToBytes(container: T): ByteArray =
        XmPushThriftSerializeUtils.convertThriftObjectToBytes(container)
            ?: throw IllegalArgumentException("Unable to serialize: ${container.javaClass.name}")

    /**
     * Observable outcome of a downstream dispatch attempt. Callers must not treat a broadcast
     * fallback as an equivalent success for apps that rely on `PushMessageHandler` (e.g. QQ): a
     * generic broadcast can be delivered by the system yet never reach the SDK message pipeline.
     */
    sealed class DispatchResult {
        /** Explicit `startService` to the target `PushMessageHandler` returned a component. */
        object ServiceStarted : DispatchResult()

        /**
         * `startService` was refused by the platform (null return or an exception such as
         * Samsung/AOSP "Background start not allowed"). No broadcast fallback succeeded either.
         */
        data class ServiceBlocked(val cause: Throwable? = null) : DispatchResult()

        /** Service start did not succeed, but a broadcast fallback was sent. */
        data class BroadcastSent(val explicit: Boolean) : DispatchResult()

        /** Nothing could be delivered. */
        object Failed : DispatchResult()

        val dispatched: Boolean
            get() = this is ServiceStarted || this is BroadcastSent
    }

    @JvmStatic
    fun dispatchToApplication(
        context: Context,
        packageName: String,
        payload: ByteArray,
        fromNotification: Boolean = false
    ): Boolean = dispatchToApplicationResult(context, packageName, payload, fromNotification).dispatched

    @JvmStatic
    fun dispatchToApplicationResult(
        context: Context,
        packageName: String,
        payload: ByteArray,
        fromNotification: Boolean = false
    ): DispatchResult {
        if (packageName.isBlank()) return DispatchResult.Failed

        val intent = Intent("com.xiaomi.mipush.RECEIVE_MESSAGE").apply {
            `package` = packageName
            putExtra("mipush_payload", payload)
            putExtra("mipush_receive_time", System.currentTimeMillis())
            if (fromNotification) {
                putExtra("from_notification", true)
            }
            // Try to add category if it's a notification
            val notifyId = packToContainer(payload)?.metaInfo?.notifyId
            if (notifyId != null) {
                addCategory(notifyId.toString())
            }
        }

        // 1. Try explicit service dispatch (PushMessageHandler)
        val serviceIntent = Intent(intent).apply {
            component = android.content.ComponentName(packageName, io.github.magisk317.mipush.common.Constants.PUSH_MESSAGE_HANDLER_CLASS)
        }
        val serviceStart = runCatching { context.startService(serviceIntent) }
        val startedComponent = serviceStart.getOrNull()
        if (startedComponent != null) {
            return DispatchResult.ServiceStarted
        }
        val serviceStartError = serviceStart.exceptionOrNull()

        // 2. Fallback to broadcast dispatch
        // Query explicit receivers first to bypass some restrictions or for logging
        val explicitReceivers = runCatching {
            context.packageManager.queryBroadcastReceivers(
                intent,
                PackageManager.MATCH_DISABLED_COMPONENTS
            )
        }.getOrDefault(emptyList())
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                if (activityInfo.packageName != packageName) return@mapNotNull null
                val canDispatch = activityInfo.enabled &&
                    (activityInfo.exported || activityInfo.packageName == context.packageName)
                if (!canDispatch) return@mapNotNull null
                android.content.ComponentName(activityInfo.packageName, activityInfo.name)
            }
            .distinct()

        if (explicitReceivers.isNotEmpty()) {
            var dispatched = false
            for (component in explicitReceivers) {
                val explicitIntent = Intent(intent).apply {
                    this.component = component
                    `package` = null
                }
                val delivered = runCatching {
                    context.sendBroadcast(explicitIntent)
                    true
                }.getOrDefault(false)
                dispatched = dispatched || delivered
            }
            if (dispatched) {
                return DispatchResult.BroadcastSent(explicit = true)
            }
        }

        // 3. Final generic broadcast
        val genericSent = runCatching {
            context.sendBroadcast(intent)
            true
        }.getOrDefault(false)
        return if (genericSent) {
            DispatchResult.BroadcastSent(explicit = false)
        } else {
            DispatchResult.ServiceBlocked(serviceStartError)
        }
    }
}
