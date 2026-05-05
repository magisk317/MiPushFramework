package com.xiaomi.mipush.sdk

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import com.xiaomi.push.service.clientReport.PushClientReportHelper
import com.xiaomi.push.service.clientReport.PushClientReportManager
import com.xiaomi.push.service.clientReport.ReportConstants

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/ActivityLifecycleCallbacksForCR.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
class ActivityLifecycleCallbacksForCR : Application.ActivityLifecycleCallbacks {
    private val mMsgIdSet: MutableSet<String> = HashSet()

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {}

    override fun onActivityDestroyed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {
        val intent = activity.intent ?: return
        val messageId = intent.getStringExtra("messageId")
        val eventMessageType = intent.getIntExtra(ReportConstants.EVENT_MESSAGE_TYPE, -1)
        if (TextUtils.isEmpty(messageId) || eventMessageType <= 0 || mMsgIdSet.contains(messageId)) {
            return
        }
        mMsgIdSet.add(messageId ?: "")
        if (eventMessageType == ReportConstants.AWAKE_TYPE) {
            PushClientReportManager.getInstance(activity.applicationContext).reportEvent(
                activity.packageName,
                PushClientReportHelper.getInterfaceIdByType(eventMessageType),
                messageId ?: "",
                ReportConstants.AWAKE_TYPE_APP_START,
                null
            )
        } else if (eventMessageType == ReportConstants.NOTIFICATION_TYPE) {
            PushClientReportManager.getInstance(activity.applicationContext).reportEvent(
                activity.packageName,
                PushClientReportHelper.getInterfaceIdByType(eventMessageType),
                messageId ?: "",
                ReportConstants.NOTIFICATION_TYPE_APP_START,
                null
            )
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {}

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    companion object {
        private fun attachApplication(application: Application) {
            application.registerActivityLifecycleCallbacks(ActivityLifecycleCallbacksForCR())
        }

        @JvmStatic
        fun forceAttachApplication(context: Context) {
            attachApplication(context.applicationContext as Application)
        }
    }
}
