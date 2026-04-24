package com.xiaomi.mipush.sdk
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.reflect.JavaCalls

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
        return JavaCalls.callStaticMethod(manageClassInfo.className, manageClassInfo.methodName, context) as AbstractPushManager
    }
}
