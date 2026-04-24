package com.xiaomi.push.mpcd
import io.github.magisk317.mipush.protocol.model.*

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import com.xiaomi.push.mpcd.job.CollectionJob
import com.xiaomi.xmpush.thrift.ClientCollectionType
import com.xiaomi.xmpush.thrift.DataCollectionItem

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
