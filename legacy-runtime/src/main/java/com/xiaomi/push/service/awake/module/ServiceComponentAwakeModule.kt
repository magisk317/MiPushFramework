package com.xiaomi.push.service.awake.module

import android.app.Service
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.ComponentHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.awake.AwakeDataHelper
import com.xiaomi.push.service.awake.AwakeUploadHelper

internal class ServiceComponentAwakeModule : IAwakeModule {
    private fun awakeByServiceName(context: Context, packageName: String, className: String, awakeInfoStr: String) {
        if (context == null || TextUtils.isEmpty(packageName) || TextUtils.isEmpty(className)) {
            val logContent = if (TextUtils.isEmpty(awakeInfoStr)) "service" else awakeInfoStr
            AwakeUploadHelper.uploadData(context, logContent, 1008, "argument error")
            return
        }

        if (!ComponentHelper.checkService(context, packageName)) {
            AwakeUploadHelper.uploadData(context, awakeInfoStr, 1003, "B is not ready")
            return
        }

        AwakeUploadHelper.uploadData(context, awakeInfoStr, 1002, "B is ready")
        AwakeUploadHelper.uploadData(context, awakeInfoStr, 1004, "A is ready")

        try {
            val intent = Intent().apply {
                setClassName(packageName, className)
                action = PushConstants.ACTION_WAKEUP
                putExtra(PushConstants.ACTION_WAKER_PKGNAME, context.packageName)
                putExtra(AwakeUploadHelper.KEY_AWAKE_INFO, AwakeDataHelper.encode(awakeInfoStr))
            }
            if (context.startService(intent) == null) {
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
        if (PushConstants.ACTION_WAKEUP == intent.action) {
            val wakerPkgName = intent.getStringExtra(PushConstants.ACTION_WAKER_PKGNAME)
            val awakeInfoStr = intent.getStringExtra(AwakeUploadHelper.KEY_AWAKE_INFO)

            if (TextUtils.isEmpty(wakerPkgName)) {
                AwakeUploadHelper.uploadData(service.applicationContext, "service", 1007, "old version message")
                return
            }
            if (TextUtils.isEmpty(awakeInfoStr)) {
                AwakeUploadHelper.uploadData(service.applicationContext, wakerPkgName!!, 1007, "play with service ")
                return
            }
            val strDecode = AwakeDataHelper.decode(awakeInfoStr!!)
            if (TextUtils.isEmpty(strDecode)) {
                AwakeUploadHelper.uploadData(service.applicationContext, "service", 1008, "B get a incorrect message")
            } else {
                AwakeUploadHelper.uploadData(service.applicationContext, strDecode, 1007, "old version message ")
            }
        }
    }

    override fun doAwake(context: Context, awakeInfo: AwakeInfo) {
        if (awakeInfo != null) {
            awakeByServiceName(
                context,
                awakeInfo.targetPackageName!!,
                awakeInfo.className!!,
                awakeInfo.awakeInfo!!
            )
        }
    }

    override fun doSendAwakeResult(context: Context, intent: Intent, str: String) {
        if (context == null || context !is Service) return
        parseService(context, intent)
    }
}
