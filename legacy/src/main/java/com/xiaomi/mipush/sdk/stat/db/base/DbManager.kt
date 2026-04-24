package com.xiaomi.mipush.sdk.stat.db.base
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.text.TextUtils
import com.xiaomi.channel.commonutils.file.FileLockerWorker
import com.xiaomi.channel.commonutils.misc.ScheduledJobConstants
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.channel.commonutils.string.MD5
import com.xiaomi.mipush.sdk.stat.db.MyLog
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.xmpush.thrift.ConfigKey
import java.io.File
import java.lang.ref.WeakReference
import java.util.Random
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class DbManager private constructor(private val mContext: Context) {

    private var mBaseDbHelperFactory: BaseDbHelperFactory? = null
    private val mDbHelperMap = HashMap<String, BaseDbHelper>()
    private val mPendingList = ArrayList<BaseJob>()
    private val mPool = ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS, LinkedBlockingQueue())

    companion object {
        private const val EXEC_DELAY = 5
        @Volatile
        private var sDbManager: DbManager? = null

        fun getInstance(context: Context): DbManager {
            return sDbManager ?: synchronized(DbManager::class.java) {
                sDbManager ?: DbManager(context).also { sDbManager = it }
            }
        }
    }

    private fun getDbHelper(str: String): BaseDbHelper {
        var dbHelper: BaseDbHelper? = mDbHelperMap[str]
        if (dbHelper == null) {
            synchronized(mDbHelperMap) {
                dbHelper = mDbHelperMap[str]
                if (dbHelper == null) {
                    dbHelper = mBaseDbHelperFactory!!.getDbHelper(mContext, str)
                    mDbHelperMap[str] = dbHelper
                }
            }
        }
        return dbHelper!!
    }

    private fun postDelayed(runnable: Runnable, i: Int) {
        ScheduledJobManager.getInstance(mContext).addOneShootJob(
            {
                if (!mPool.isShutdown) {
                    mPool.execute(runnable)
                }
            },
            i,
        )
    }

    private fun sendExecCmd() {
        ScheduledJobManager.getInstance(mContext).addOneShootJob(
            object : ScheduledJobManager.Job() {
                override fun getJobId(): String = ScheduledJobConstants.SEND_EXEC_CMD_JOB_ID

                override fun run() {
                    synchronized(mPendingList) {
                        if (mPendingList.isNotEmpty()) {
                            if (mPendingList.size > 1) {
                                exec(ArrayList(mPendingList))
                            } else {
                                execNow(mPendingList[0])
                            }
                            mPendingList.clear()
                            System.gc()
                        }
                    }
                }
            },
            OnlineConfig.getInstance(mContext)
                .getIntValue(ConfigKey.StatDataProcessFrequency.value, 5),
        )
    }

    fun exec(baseJob: BaseJob?) {
        if (baseJob == null) return
        if (mBaseDbHelperFactory == null) {
            throw IllegalStateException("should exec init method first!")
        }
        val dataPath = baseJob.dataPath
        var dbHelper: BaseDbHelper?
        synchronized(mDbHelperMap) {
            dbHelper = mDbHelperMap[dataPath]
            if (dbHelper == null) {
                dbHelper = mBaseDbHelperFactory!!.getDbHelper(mContext, dataPath)
                mDbHelperMap[dataPath] = dbHelper
            }
        }
        if (mPool.isShutdown) return
        baseJob.attachInfo(dbHelper!!, mContext)
        synchronized(mPendingList) {
            mPendingList.add(baseJob)
            sendExecCmd()
        }
    }

    fun exec(arrayList: ArrayList<BaseJob>) {
        if (mBaseDbHelperFactory == null) {
            throw IllegalStateException("should exec setDbHelperFactory method first!")
        }
        val map = HashMap<String, ArrayList<BaseJob>>()
        if (mPool.isShutdown) return
        for (baseJob in arrayList) {
            if (baseJob.needAttachInfo()) {
                baseJob.attachInfo(getDbHelper(baseJob.dataPath), mContext)
            }
            var arrayList2 = map[baseJob.dataPath]
            if (arrayList2 == null) {
                arrayList2 = ArrayList()
                map[baseJob.dataPath] = arrayList2
            }
            arrayList2.add(baseJob)
        }
        for (str in map.keys) {
            val arrayList4 = map[str]
            if (arrayList4 != null && arrayList4.isNotEmpty()) {
                val batchJob = BatchJob(str, arrayList4)
                batchJob.attachInfo(arrayList4[0].mDbHelper!!, mContext)
                mPool.execute(batchJob)
            }
        }
    }

    fun execDelay(baseJob: BaseJob?, i: Int) {
        if (i == 0) {
            execNow(baseJob)
        } else {
            ScheduledJobManager.getInstance(mContext).addOneShootJob(
                { execNow(baseJob) },
                i,
            )
        }
    }

    fun execNow(baseJob: BaseJob?) {
        if (baseJob == null) return
        if (mBaseDbHelperFactory == null) {
            throw IllegalStateException("should exec init method first!")
        }
        val dataPath = baseJob.dataPath
        var dbHelper: BaseDbHelper?
        synchronized(mDbHelperMap) {
            dbHelper = mDbHelperMap[dataPath]
            if (dbHelper == null) {
                dbHelper = mBaseDbHelperFactory!!.getDbHelper(mContext, dataPath)
                mDbHelperMap[dataPath] = dbHelper
            }
        }
        if (mPool.isShutdown) return
        baseJob.attachInfo(dbHelper!!, mContext)
        execR(baseJob)
    }

    fun execR(runnable: Runnable) {
        if (mPool.isShutdown) return
        mPool.execute(runnable)
    }

    fun getTableName(str: String): String = getDbHelper(str).getTableName()

    fun init(baseDbHelperFactory: BaseDbHelperFactory) {
        mBaseDbHelperFactory = baseDbHelperFactory
    }

    abstract class BaseJob(
        private val mDataPath: String,
    ) : Runnable, ISerialSchedule {

        var mNextJob: BaseJob? = null
        protected var mTableName: String? = null
        private var mWRContext: WeakReference<Context>? = null
        var mDbHelper: BaseDbHelper? = null
        private val mRandom = Random()
        private var mRetryCount: Int = 0

        fun append(baseJob: BaseJob?) {
            mNextJob = baseJob
        }

        fun attachInfo(baseDbHelper: BaseDbHelper, context: Context) {
            mDbHelper = baseDbHelper
            mTableName = baseDbHelper.getTableName()
            mWRContext = WeakReference(context)
        }

        abstract fun description(): String

        @Throws(Exception::class)
        abstract fun doRun(context: Context, db: SQLiteDatabase)

        fun finish(context: Context) {
            val out = output()
            if (out != null) {
                mNextJob?.input(context, out)
            }
            onFinish(context)
        }

        val dataPath: String
            get() = mDataPath

        open fun getDatabase(): SQLiteDatabase? {
            return mDbHelper?.writableDatabase
        }

        override fun input(context: Context, obj: Any) {
            DbManager.getInstance(context).exec(this)
        }

        fun needAttachInfo(): Boolean {
            return mDbHelper == null || TextUtils.isEmpty(mTableName) || mWRContext == null
        }

        open fun onFinish(context: Context) {}

        override fun output(): Any? = null

        override fun run() {
            val context = mWRContext?.get()
            if (context == null || context.filesDir == null || mDbHelper == null ||
                TextUtils.isEmpty(mDataPath)
            ) {
                return
            }
            val file = File(mDataPath)
            FileLockerWorker.runMutiProcessJob(
                context,
                File(file.parentFile, MD5.MD5_16(file.absolutePath)),
            ) {
                var db: SQLiteDatabase? = null
                try {
                    val database = getDatabase()
                    if (database != null && database.isOpen) {
                        database.beginTransaction()
                        doRun(context, database)
                        db = database
                        database.setTransactionSuccessful()
                    }
                    database?.endTransaction()
                } catch (e: Exception) {
                    MyLog.e(e)
                    db?.endTransaction()
                } finally {
                    mDbHelper?.close()
                    finish(context)
                }
            }
        }
    }

    abstract class BaseQueryJob<T>(
        str: String,
        private val mBackRows: List<String>?,
        private val mWhereClause: String?,
        private val mWhereValues: Array<String>?,
        private val mGroupBy: String?,
        private val mHaving: String?,
        private val mOrderBy: String?,
        private var mLimit: Int,
    ) : BaseJob(str) {

        private val mResults = ArrayList<T>()

        @Throws(Exception::class)
        override fun doRun(context: Context, db: SQLiteDatabase) {
            mResults.clear()
            val strArr2: Array<String>? = if (mBackRows != null && mBackRows.isNotEmpty()) {
                mBackRows.toTypedArray()
            } else {
                null
            }
            val i = mLimit
            val cursorQuery = db.query(
                mTableName!!,
                strArr2,
                mWhereClause,
                mWhereValues,
                mGroupBy,
                mHaving,
                mOrderBy,
                if (i <= 0) null else i.toString(),
            )
            if (cursorQuery.moveToFirst()) {
                do {
                    val tProcessOneData = processOneData(context, cursorQuery)
                    if (tProcessOneData != null) {
                        mResults.add(tProcessOneData)
                    }
                } while (cursorQuery.moveToNext())
                cursorQuery.close()
            }
            notifyResult(context, mResults)
        }

        override fun getDatabase(): SQLiteDatabase? {
            return mDbHelper?.readableDatabase
        }

        protected val limitValue: Int
            get() = mLimit

        protected fun setLimitValue(i: Int) {
            mLimit = i
        }

        var limit: Int
            get() = mLimit
            set(value) {
                mLimit = value
            }

        abstract fun notifyResult(context: Context, list: List<T>)

        abstract fun processOneData(context: Context, cursor: android.database.Cursor): T?
    }

    open class BatchJob(
        str: String,
        private val mJobs: ArrayList<BaseJob>,
    ) : BaseJob(str) {

        override fun description(): String = "BatchJob"

        @Throws(Exception::class)
        override fun doRun(context: Context, db: SQLiteDatabase) {
            for (baseJob in mJobs) {
                baseJob.doRun(context, db)
            }
        }
    }

    open class DeleteJob(
        str: String,
        private val mWhereClause: String?,
        val mWhereValues: Array<String>?,
    ) : BaseJob(str) {

        override fun description(): String = "DeleteJob"

        @Throws(Exception::class)
        override fun doRun(context: Context, db: SQLiteDatabase) {
            db.delete(mTableName!!, mWhereClause, mWhereValues)
        }
    }

    open class InsertJob(
        str: String,
        private val mContentValues: android.content.ContentValues,
    ) : BaseJob(str) {

        override fun description(): String = "InsertJob"

        @Throws(Exception::class)
        override fun doRun(context: Context, db: SQLiteDatabase) {
            db.insert(mTableName!!, null, mContentValues)
        }
    }

    open class UpdateJob(
        str: String,
        private val mWhereClause: String?,
        private val mWhereValues: Array<String>?,
        private val mContentValues: android.content.ContentValues,
    ) : BaseJob(str) {

        override fun description(): String = "UpdateJob"

        @Throws(Exception::class)
        override fun doRun(context: Context, db: SQLiteDatabase) {
            db.update(mTableName!!, mContentValues, mWhereClause, mWhereValues)
        }
    }
}
