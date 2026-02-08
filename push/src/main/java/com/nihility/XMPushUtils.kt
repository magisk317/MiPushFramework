@file:Suppress("DEPRECATION")
package com.nihility
// Compatibility shim: legacy namespace forwarding to com.magisk317.*

import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import org.apache.thrift.TBase
import top.trumeet.common.utils.CustomConfiguration

object XMPushUtils {
    @JvmStatic
    fun getConfiguration(container: XmPushActionContainer?): CustomConfiguration =
        com.magisk317.XMPushUtils.getConfiguration(container)

    @JvmStatic
    fun getConfiguration(metaInfo: PushMetaInfo?): CustomConfiguration =
        com.magisk317.XMPushUtils.getConfiguration(metaInfo)

    @JvmStatic
    fun packToContainer(payload: ByteArray?): XmPushActionContainer? =
        com.magisk317.XMPushUtils.packToContainer(payload)

    @JvmStatic
    fun packToContainer(
        action: XmPushActionNotification,
        packageName: String
    ): XmPushActionContainer =
        com.magisk317.XMPushUtils.packToContainer(action, packageName)

    @JvmStatic
    fun <T : TBase<T, *>> packToContainer(
        action: T,
        packageName: String,
        actionType: ActionType,
        appId: String?
    ): XmPushActionContainer =
        com.magisk317.XMPushUtils.packToContainer(action, packageName, actionType, appId)

    @JvmStatic
    fun <T : TBase<T, *>> packToBytes(container: T): ByteArray =
        com.magisk317.XMPushUtils.packToBytes(container)
}
