package com.xiaomi.push.mpcd

interface CDActionProvider {
    fun getRegSecret(): String?
    fun uploadNotification(
        xmPushActionNotification: com.xiaomi.xmpush.thrift.XmPushActionNotification,
        actionType: com.xiaomi.xmpush.thrift.ActionType,
        pushMetaInfo: com.xiaomi.xmpush.thrift.PushMetaInfo?
    )
}
