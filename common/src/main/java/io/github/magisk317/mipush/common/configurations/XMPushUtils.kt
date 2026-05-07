package io.github.magisk317.mipush.common.configurations

import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.Target
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import org.apache.thrift.TBase
import io.github.magisk317.mipush.common.utils.CustomConfiguration

/**
 * XM 推送核心工具类 (不依赖 Hook 逻辑)
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
    fun packToContainer(payload: ByteArray?): XmPushActionContainer? {
        if (payload == null) {
            return null
        }
        val container = XmPushActionContainer()
        try {
            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(container, payload)
            return container
        } catch (e: Exception) {
            return null
        }
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
        return XmPushActionContainer().apply {
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
    }

    @JvmStatic
    fun <T : TBase<T, *>> packToBytes(container: T): ByteArray? =
        XmPushThriftSerializeUtils.convertThriftObjectToBytes(container)
}
