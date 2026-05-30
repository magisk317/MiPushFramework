package com.xiaomi.push.mpcd

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/push/mpcd/CDActionProvider.java
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
