package com.nihility.utils

import android.content.Context
import android.content.Intent
import com.nihility.XMPushUtils
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
        @JvmStatic
        fun tryForceRegister(packageName: String) {
            val msgBytes = XMPushUtils.packToBytes(createForceRegisterMessage(packageName))
            val intent = Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE).apply {
                `package` = packageName
                putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, msgBytes)
                putExtra(PushConstants.MESSAGE_RECEIVE_TIME, System.currentTimeMillis())
            }
            Utils.getApplication()?.sendBroadcast(intent, null)
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
