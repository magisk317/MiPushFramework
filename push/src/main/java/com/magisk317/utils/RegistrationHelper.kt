package com.magisk317.utils

import android.content.Context
import android.content.Intent
import com.elvishew.xlog.XLog
import com.magisk317.XMPushUtils
import com.topjohnwu.superuser.Shell
import com.xiaomi.push.sdk.MyPushMessageHandler
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import top.trumeet.common.utils.Utils

class RegistrationHelper(
    private val context: Context,
    private val packageName: String
) {
    fun removeMiPushXml(): Boolean {
        val result = Shell.cmd(
            String.format(
                "rm $(ls -1" +
                    " /data/user/0/%s/shared_prefs/mipush*.xml" +
                    " /data_mirror/data_ce/null/0/%s/shared_prefs/mipush*.xml" +
                    " 2> /dev/null)",
                packageName,
                packageName
            )
        ).exec()
        return result.isSuccess
    }

    fun deleteRegistrationInfoAndRetryForceRegister() {
        removeMiPushXml()
        MyPushMessageHandler.launchApp(context, createForceRegisterMessage(packageName))
        tryForceRegister(packageName)
    }

    companion object {
        private val logger = XLog.tag("RegistrationHelper").build()

        @JvmStatic
        fun tryForceRegisterFallback(packageName: String): Boolean {
            val msgBytes = runCatching {
                XMPushUtils.packToBytes(createForceRegisterMessage(packageName))
            }.getOrNull() ?: return false
            val intent = Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE).apply {
                `package` = packageName
                putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, msgBytes)
                putExtra(PushConstants.MESSAGE_RECEIVE_TIME, System.currentTimeMillis())
            }
            Utils.getApplication()?.sendBroadcast(intent, null)
            return true
        }

        @JvmStatic
        fun tryForceRegister(packageName: String) {
            val app = Utils.getApplication() ?: return
            val container = createForceRegisterMessage(packageName)
            val msgBytes = XMPushUtils.packToBytes(container)
            // Prefer direct handler dispatch without launching/settings guidance side effects.
            val started = runCatching {
                MyPushMessageHandler.forwardToTargetApplication(app, msgBytes)
            }.getOrNull()
            if (started != null) {
                logger.i("force register via PushMessageHandler succeeded: $packageName")
                return
            }
            // Fallback to package-targeted broadcast for apps with nonstandard handlers.
            val intent = Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE).apply {
                `package` = packageName
                putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, msgBytes)
                putExtra(PushConstants.MESSAGE_RECEIVE_TIME, System.currentTimeMillis())
            }
            app.sendBroadcast(intent, null)
            logger.w("force register fell back to broadcast only: $packageName")
        }

        @JvmStatic
        fun createForceRegisterMessage(packageName: String): XmPushActionContainer {
            val id = "fake_expired_${packageName}_${System.currentTimeMillis()}"
            val regIdExpiredNotification = XmPushActionNotification().apply {
                type = NotificationType.RegIdExpired.value
                setId(id)
            }
            val metaInfo = PushMetaInfo().apply { setId(id) }
            val regIdExpiredContainer = XMPushUtils.packToContainer(regIdExpiredNotification, packageName)
            regIdExpiredContainer.metaInfo = metaInfo
            return regIdExpiredContainer
        }
    }
}
