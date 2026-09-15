package com.xiaomi.push.mpcd

/*
 * Stock 7.4.67-C routes the same role through com.xiaomi.mipush.sdk.i and holder u9.b rather than a same-path interface.
 */
interface CDActionProvider {
    fun getRegSecret(): String?
    fun uploadNotification(
        xmPushActionNotification: com.xiaomi.xmpush.thrift.XmPushActionNotification,
        actionType: com.xiaomi.xmpush.thrift.ActionType,
        pushMetaInfo: com.xiaomi.xmpush.thrift.PushMetaInfo?
    )
}
