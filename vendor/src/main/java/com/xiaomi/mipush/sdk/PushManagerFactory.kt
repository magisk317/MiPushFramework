package com.xiaomi.mipush.sdk

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.reflect.JavaCalls

/*
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
        return try {
            callOptionalProbe {
                JavaCalls.callStaticMethodOrThrow(manageClassInfo.className!!, manageClassInfo.methodName!!, context)
            } as? AbstractPushManager
        } catch (e: Exception) {
            MyLog.e("assemble push manager load failed", e)
            null
        }
    }

    private inline fun <T> callOptionalProbe(block: () -> T): T? {
        return try {
            block()
        } catch (_: NoSuchMethodException) {
            null
        } catch (_: ClassNotFoundException) {
            null
        }
    }
}
