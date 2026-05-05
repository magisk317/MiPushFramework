package com.xiaomi.push.service.awake.module

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.ComponentHelper
import com.xiaomi.push.service.awake.AwakeDataHelper
import com.xiaomi.push.service.awake.AwakeUploadHelper

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/push/service/awake/module/ActivityActionAwakeModule.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
internal class ActivityActionAwakeModule : IAwakeModule {
    private fun awakeByActivity(context: Context, awakeInfo: AwakeInfo) {
        val targetPackageName = awakeInfo.targetPackageName
        val action = awakeInfo.action
        val awakeInfoStr = awakeInfo.awakeInfo
        val awakeForeground = awakeInfo.awakeForeground

        if (TextUtils.isEmpty(targetPackageName) || TextUtils.isEmpty(action) || TextUtils.isEmpty(awakeInfoStr)) {
            val logContent = if (TextUtils.isEmpty(awakeInfoStr)) "activity" else awakeInfoStr!!
            AwakeUploadHelper.uploadData(context, logContent, 1008, "argument error")
            return
        }

        if (!ComponentHelper.checkActivity(context, targetPackageName!!, action!!)) {
            AwakeUploadHelper.uploadData(context, awakeInfoStr!!, 1003, "B is not ready")
            return
        }

        AwakeUploadHelper.uploadData(context, awakeInfoStr!!, 1002, "B is ready")
        AwakeUploadHelper.uploadData(context, awakeInfoStr, 1004, "A is ready")

        val intent = Intent(action).apply {
            setPackage(targetPackageName)
            putExtra(AwakeUploadHelper.KEY_AWAKE_INFO, AwakeDataHelper.encode(awakeInfoStr))
            addFlags(276824064)
        }

        if (awakeForeground == 1) {
            try {
                if (!AwakeManager.isMeForeground(context)) {
                    AwakeUploadHelper.uploadData(context, awakeInfoStr, 1008, "A not in foreground")
                    return
                }
            } catch (e: Exception) {
                MyLog.e(e)
                AwakeUploadHelper.uploadData(context, awakeInfoStr, 1008, "A meet a exception when help B's activity")
                return
            }
        }

        context.startActivity(intent)
        AwakeUploadHelper.uploadData(context, awakeInfoStr, 1005, "A is successful")
        AwakeUploadHelper.uploadData(context, awakeInfoStr, 1006, "The job is finished")
    }

    private fun parseActivity(activity: Activity, intent: Intent) {
        val stringExtra = intent.getStringExtra(AwakeUploadHelper.KEY_AWAKE_INFO)
        if (TextUtils.isEmpty(stringExtra)) {
            AwakeUploadHelper.uploadData(activity.applicationContext, "activity", 1008, "B get incorrect message")
            return
        }
        val strDecode = AwakeDataHelper.decode(stringExtra!!)
        if (TextUtils.isEmpty(strDecode)) {
            AwakeUploadHelper.uploadData(activity.applicationContext, "activity", 1008, "B get incorrect message")
        } else {
            AwakeUploadHelper.uploadData(activity.applicationContext, strDecode, 1007, "play with activity successfully")
        }
    }

    override fun doAwake(context: Context, awakeInfo: AwakeInfo) {
        awakeByActivity(context, awakeInfo)
    }

    override fun doSendAwakeResult(context: Context, intent: Intent, str: String) {
        if (context !is Activity) {
            AwakeUploadHelper.uploadData(context, "activity", 1008, "B receive incorrect message")
        } else {
            parseActivity(context, intent)
        }
    }
}
