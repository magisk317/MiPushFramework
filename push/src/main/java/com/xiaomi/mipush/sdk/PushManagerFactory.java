package com.xiaomi.mipush.sdk;

import android.content.Context;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.reflect.JavaCalls;
import com.xiaomi.mipush.sdk.AssemblePushInfoHelper;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/PushManagerFactory.class */
public class PushManagerFactory {
    public static AbstractPushManager getManager(Context context, AssemblePush assemblePush) {
        return getManagerByType(context, assemblePush);
    }

    private static AbstractPushManager getManagerByType(Context context, AssemblePush assemblePush) {
        AssemblePushInfoHelper.ManageClassInfo manageClassInfoByType = AssemblePushInfoHelper.getManageClassInfoByType(assemblePush);
        if (manageClassInfoByType == null || TextUtils.isEmpty(manageClassInfoByType.className) || TextUtils.isEmpty(manageClassInfoByType.methodName)) {
            return null;
        }
        return (AbstractPushManager) JavaCalls.callStaticMethod(manageClassInfoByType.className, manageClassInfoByType.methodName, context);
    }
}
