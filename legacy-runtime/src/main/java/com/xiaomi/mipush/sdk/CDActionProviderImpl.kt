package com.xiaomi.mipush.sdk

import android.content.Context
import com.xiaomi.push.mpcd.CDActionProvider
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionNotification

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/CDActionProviderImpl.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
class CDActionProviderImpl(context: Context) : CDActionProvider {
    private val mContext: Context = context

    override fun getRegSecret(): String? {
        return AppInfoHolder.getInstance(mContext).regSecret
    }

    override fun uploadNotification(
        xmPushActionNotification: XmPushActionNotification,
        actionType: ActionType,
        pushMetaInfo: PushMetaInfo?
    ) {
        pushMetaInfo?.let {
            PushServiceClient.getInstance(mContext).sendMessage(xmPushActionNotification, actionType, it)
        }
    }
}
