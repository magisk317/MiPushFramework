package com.xiaomi.push.mpcd

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import com.xiaomi.push.mpcd.job.CollectionJob
import com.xiaomi.xmpush.thrift.ClientCollectionType
import com.xiaomi.xmpush.thrift.DataCollectionItem

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/u9/a.java
 * Current override same-path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/push/mpcd/ActivityLifecycleCallbacksImpl.java
 * Stock class name is obfuscated as u9.a; this file keeps the deobfuscated activity timestamp collector API.
 */
class ActivityLifecycleCallbacksImpl(
    private val mContext: Context,
    private var mActiveStartTS: String
) : Application.ActivityLifecycleCallbacks {
    private var mCurrentActiveActivity: String = ""

    private fun writeData(data: String) {
        val item = DataCollectionItem()
        item.content = data
        item.collectedAt = System.currentTimeMillis()
        item.collectionType = ClientCollectionType.ActivityActiveTimeStamp
        CollectionJob.writeItemToFile(mContext, item)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityDestroyed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {
        val localClassName = activity.localClassName
        if (TextUtils.isEmpty(mActiveStartTS) || TextUtils.isEmpty(localClassName)) return
        mCurrentActiveActivity = ""
        if (!TextUtils.isEmpty("") && !TextUtils.equals(mCurrentActiveActivity, localClassName)) {
            mActiveStartTS = ""
            return
        }
        writeData("${mContext.packageName}${Constants.TYPE_SEPARATOR}$localClassName:$mActiveStartTS,${System.currentTimeMillis() / 1000}")
        mActiveStartTS = ""
        mCurrentActiveActivity = ""
    }

    override fun onActivityResumed(activity: Activity) {
        if (TextUtils.isEmpty(mCurrentActiveActivity)) {
            mCurrentActiveActivity = activity.localClassName
        }
        mActiveStartTS = (System.currentTimeMillis() / 1000).toString()
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}
}
