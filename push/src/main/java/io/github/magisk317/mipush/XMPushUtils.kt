package io.github.magisk317.mipush

import io.github.magisk317.mipush.push.hook.HookTraceCompat
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.mipush.sdk.PushContainerHelper
import com.xiaomi.push.service.MIPushEventProcessor
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import org.apache.thrift.TBase
import io.github.magisk317.mipush.common.utils.CustomConfiguration
import io.github.magisk317.mipush.common.utils.Utils

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
        val container = JavaCalls.callStaticMethod(
            PushContainerHelper::class.java.name,
            "generateRequestContainer",
            Utils.getApplication(),
            action,
            actionType,
            false,
            packageName,
            appId
        ) as XmPushActionContainer
        HookTraceCompat.onBuildContainer(0, container)
        return container
    }

    @JvmStatic
    fun <T : TBase<T, *>> packToBytes(container: T): ByteArray =
        XmPushThriftSerializeUtils.convertThriftObjectToBytes(container)
}
