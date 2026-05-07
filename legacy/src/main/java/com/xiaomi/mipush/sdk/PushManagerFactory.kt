package com.xiaomi.mipush.sdk

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.reflect.JavaCalls

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/PushManagerFactory.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
object PushManagerFactory {
    @JvmStatic
    fun getManager(context: Context, assemblePush: AssemblePush): AbstractPushManager? {
        return getManagerByType(context, assemblePush)
    }

    private fun getManagerByType(context: Context, assemblePush: AssemblePush): AbstractPushManager? {
        val manageClassInfo = AssemblePushInfoHelper.getManageClassInfoByType(assemblePush)
            ?: return null
        if (TextUtils.isEmpty(manageClassInfo.className) || TextUtils.isEmpty(manageClassInfo.methodName)) {
            return null
        }
        return JavaCalls.callStaticMethod(manageClassInfo.className!!, manageClassInfo.methodName!!, context) as AbstractPushManager
    }
}
