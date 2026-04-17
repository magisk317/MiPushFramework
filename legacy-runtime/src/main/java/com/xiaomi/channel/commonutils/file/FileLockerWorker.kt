package com.xiaomi.channel.commonutils.file

import android.content.Context
import android.text.TextUtils
import java.io.File
import java.io.IOException

abstract class FileLockerWorker : Runnable {
    private val mContext: Context
    private var mFile: File?
    private var mRunnable: Runnable?

    companion object {
        private const val DEFAULT_LOCKER = "default_locker"

        @JvmStatic
        fun runMutiProcessJob(context: Context, file: File?, runnable: Runnable?) {
            object : FileLockerWorker(context, file) {
                override fun doWork(context: Context) {
                    runnable?.run()
                }
            }.run()
        }

        @JvmStatic
        fun runMutiProcessJob(context: Context, name: String?, runnable: Runnable?) {
            val file = if (!name.isNullOrEmpty()) {
                File(context.filesDir, name)
            } else {
                null
            }
            runMutiProcessJob(context, file, runnable)
        }
    }

    private constructor(context: Context, file: File?) {
        mContext = context
        mFile = file
        mRunnable = null
    }

    protected abstract fun doWork(context: Context)

    override fun run() {
        var fileLocker: FileLocker? = null
        try {
            if (mFile == null) {
                mFile = File(mContext.filesDir, DEFAULT_LOCKER)
            }
            fileLocker = FileLocker.lock(mContext, mFile!!)
            mRunnable?.run()
            doWork(mContext)
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (th: Throwable) {
            fileLocker?.unlock()
            throw th
        }
        fileLocker?.unlock()
    }
}
