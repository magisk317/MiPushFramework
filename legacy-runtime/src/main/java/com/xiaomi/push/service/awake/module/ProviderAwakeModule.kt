package com.xiaomi.push.service.awake.module

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.ComponentHelper
import com.xiaomi.push.service.awake.AwakeDataHelper
import com.xiaomi.push.service.awake.AwakeUploadHelper

internal class ProviderAwakeModule : IAwakeModule {
    private fun awakeByProvider(context: Context, awakeInfo: AwakeInfo) {
        val action = awakeInfo.action
        val awakeInfoStr = awakeInfo.awakeInfo
        val awakeForeground = awakeInfo.awakeForeground

        if (TextUtils.isEmpty(action) || TextUtils.isEmpty(awakeInfoStr)) {
            val logContent = if (TextUtils.isEmpty(awakeInfoStr)) "provider" else awakeInfoStr!!
            AwakeUploadHelper.uploadData(context, logContent, 1008, "argument error")
            return
        }

        if (!ComponentHelper.checkProvider(context, action!!)) {
            AwakeUploadHelper.uploadData(context, awakeInfoStr!!, 1003, "B is not ready")
            return
        }

        AwakeUploadHelper.uploadData(context, awakeInfoStr!!, 1002, "B is ready")
        AwakeUploadHelper.uploadData(context, awakeInfoStr, 1004, "A is ready")

        val strEncode = AwakeDataHelper.encode(awakeInfoStr)
        try {
            if (TextUtils.isEmpty(strEncode)) {
                AwakeUploadHelper.uploadData(context, awakeInfoStr, 1008, "info is empty")
                return
            }
            if (awakeForeground == 1 && !AwakeManager.isMeForeground(context)) {
                AwakeUploadHelper.uploadData(context, awakeInfoStr, 1008, "A not in foreground")
                return
            }

            val type = context.contentResolver.getType(AwakeDataHelper.getContentUri(action, strEncode))
            if (TextUtils.isEmpty(type) || "success" != type) {
                AwakeUploadHelper.uploadData(context, awakeInfoStr, 1008, "A is fail to help B's provider")
            } else {
                AwakeUploadHelper.uploadData(context, awakeInfoStr, 1005, "A is successful")
                AwakeUploadHelper.uploadData(context, awakeInfoStr, 1006, "The job is finished")
            }
        } catch (e: Exception) {
            MyLog.e(e)
            AwakeUploadHelper.uploadData(context, awakeInfoStr, 1008, "A meet a exception when help B's provider")
        }
    }

    private fun parseProvider(context: Context, str: String?) {
        try {
            if (TextUtils.isEmpty(str)) {
                AwakeUploadHelper.uploadData(context, "provider", 1008, "B get a incorrect message")
                return
            }

            val strArrSplit = str!!.split("/")
            if (strArrSplit.isEmpty() || TextUtils.isEmpty(strArrSplit.last())) {
                AwakeUploadHelper.uploadData(context, "provider", 1008, "B get a incorrect message")
                return
            }

            val lastPart = strArrSplit.last()
            if (TextUtils.isEmpty(lastPart)) {
                AwakeUploadHelper.uploadData(context, "provider", 1008, "B get a incorrect message")
                return
            }

            val strDecode = Uri.decode(lastPart)
            if (TextUtils.isEmpty(strDecode)) {
                AwakeUploadHelper.uploadData(context, "provider", 1008, "B get a incorrect message")
                return
            }

            val strDecode2 = AwakeDataHelper.decode(strDecode)
            if (TextUtils.isEmpty(strDecode2)) {
                AwakeUploadHelper.uploadData(context, "provider", 1008, "B get a incorrect message")
            } else {
                AwakeUploadHelper.uploadData(context, strDecode2, 1007, "play with provider successfully")
            }
        } catch (e: Exception) {
            AwakeUploadHelper.uploadData(context, "provider", 1008, "B meet a exception${e.message}")
        }
    }

    override fun doAwake(context: Context, awakeInfo: AwakeInfo) {
        awakeByProvider(context, awakeInfo)
    }

    override fun doSendAwakeResult(context: Context, intent: Intent, str: String) {
        parseProvider(context, str)
    }
}
