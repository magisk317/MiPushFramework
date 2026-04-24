package com.xiaomi.push.mpcd
import io.github.magisk317.mipush.protocol.model.*

interface CDActionProvider {
    fun getRegSecret(): String?
    fun uploadNotification(
        xmPushActionNotification: com.xiaomi.xmpush.thrift.XmPushActionNotification,
        actionType: com.xiaomi.xmpush.thrift.ActionType,
        pushMetaInfo: com.xiaomi.xmpush.thrift.PushMetaInfo?
    )
}
