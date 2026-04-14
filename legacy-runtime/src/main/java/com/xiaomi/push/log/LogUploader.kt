package com.xiaomi.push.log

import android.content.Context
import android.content.SharedPreferences
import com.xiaomi.channel.commonutils.file.SDCardUtils
import com.xiaomi.channel.commonutils.logger.LoggerInterface
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.SerializedAsyncTaskProcessor
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.miui.pushads.sdk.NotifyAdsDef
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.ServiceConfig
import com.xiaomi.smack.util.TaskExecutor
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class LogUploader private constructor(context: Context) {
    private val mTasks: ConcurrentLinkedQueue<Task> = ConcurrentLinkedQueue()
    private var mContext: Context = context

    open inner class Task : SerializedAsyncTaskProcessor.SerializedAsyncTask() {
        var timestamp: Long = System.currentTimeMillis()

        open fun canExcuteNow(): Boolean = true

        internal fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > EXPIRE_TIME

        override fun process() {}
    }

    inner class CleanUpTask : Task() {
        override fun process() = cleanUp()
    }

    inner class UploadTask(
        val url: String,
        val token: String,
        val file: File,
        val force: Boolean
    ) : Task() {
        var retryNum: Int = 0
        var uploaded: Boolean = false

        @Throws(JSONException::class)
        private fun checkLimit(): Boolean {
            val prefs = mContext.getSharedPreferences(PREF_NAME, 0)
            var jsonString = prefs.getString(PREF_KEY_REQUEST, "")
            var currentTime = System.currentTimeMillis()
            var times = 0
            try {
                val json = JSONObject(jsonString)
                currentTime = json.getLong(NotifyAdsDef.JSON_TAG_ACTIONTIME)
                times = json.getInt("times")
            } catch (e: JSONException) {
            }
            var newTimes = times
            if (System.currentTimeMillis() - currentTime < 86400000) {
                if (times > MAX_TIMES_PER_DAY) return false
            } else {
                currentTime = System.currentTimeMillis()
                newTimes = 0
            }
            return try {
                val json = JSONObject()
                json.put(NotifyAdsDef.JSON_TAG_ACTIONTIME, currentTime)
                json.put("times", newTimes + 1)
                prefs.edit().putString(PREF_KEY_REQUEST, json.toString()).commit()
                true
            } catch (e: JSONException) {
                MyLog.v("JSONException on put " + e.message)
                true
            }
        }

        override fun canExcuteNow(): Boolean {
            return Network.isWIFIConnected(mContext) || (force && Network.hasNetwork(mContext))
        }

        override fun postProcess() {
            if (!uploaded) {
                retryNum++
                if (retryNum < 3) {
                    mTasks.add(this@UploadTask)
                }
            }
            if (uploaded || retryNum >= 3) {
                file.delete()
            }
            this@LogUploader.uploadIfNeed((1 shl retryNum).toLong() * 1000)
        }

        override fun process() {
            try {
                if (checkLimit()) {
                    val map: MutableMap<String, String> = mutableMapOf()
                    map["uid"] = ServiceConfig.getDeviceUUID()!!
                    map["token"] = token
                    map["net"] = Network.getActiveConnPoint(mContext)!!
                    Network.uploadFile(url, map as Map<String, String>, file, PushConstants.UPLOAD_FILE_POST_KEY)
                }
                uploaded = true
            } catch (e: Exception) {
            }
        }
    }

    private fun cleanExpiredTask() {
        while (mTasks.isNotEmpty()) {
            val task = mTasks.peek() ?: break
            if (!task.isExpired() && mTasks.size <= MAX_PENDING_TASKS) return
            MyLog.v("remove Expired task")
            mTasks.remove(task)
        }
    }

    private fun cleanUp() {
        if (SDCardUtils.isSDCardBusy() || SDCardUtils.isSDCardUnavailable()) return
        try {
            val dir = File(mContext.getExternalFilesDir(null).toString() + ZIPPED_LOG_PATH)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: NullPointerException) {
        }
    }

    private fun executeTask(delay: Long) {
        if (mTasks.isEmpty()) return
        TaskExecutor.execute(object : SerializedAsyncTaskProcessor.SerializedAsyncTask() {
            var current: SerializedAsyncTaskProcessor.SerializedAsyncTask? = null
            override fun postProcess() {
                current?.postProcess()
            }
            override fun process() {
                val task = mTasks.peek() as Task?
                if (task == null || !task.canExcuteNow()) return
                if (mTasks.remove(task)) {
                    current = task
                }
                current?.process()
            }
        }, delay)
    }

    private fun uploadIfNeed(delay: Long) {
        val task = mTasks.peek() as Task?
        if (task == null || !task.canExcuteNow()) return
        executeTask(delay)
    }

    fun checkUpload() {
        cleanExpiredTask()
        uploadIfNeed(0L)
    }

    fun upload(url: String, token: String, startDate: Date, endDate: Date, maxLen: Int, force: Boolean) {
        val task = object : Task() {
            var file: File? = null
            override fun postProcess() {
                if (file != null && file!!.exists()) {
                    mTasks.add(UploadTask(url, token, file!!, force))
                }
                this@LogUploader.uploadIfNeed(0L)
            }
            override fun process() {
                if (SDCardUtils.isSDCardUseful()) {
                    try {
                        val dir = File(mContext.getExternalFilesDir(null).toString() + ZIPPED_LOG_PATH)
                        dir.mkdirs()
                        if (dir.isDirectory) {
                            val filter = LogFilter()
                            filter.setMaxLen(maxLen)
                            file = filter.filter(mContext, startDate, endDate, dir)
                        }
                    } catch (e: NullPointerException) {
                    }
                }
            }
        }
        mTasks.add(task)
        executeTask(0L)
    }

    companion object {
        private const val EXPIRE_TIME = 172800000L
        private const val MAX_PENDING_TASKS = 6
        private const val MAX_TIMES_PER_DAY = 10
        private const val PREF_KEY_REQUEST = "log.requst"
        private const val PREF_NAME = "log.timestamp"
        private const val ZIPPED_LOG_PATH = "/.logcache"

        @Volatile
        private var sInstance: LogUploader? = null

        fun getInstance(context: Context): LogUploader {
            if (sInstance == null) {
                synchronized(LogUploader::class.java) {
                    if (sInstance == null) {
                        sInstance = LogUploader(context)
                    }
                }
            }
            sInstance!!.mContext = context
            return sInstance!!
        }
    }

    init {
        mTasks.add(CleanUpTask())
        executeTask(0L)
    }
}
