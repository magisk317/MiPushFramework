package com.xiaomi.push.mpcd

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.push.mpcd.job.BroadcastActionCollectionjob
import com.xiaomi.push.mpcd.job.CollectionJob
import com.xiaomi.push.mpcd.receivers.BroadcastActionsReceiver
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.xmpush.thrift.ClientCollectionType
import com.xiaomi.xmpush.thrift.ConfigKey
import com.xiaomi.xmpush.thrift.DataCollectionItem

object CDEntrance {
    private const val BROADCAST_ACTION_PERIOD = 1L

    private fun getIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction("android.intent.action.PACKAGE_ADDED")
            addAction("android.intent.action.PACKAGE_CHANGED")
            addAction("android.intent.action.PACKAGE_DATA_CLEARED")
            addAction("android.intent.action.PACKAGE_REPLACED")
            addAction("android.intent.action.PACKAGE_RESTARTED")
            addAction("android.intent.action.PACKAGE_REMOVED")
            addDataScheme("package")
        }
    }

    private fun getIntentHandler(): IntentHandler {
        return IntentHandler { context, intent ->
            if (intent == null) return@IntentHandler
            ScheduledJobManager.getInstance(context).addOneShootJob {
                handleIntent(context, intent)
            }
        }
    }

    private fun handleIntent(context: Context, intent: Intent) {
        try {
            val dataString = intent.dataString ?: return
            if (TextUtils.isEmpty(dataString)) return
            val strArrSplit = dataString.split(":")
            if (strArrSplit.size < 2 || TextUtils.isEmpty(strArrSplit[1])) return
            val pkgName = strArrSplit[1]
            val currentTime = System.currentTimeMillis()
            val booleanValue = OnlineConfig.getInstance(context)
                .getBooleanValue(ConfigKey.BroadcastActionCollectionSwitch.value, true)

            when (intent.action) {
                "android.intent.action.PACKAGE_RESTARTED" -> {
                    if (CDataHelper.checkDataCollectionJobMutual(context, "12", 1L) || !booleanValue) return
                    if (TextUtils.isEmpty(BroadcastActionCollectionjob.mRestartedActions)) {
                        BroadcastActionCollectionjob.mRestartedActions += Constants.ACTION_PACKAGE_RESTARTED + ":"
                    }
                    BroadcastActionCollectionjob.mRestartedActions += "$pkgName${Constants.SEPARATOR_LEFT_PARENTESIS}$currentTime${Constants.SEPARATOR_RIGHT_PARENTESIS},"
                }
                "android.intent.action.PACKAGE_CHANGED" -> {
                    if (CDataHelper.checkDataCollectionJobMutual(context, "12", 1L) || !booleanValue) return
                    if (TextUtils.isEmpty(BroadcastActionCollectionjob.mChangedActions)) {
                        BroadcastActionCollectionjob.mChangedActions += Constants.ACTION_PACKAGE_CHANGED + ":"
                    }
                    BroadcastActionCollectionjob.mChangedActions += "$pkgName${Constants.SEPARATOR_LEFT_PARENTESIS}$currentTime${Constants.SEPARATOR_RIGHT_PARENTESIS},"
                }
                "android.intent.action.PACKAGE_ADDED" -> {
                    if (intent.extras?.getBoolean("android.intent.extra.REPLACING") != true && booleanValue) {
                        writeActionInfo(context, ClientCollectionType.BroadcastActionAdded.value.toString(), pkgName)
                    }
                }
                "android.intent.action.PACKAGE_REMOVED" -> {
                    if (intent.extras?.getBoolean("android.intent.extra.REPLACING") != true && booleanValue) {
                        writeActionInfo(context, ClientCollectionType.BroadcastActionRemoved.value.toString(), pkgName)
                    }
                }
                "android.intent.action.PACKAGE_REPLACED" -> {
                    if (booleanValue) {
                        writeActionInfo(context, ClientCollectionType.BroadcastActionReplaced.value.toString(), pkgName)
                    }
                }
                "android.intent.action.PACKAGE_DATA_CLEARED" -> {
                    if (booleanValue) {
                        writeActionInfo(context, ClientCollectionType.BroadcastActionDataCleared.value.toString(), pkgName)
                    }
                }
            }
        } catch (th: Throwable) {
        }
    }

    private fun writeActionInfo(context: Context, type: String, pkgName: String) {
        if (TextUtils.isEmpty(pkgName) || TextUtils.isEmpty(type)) return
        try {
            if (CDataHelper.checkDataCollectionJobMutual(context, "12", 1L)) return
            val item = DataCollectionItem()
            item.content = "$type:$pkgName"
            item.collectedAt = System.currentTimeMillis()
            item.collectionType = ClientCollectionType.BroadcastAction
            CollectionJob.writeItemToFile(context, item)
        } catch (th: Throwable) {
        }
    }

    @JvmStatic
    fun start(context: Context) {
        JobController.getInstance(context).schedulerJob()
        try {
            context.registerReceiver(BroadcastActionsReceiver(getIntentHandler()), getIntentFilter())
        } catch (th: Throwable) {
            MyLog.e(th)
        }
    }
}
