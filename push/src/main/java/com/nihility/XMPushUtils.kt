package com.nihility

import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.mipush.sdk.PushContainerHelper
import com.xiaomi.push.service.MIPushEventProcessor
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import org.apache.thrift.TBase
import top.trumeet.common.utils.CustomConfiguration
import top.trumeet.common.utils.Utils

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
        return MIPushEventProcessor.buildContainer(payload)
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
        return JavaCalls.callStaticMethod(
            PushContainerHelper::class.java.name,
            "generateRequestContainer",
            Utils.getApplication(),
            action,
            actionType,
            false,
            packageName,
            appId
        )
    }

    @JvmStatic
    fun <T : TBase<T, *>> packToBytes(container: T): ByteArray =
        XmPushThriftSerializeUtils.convertThriftObjectToBytes(container)
}
