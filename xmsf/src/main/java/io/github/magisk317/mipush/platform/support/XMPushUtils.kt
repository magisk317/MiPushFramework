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
        } catch (e: PackageManager.NameNotFoundException) {
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

    @JvmStatic
    fun dispatchToApplication(
        context: Context,
        packageName: String,
        payload: ByteArray,
        fromNotification: Boolean = false
    ): Boolean {
        if (packageName.isBlank()) return false
        
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
        val started = runCatching { context.startService(serviceIntent) }.getOrNull()
        if (started != null) {
            return true
        }

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
            return dispatched
        }

        // 3. Final generic broadcast
        return runCatching {
            context.sendBroadcast(intent)
            true
        }.getOrDefault(false)
    }
}
