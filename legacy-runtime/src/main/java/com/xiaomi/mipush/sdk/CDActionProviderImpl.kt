package com.xiaomi.mipush.sdk

import android.content.Context
import com.xiaomi.push.mpcd.CDActionProvider
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionNotification

class CDActionProviderImpl(context: Context) : CDActionProvider {
    private val mContext: Context = context

    override fun getRegSecret(): String {
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
