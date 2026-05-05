package com.xiaomi.push.mpcd.job

import android.content.Context
import android.content.SharedPreferences
import com.xiaomi.channel.commonutils.file.IOUtils
import com.xiaomi.channel.commonutils.misc.CollectionUtils
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.channel.commonutils.string.XMStringUtils
import com.xiaomi.push.mpcd.CDActionProviderHolder
import com.xiaomi.push.mpcd.Constants
import com.xiaomi.push.service.DefaultConfig
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.ClientCollectionType
import com.xiaomi.xmpush.thrift.ConfigKey
import com.xiaomi.xmpush.thrift.DataCollectionItem
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.XmPushActionCollectData
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import java.io.File
import java.nio.ByteBuffer

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/v9/c.java
 * Current override same-path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/push/mpcd/job/UploadJob.java
 * Stock class name is obfuscated as v9.c; this file keeps the deobfuscated UploadJob API.
 */
class UploadJob(private val context: Context) : ScheduledJobManager.Job() {
    private val mSharedPreference: SharedPreferences = context.getSharedPreferences("mipush_extra", 0)
    private val mOnlineConfig: OnlineConfig = OnlineConfig.getInstance(context)

    private fun ignoreUploadDataAtCurrentNetwork(): Boolean {
        if (Network.isWIFIConnected(context)) return false
        if ((Network.is4GConnected(context) || Network.is5GConnected(context)) && !verify4GorFasterUploadData()) {
            return true
        }
        return Network.is3GConnected(context) && !verify3GUploadData() || Network.is2GConnected(context)
    }

    private fun readFromFile(file: File): List<DataCollectionItem> = ArrayList()

    private fun recordLastUploadFullData(item: DataCollectionItem) {
        if (item.collectionType != ClientCollectionType.AppInstallList ||
            item.content.startsWith(CollectionJob.RESULT_SAME_PREFIX)
        ) return
        mSharedPreference.edit()
            .putLong("dc_job_result_time_4", item.collectedAt)
            .putString("dc_job_result_4", XMStringUtils.getMd5Digest(item.content))
            .commit()
    }

    private fun updateUpdateTimeStamp() {
        mSharedPreference.edit()
            .putLong(LAST_UPLADTE_DATA_TIMESTAMP, System.currentTimeMillis() / 1000)
            .commit()
    }

    private fun verify3GUploadData(): Boolean {
        if (!mOnlineConfig.getBooleanValue(ConfigKey.Upload3GSwitch.value, true)) return false
        return kotlin.math.abs((System.currentTimeMillis() / 1000) -
            mSharedPreference.getLong(LAST_UPLADTE_DATA_TIMESTAMP, DEFAULT_LAST_UPDATE_DATA_TIMESTAMP.toLong())) >
            maxOf(86400, mOnlineConfig.getIntValue(ConfigKey.Upload3GFrequency.value, DefaultConfig.DEFAULT_3G_UPLOAD_PERIOD))
    }

    private fun verify4GorFasterUploadData(): Boolean {
        if (!mOnlineConfig.getBooleanValue(ConfigKey.Upload4GSwitch.value, true)) return false
        return kotlin.math.abs((System.currentTimeMillis() / 1000) -
            mSharedPreference.getLong(LAST_UPLADTE_DATA_TIMESTAMP, DEFAULT_LAST_UPDATE_DATA_TIMESTAMP.toLong())) >
            maxOf(86400, mOnlineConfig.getIntValue(ConfigKey.Upload4GFrequency.value, DefaultConfig.DEFAULT_4G_UPLOAD_PERIOD))
    }

    override fun getJobId(): String = "1"

    override fun run() {
        val file = File(context.getExternalFilesDir(null), Constants.COLLECTED_DATA_FILENAME)
        if (!Network.isConnected(context)) {
            if (file.length() > 1863680) file.delete()
            return
        }
        if (!ignoreUploadDataAtCurrentNetwork() && file.exists()) {
            val list = readFromFile(file)
            if (!CollectionUtils.isEmpty(list)) {
                val size = list.size
                val subListItems = if (size > 4000) {
                    list.subList(size - Constants.MAX_CDATA_ITEM_TO_UPLOAD, size)
                } else {
                    list
                }
                val collectData = XmPushActionCollectData()
                collectData.dataCollectionItems = subListItems
                val collectBytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(collectData) ?: return
                val gzipped = IOUtils.gZip(collectBytes)
                val notification = XmPushActionNotification("-1", false)
                notification.type = NotificationType.DataCollection.value
                notification.binaryExtra = ByteBuffer.wrap(gzipped)
                val provider = CDActionProviderHolder.getInstance().getCDActionProvider()
                provider?.uploadNotification(notification, ActionType.Notification, null)
                updateUpdateTimeStamp()
            }
            file.delete()
        }
    }

    companion object {
        private const val DEFAULT_LAST_UPDATE_DATA_TIMESTAMP = -1
        const val LAST_UPLADTE_DATA_TIMESTAMP = "last_upload_data_timestamp"
    }
}
