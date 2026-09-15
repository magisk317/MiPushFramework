package com.xiaomi.push.service.awake.module

import android.app.Service
import android.content.Context
import android.content.Intent
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.ComponentHelper
import com.xiaomi.push.service.awake.AwakeDataHelper
import com.xiaomi.push.service.awake.AwakeUploadHelper

/*
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
internal class ServiceActionAwakeModule : IAwakeModule {
    private fun awakeByServiceAction(context: Context, awakeInfo: AwakeInfo) {
        val targetPackageName = awakeInfo.targetPackageName
        val action = awakeInfo.action
        val awakeInfoStr = awakeInfo.awakeInfo
        val awakeForeground = awakeInfo.awakeForeground

        if (targetPackageName.isNullOrEmpty() || action.isNullOrEmpty() || awakeInfoStr.isNullOrEmpty()) {
            val logContent = if (awakeInfoStr.isNullOrEmpty()) "service" else awakeInfoStr
            AwakeUploadHelper.uploadData(context, logContent, 1008, "argument error")
            return
        }

        if (!ComponentHelper.checkService(context, targetPackageName, action)) {
            AwakeUploadHelper.uploadData(context, awakeInfoStr, 1003, "B is not ready")
            return
        }

        AwakeUploadHelper.uploadData(context, awakeInfoStr, 1002, "B is ready")
        AwakeUploadHelper.uploadData(context, awakeInfoStr, 1004, "A is ready")

        try {
            val intent = Intent().apply {
                this.action = action
                setPackage(targetPackageName)
                putExtra(AwakeUploadHelper.KEY_AWAKE_INFO, AwakeDataHelper.encode(awakeInfoStr))
            }

            if (awakeForeground == 1 && !AwakeManager.isMeForeground(context)) {
                AwakeUploadHelper.uploadData(context, awakeInfoStr, 1008, "A not in foreground")
            } else if (context.startService(intent) == null) {
                AwakeUploadHelper.uploadData(context, awakeInfoStr, 1008, "A is fail to help B's service")
            } else {
                AwakeUploadHelper.uploadData(context, awakeInfoStr, 1005, "A is successful")
                AwakeUploadHelper.uploadData(context, awakeInfoStr, 1006, "The job is finished")
            }
        } catch (e: Exception) {
            MyLog.e(e)
            AwakeUploadHelper.uploadData(context, awakeInfoStr, 1008, "A meet a exception when help B's service")
        }
    }

    private fun parseService(service: Service, intent: Intent) {
        val stringExtra = intent.getStringExtra(AwakeUploadHelper.KEY_AWAKE_INFO)
        if (stringExtra.isNullOrEmpty()) {
            AwakeUploadHelper.uploadData(service.applicationContext, "service", 1008, "B get a incorrect message")
            return
        }
        val strDecode = AwakeDataHelper.decode(stringExtra)
        if (strDecode.isEmpty()) {
            AwakeUploadHelper.uploadData(service.applicationContext, "service", 1008, "B get a incorrect message")
        } else {
            AwakeUploadHelper.uploadData(service.applicationContext, strDecode, 1007, "play with service successfully")
        }
    }

    override fun doAwake(context: Context, awakeInfo: AwakeInfo) {
        awakeByServiceAction(context, awakeInfo)
    }

    override fun doSendAwakeResult(context: Context, intent: Intent, str: String) {
        if (context !is Service) {
            AwakeUploadHelper.uploadData(context, "service", 1008, "A receive incorrect message")
        } else {
            parseService(context, intent)
        }
    }
}
