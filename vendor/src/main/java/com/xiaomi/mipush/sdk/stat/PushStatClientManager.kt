package com.xiaomi.mipush.sdk.stat

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.SharedPrefsCompat
import com.xiaomi.channel.commonutils.misc.ScheduledJobConstants
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.mipush.sdk.stat.db.DataBaseConfig
import com.xiaomi.mipush.sdk.stat.db.DbSizeControlJob
import com.xiaomi.mipush.sdk.stat.db.MessageDbHelperFactory
import com.xiaomi.mipush.sdk.stat.db.MessageInfoContract
import com.xiaomi.mipush.sdk.stat.db.MessageInsertJob
import com.xiaomi.mipush.sdk.stat.db.base.DbManager
import com.xiaomi.mipush.sdk.stat.db.MyLog
import com.xiaomi.mipush.sdk.stat.upload.BaseDataSender
import com.xiaomi.mipush.sdk.stat.upload.BaseScheduleWorker
import com.xiaomi.mipush.sdk.stat.upload.DefaultDbPathGetter
import com.xiaomi.mipush.sdk.stat.upload.IDataSender
import com.xiaomi.mipush.sdk.stat.upload.IDbPathGetter
import com.xiaomi.mipush.sdk.stat.upload.IScheduleWorker
import com.xiaomi.mipush.sdk.stat.upload.UploadDataHelper
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.push.service.TinyDataHelper
import com.xiaomi.xmpush.thrift.ClientUploadDataItem
import com.xiaomi.xmpush.thrift.ConfigKey
import org.json.JSONException
import org.json.JSONObject
import java.lang.ref.WeakReference

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/stat/PushStatClientManager.java
 * No stock 7.4.67-C same-path stat source was found in the split source tree.
 */
class PushStatClientManager private constructor(private val mContext: Context) {

    companion object {
        const val DEFAULT_FREQUENCY: Int = 3600
        private const val TAG = "PUSH_STAT"

        @Volatile
        private var sInstance: PushStatClientManager? = null

        @JvmStatic
        fun getInstance(context: Context): PushStatClientManager {
            return sInstance ?: synchronized(PushStatClientManager::class.java) {
                sInstance ?: PushStatClientManager(context).also { sInstance = it }
            }
        }
    }

    private var mAppId: String? = null
    private var mChannel: String? = null
    private var mDataSender: IDataSender? = null
    private var mDbPathGetter: IDbPathGetter? = null
    private var mDeleteFrequency: Int = 0
    private var mPackageName: String? = null
    private var mScheduleJob: IScheduleWorker? = null
    private var mUploadFrequency: Int = 0
    private val mSPName = "push_stat_sp"
    private val mSPUploadKey = "upload_time"
    private val mSPDeleteKey = "delete_time"
    private val mSPCheckKey = "check_time"

    private val mUploadJob: ScheduledJobManager.Job =
        object : ScheduledJobManager.Job() {
            override fun getJobId(): String = ScheduledJobConstants.STAT_UPLOAD_JOB_ID

            override fun run() {
                MyLog.v("exec== mUploadJob")
                if (mScheduleJob != null) {
                    mScheduleJob!!.onUpload(mContext)
                    updateTime(mSPUploadKey)
                }
            }
        }

    private val mCheckDbSizeJob: ScheduledJobManager.Job =
        object : ScheduledJobManager.Job() {
            override fun getJobId(): String = ScheduledJobConstants.STAT_CHECK_DB_ID

            override fun run() {
                MyLog.v("exec== DbSizeControlJob")
                DbManager.getInstance(mContext).execR(
                    DbSizeControlJob(getDbPath(), WeakReference(mContext)),
                )
                updateTime(mSPCheckKey)
            }
        }

    private val mDeleteJob: ScheduledJobManager.Job =
        object : ScheduledJobManager.Job() {
            override fun getJobId(): String = ScheduledJobConstants.STAT_DELETE_JOB_ID

            override fun run() {
                if (mScheduleJob != null) {
                    mScheduleJob!!.onDelete(mContext)
                    updateTime(mSPDeleteKey)
                }
            }
        }

    private fun OCSwitch(): Boolean {
        return OnlineConfig.getInstance(mContext).getBooleanValue(
            ConfigKey.StatDataSwitch.value,
            true,
        )
    }

    private fun checkTime(str: String, i: Int): Boolean {
        val jCurrentTimeMillis = System.currentTimeMillis() - getLastTime(str)
        MyLog.v("checkTime:  period = $jCurrentTimeMillis   frequency = ${i * 1000}")
        return jCurrentTimeMillis > (i * 1000).toLong()
    }

    private fun getDbPath(): String {
        return mContext.getDatabasePath(DataBaseConfig.DATABASE_NAME).absolutePath
    }

    private fun getLastTime(str: String): Long {
        return mContext.getSharedPreferences(mSPName, 0).getLong(str, 0L)
    }

    private fun initDataSender() {
        if (mDataSender == null) {
            synchronized(PushStatClientManager::class.java) {
                if (mDataSender == null) {
                    if (mDbPathGetter == null) {
                        mDbPathGetter = DefaultDbPathGetter()
                    }
                    mDataSender = BaseDataSender(mDbPathGetter!!)
                }
            }
        }
    }

    private fun initSchedule() {
        if (mScheduleJob == null) {
            synchronized(PushStatClientManager::class.java) {
                if (mScheduleJob == null) {
                    if (mDbPathGetter == null) {
                        mDbPathGetter = DefaultDbPathGetter()
                    }
                    mScheduleJob = BaseScheduleWorker(mDbPathGetter!!)
                }
            }
        }
    }

    private fun onInit() {
        if (OCSwitch()) {
            DbManager.getInstance(mContext).init(MessageDbHelperFactory())
            initDataSender()
            initSchedule()
            scheduleCheckJob()
            recorderChannel(mChannel)
            OnlineConfig.getInstance(mContext).addOCUpdateCallbacks(
                object : OnlineConfig.OCUpdateCallback(103, "stat data updata job") {
                    override fun onCallback() {
                        resetUploadFrequency(
                            OnlineConfig.getInstance(mContext)
                                .getLongValue(ConfigKey.StatDataUploadFrequency.value, 3600L)
                                .toInt(),
                            OnlineConfig.getInstance(mContext)
                                .getLongValue(ConfigKey.StatDataDeleteFrequency.value, 3600L)
                                .toInt(),
                        )
                    }
                },
            )
        }
    }

    private fun recorderChannel(str: String?) {
        if (TextUtils.isEmpty(str)) return
        val jSONObject = JSONObject()
        try {
            jSONObject.put("channel", str)
            record(jSONObject.toString())
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun resetUploadFrequency(i: Int, i2: Int) {
        if (!OCSwitch()) return
        if (i != mUploadFrequency) {
            mUploadFrequency = i
            ScheduledJobManager.getInstance(mContext).cancelJob(ScheduledJobConstants.STAT_UPLOAD_JOB_ID)
            ScheduledJobManager.getInstance(mContext).addRepeatJob(mUploadJob, mUploadFrequency)
        }
        if (i2 != mDeleteFrequency) {
            mDeleteFrequency = i2
            ScheduledJobManager.getInstance(mContext).cancelJob(ScheduledJobConstants.STAT_DELETE_JOB_ID)
            ScheduledJobManager.getInstance(mContext).addRepeatJob(mDeleteJob, mDeleteFrequency)
        }
    }

    private fun scheduleCheckJob() {
        if (checkTime(mSPCheckKey, 43200)) {
            ScheduledJobManager.getInstance(mContext).addOneShootJob(
                { mCheckDbSizeJob.run() },
                20,
            )
        }
        ScheduledJobManager.getInstance(mContext).addRepeatJob(mCheckDbSizeJob, 43200)
    }

    private fun scheduleDeleteJob() {
        if (checkTime(mSPDeleteKey, mDeleteFrequency)) {
            ScheduledJobManager.getInstance(mContext).addOneShootJob(
                { mDeleteJob.run() },
                15,
            )
        }
        ScheduledJobManager.getInstance(mContext).addRepeatJob(mDeleteJob, mDeleteFrequency)
    }

    private fun scheduleUploadJob() {
        if (checkTime(mSPUploadKey, mUploadFrequency)) {
            ScheduledJobManager.getInstance(mContext).addOneShootJob(
                { mUploadJob.run() },
                10,
            )
        }
        ScheduledJobManager.getInstance(mContext).addRepeatJob(mUploadJob, mUploadFrequency)
    }

    private fun updateTime(str: String) {
        val editorEdit: SharedPreferences.Editor =
            mContext.getSharedPreferences(mSPName, 0).edit()
        editorEdit.putLong(str, System.currentTimeMillis())
        SharedPrefsCompat.apply(editorEdit)
    }

    fun exec(baseJob: DbManager.BaseJob) {
        DbManager.getInstance(mContext).exec(baseJob)
    }

    fun exec(arrayList: ArrayList<DbManager.BaseJob>) {
        DbManager.getInstance(mContext).exec(arrayList)
    }

    fun execDelay(baseJob: DbManager.BaseJob, i: Int) {
        DbManager.getInstance(mContext).execDelay(baseJob, i)
    }

    val appId: String?
        get() = mAppId

    val deleteFrequency: Int
        get() = mDeleteFrequency

    val packageName: String?
        get() = mPackageName

    val uploadFrequency: Int
        get() = mUploadFrequency

    fun init(str: String, str2: String) {
        init(mContext.packageName, str, str2, DefaultDbPathGetter())
    }

    fun init(str: String, str2: String, iDbPathGetter: IDbPathGetter) {
        init(mContext.packageName, str, str2, iDbPathGetter)
    }

    fun init(str: String, str2: String, str3: String) {
        init(str, str2, str3, DefaultDbPathGetter())
    }

    fun init(
        str: String,
        str2: String,
        str3: String,
        iDbPathGetter: IDbPathGetter,
    ) {
        if (TextUtils.isEmpty(str)) {
            MyLog.e("packageName can not be null; ")
            return
        }
        if (TextUtils.isEmpty(str2)) {
            MyLog.e("appId can not be null; ")
            return
        }
        val longValue = OnlineConfig.getInstance(mContext)
            .getLongValue(ConfigKey.StatDataUploadFrequency.value, 3600L)
        val longValue2 = OnlineConfig.getInstance(mContext)
            .getLongValue(ConfigKey.StatDataDeleteFrequency.value, 3600L)
        mAppId = str2
        mPackageName = str
        mUploadFrequency = longValue.toInt()
        mDeleteFrequency = longValue2.toInt()
        mChannel = str3
        mDbPathGetter = iDbPathGetter
        onInit()
    }

    fun onResult(str: String, str2: String, bool: Boolean) {
        if (mDataSender != null) {
            if (bool) {
                mDataSender!!.onSuccess(mContext, str2, str)
            } else {
                mDataSender!!.onFailed(mContext, str2, str)
            }
        }
    }

    fun record(clientUploadDataItem: ClientUploadDataItem?) {
        if (clientUploadDataItem == null) return
        if (OCSwitch() && TinyDataHelper.shouldUpload(clientUploadDataItem.pkgName)) {
            val job = MessageInsertJob.buildInsertJob(mContext, getDbPath(), clientUploadDataItem)
            if (job != null) {
                exec(job)
            }
        }
    }

    fun record(str: String?) {
        if (OCSwitch() && !TextUtils.isEmpty(str)) {
            record(UploadDataHelper.wrapperData(mContext, str))
        }
    }

    fun schedule() {
        if (OCSwitch()) {
            scheduleUploadJob()
            scheduleDeleteJob()
        }
    }

    fun send(str: String, list: List<MessageInfoContract.MessageModel>) {
        if (OCSwitch()) {
            mDataSender?.onSend(mContext, str, list.toMutableList())
        }
    }
}
