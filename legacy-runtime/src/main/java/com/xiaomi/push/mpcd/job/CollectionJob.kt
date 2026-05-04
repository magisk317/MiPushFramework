package com.xiaomi.push.mpcd.job

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import com.xiaomi.channel.commonutils.file.IOUtils
import com.xiaomi.channel.commonutils.logger.LoggerInterface
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.ByteUtils
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.channel.commonutils.string.XMStringUtils
import com.xiaomi.push.mpcd.CDActionProviderHolder
import com.xiaomi.push.mpcd.CDataHelper
import com.xiaomi.push.mpcd.Constants
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.xmpush.thrift.ClientCollectionType
import com.xiaomi.xmpush.thrift.ConfigKey
import com.xiaomi.xmpush.thrift.DataCollectionItem
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock

abstract class CollectionJob(
    protected val context: Context,
    protected val period: Int
) : ScheduledJobManager.Job() {
    private fun getJobResultKey() = KEY_JOB_RESULT_PREFIX + getJobId()

    private fun getJobResultTimeKey() = KEY_JOB_RESULT_TIME_PREFIX + getJobId()

    protected fun checkDataCollectionJobMutual(): Boolean {
        return CDataHelper.checkDataCollectionJobMutual(context, getJobId(), period.toLong())
    }

    protected open fun checkPermission(): Boolean = true

    protected open fun checkRepeatedData(): Boolean = false

    abstract fun collectInfo(): String?

    abstract fun getCollectionType(): ClientCollectionType

    override fun run() {
        val collectInfo = collectInfo() ?: return
        if (checkDataCollectionJobMutual()) {
            MyLog.w("DC run job mutual: ${getJobId()}")
            return
        }
        val provider = CDActionProviderHolder.getInstance().getCDActionProvider()
        val regSecret = provider?.getRegSecret() ?: ""
        if (TextUtils.isEmpty(regSecret) || !checkPermission()) return

        var result = collectInfo
        if (checkRepeatedData()) {
            val prefs = context.getSharedPreferences("mipush_extra", 0)
            if (XMStringUtils.getMd5Digest(collectInfo) == prefs.getString(getJobResultKey(), null)) {
                val lastTime = prefs.getLong(getJobResultTimeKey(), 0L)
                val interval = OnlineConfig.getInstance(context)
                    .getIntValue(ConfigKey.DCJobUploadRepeatedInterval.value, 604800)
                if ((System.currentTimeMillis() - lastTime) / 1000 < period) return
                result = if ((System.currentTimeMillis() - lastTime) / 1000 < interval) {
                    RESULT_SAME_PREFIX + lastTime
                } else {
                    collectInfo
                }
            }
        }
        val item = DataCollectionItem()
        item.content = result
        item.collectedAt = System.currentTimeMillis()
        item.collectionType = getCollectionType()
        writeItemToFile(context, item, regSecret)
    }

    companion object {
        const val KEY_JOB_RESULT_PREFIX = "dc_job_result_"
        const val KEY_JOB_RESULT_TIME_PREFIX = "dc_job_result_time_"
        const val RESULT_SAME_PREFIX = "same_"

        fun writeItemToFile(context: Context, dataCollectionItem: DataCollectionItem) {
            val provider = CDActionProviderHolder.getInstance().getCDActionProvider()
            val regSecret = provider?.getRegSecret() ?: ""
            if (TextUtils.isEmpty(regSecret) || TextUtils.isEmpty(dataCollectionItem.content)) return
            writeItemToFile(context, dataCollectionItem, regSecret)
        }

        private fun writeItemToFile(context: Context, dataCollectionItem: DataCollectionItem, secret: String) {
            val itemBytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(dataCollectionItem) ?: return
            val encryptedData = CDataHelper.encryptData(secret, itemBytes)
            if (encryptedData == null || encryptedData.isEmpty()) return

            synchronized(Constants.cDataLock4Thread) {
                var randomAccessFile: RandomAccessFile? = null
                var fileLock: FileLock? = null
                var bufferedOutputStream: BufferedOutputStream? = null
                try {
                    val lockFile = File(context.getExternalFilesDir(null), Constants.COLLECTED_DATA_LOCK)
                    IOUtils.createFileQuietly(lockFile)
                    randomAccessFile = RandomAccessFile(lockFile, "rw")
                    fileLock = randomAccessFile.channel.lock()
                    bufferedOutputStream = BufferedOutputStream(
                        FileOutputStream(File(context.getExternalFilesDir(null), Constants.COLLECTED_DATA_FILENAME), true)
                    )
                    bufferedOutputStream.write(ByteUtils.parseInt(encryptedData.size))
                    bufferedOutputStream.write(encryptedData)
                    bufferedOutputStream.flush()
                } catch (e: IOException) {
                    e.printStackTrace()
                } finally {
                    if (fileLock != null && fileLock.isValid()) {
                        try { fileLock.release() } catch (e: IOException) {}
                    }
                    IOUtils.closeQuietly(bufferedOutputStream)
                    IOUtils.closeQuietly(randomAccessFile)
                }
            }
        }
    }
}
