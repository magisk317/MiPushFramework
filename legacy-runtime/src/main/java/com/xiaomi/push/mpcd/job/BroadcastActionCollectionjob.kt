package com.xiaomi.push.mpcd.job

import android.content.Context
import android.text.TextUtils
import com.xiaomi.push.mpcd.Constants
import com.xiaomi.xmpush.thrift.ClientCollectionType

class BroadcastActionCollectionjob(context: Context, period: Int) : CollectionJob(context, period) {

    private fun shrinkActionInfo(prefix: String, actions: String): String {
        if (TextUtils.isEmpty(actions) || TextUtils.isEmpty(prefix)) return ""
        val parts = actions.split(",")
        if (parts.size <= 10) return actions
        var result = ""
        for (i in (parts.size - 1) downTo (parts.size - 10)) {
            result += parts[i]
        }
        return result
    }

    override fun collectInfo(): String {
        var result = ""
        if (mRestartedActions.isNotEmpty()) {
            result = shrinkActionInfo(Constants.ACTION_PACKAGE_RESTARTED, mRestartedActions)
            mRestartedActions = ""
        }
        if (mChangedActions.isNotEmpty()) {
            result += shrinkActionInfo(Constants.ACTION_PACKAGE_CHANGED, mChangedActions)
            mChangedActions = ""
        }
        return result
    }

    override fun getCollectionType(): ClientCollectionType = ClientCollectionType.BroadcastAction

    override fun getJobId(): String = "12"

    companion object {
        var mRestartedActions = ""
        var mChangedActions = ""
    }
}
