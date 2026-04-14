package io.github.magisk317.mipush.push.hook

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.utils.Configurations

object BroadcastDecision {
    @JvmStatic
    fun shouldSendBroadcast(
        context: Context,
        packageName: String,
        container: XmPushActionContainer,
        metaInfo: PushMetaInfo
    ): Boolean {
        if (container.action == ActionType.Registration) return true
        if (container.packageName.startsWith("com.mi.") ||
            container.packageName.startsWith("com.miui.") ||
            container.packageName.startsWith("com.xiaomi.")
        ) {
            return true
        }
        val decoratedMetaInfo = decoratedContainer(container.packageName, container).metaInfo
        return checkAwakeField(decoratedMetaInfo) ||
            isSystemApp(context, packageName) ||
            AppInfoUtils.isAppRunning(context, packageName)
    }

    private fun decoratedContainer(realTargetPackage: String, container: XmPushActionContainer): XmPushActionContainer {
        val decorated = container.deepCopy()
        runCatching {
            Configurations.getInstance().handle(realTargetPackage, decorated)
        }
        return decorated
    }

    private fun isSystemApp(context: Context, packageName: String): Boolean {
        return try {
            val flags = context.packageManager.getApplicationInfo(packageName, 0).flags
            (flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun checkAwakeField(metaInfo: PushMetaInfo?): Boolean {
        val extra = metaInfo?.extra ?: return false
        return java.lang.Boolean.parseBoolean(extra[PushConstants.EXTRA_PARAM_AWAKE])
    }
}
