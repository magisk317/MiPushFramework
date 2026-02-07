package com.magisk317

import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import org.apache.thrift.TBase
import top.trumeet.common.utils.CustomConfiguration

/**
 * Transitional facade for future package migration.
 * Existing behavior is delegated to com.nihility.XMPushUtils.
 */
object XMPushUtils {
    @JvmStatic
    fun getConfiguration(container: XmPushActionContainer?): CustomConfiguration =
        com.nihility.XMPushUtils.getConfiguration(container)

    @JvmStatic
    fun getConfiguration(metaInfo: PushMetaInfo?): CustomConfiguration =
        com.nihility.XMPushUtils.getConfiguration(metaInfo)

    @JvmStatic
    fun packToContainer(payload: ByteArray?): XmPushActionContainer? =
        com.nihility.XMPushUtils.packToContainer(payload)

    @JvmStatic
    fun packToContainer(
        action: XmPushActionNotification,
        packageName: String
    ): XmPushActionContainer =
        com.nihility.XMPushUtils.packToContainer(action, packageName)

    @JvmStatic
    fun <T : TBase<T, *>> packToContainer(
        action: T,
        packageName: String,
        actionType: ActionType,
        appId: String?
    ): XmPushActionContainer =
        com.nihility.XMPushUtils.packToContainer(action, packageName, actionType, appId)

    @JvmStatic
    fun <T : TBase<T, *>> packToBytes(container: T): ByteArray =
        com.nihility.XMPushUtils.packToBytes(container)
}

