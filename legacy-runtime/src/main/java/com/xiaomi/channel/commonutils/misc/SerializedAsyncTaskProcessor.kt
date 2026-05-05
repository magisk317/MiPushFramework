package com.xiaomi.channel.commonutils.misc

import android.os.Handler
import android.os.Looper
import android.os.Message
import com.xiaomi.channel.commonutils.logger.MyLog
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/channel/commonutils/misc/SerializedAsyncTaskProcessor.java
 */
class SerializedAsyncTaskProcessor @JvmOverloads constructor(
    private val mIsDaemon: Boolean = false,
    private var mKeepAliveTime: Int = 0,
) {
    @Volatile
    private var mCurrentTask: SerializedAsyncTask? = null
    private val mMainThreadHandler: Handler
    private var mProcessThread: ProcessPackageThread? = null
    @Volatile
    private var threadQuit = false

    private inner class ProcessPackageThread : Thread(THREAD_NAME) {
        val mTasks: LinkedBlockingQueue<SerializedAsyncTask> = LinkedBlockingQueue()

        private fun notifyUI(what: Int, task: SerializedAsyncTask) {
            try {
                mMainThreadHandler.sendMessage(mMainThreadHandler.obtainMessage(what, task))
            } catch (e: Exception) {
                MyLog.e(e)
            }
        }

        fun insertTask(task: SerializedAsyncTask) {
            try {
                mTasks.add(task)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun run() {
            val keepAlive = if (mKeepAliveTime > 0) mKeepAliveTime.toLong() else Long.MAX_VALUE
            while (!threadQuit) {
                try {
                    val task = mTasks.poll(keepAlive, TimeUnit.SECONDS)
                    mCurrentTask = task
                    if (task != null) {
                        notifyUI(MSG_BEFORE_EXECUTE, task)
                        task.process()
                        notifyUI(MSG_AFTER_EXECUTE, task)
                    } else if (mKeepAliveTime > 0) {
                        stopTaskProcessor()
                    }
                } catch (e: InterruptedException) {
                    MyLog.e(e)
                }
            }
        }
    }

    abstract class SerializedAsyncTask {
        open fun postProcess() {
        }

        open fun preProcess() {
        }

        abstract fun process()
    }

    init {
        mMainThreadHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(message: Message) {
                val task = message.obj as SerializedAsyncTask
                if (message.what == MSG_BEFORE_EXECUTE) {
                    task.preProcess()
                } else if (message.what == MSG_AFTER_EXECUTE) {
                    task.postProcess()
                }
                super.handleMessage(message)
            }
        }
    }

    fun stopTaskProcessor() {
        synchronized(this) {
            mProcessThread = null
            threadQuit = true
        }
    }

    fun addNewTask(serializedAsyncTask: SerializedAsyncTask) {
        synchronized(this) {
            if (mProcessThread == null) {
                val processThread = ProcessPackageThread()
                mProcessThread = processThread
                processThread.isDaemon = mIsDaemon
                threadQuit = false
                processThread.start()
            }
            mProcessThread?.insertTask(serializedAsyncTask)
        }
    }

    fun addNewTaskWithDelayed(serializedAsyncTask: SerializedAsyncTask, delay: Long) {
        mMainThreadHandler.postDelayed({ addNewTask(serializedAsyncTask) }, delay)
    }

    fun clearTask() {
        mProcessThread?.mTasks?.clear()
    }

    fun destroy() {
        threadQuit = true
    }

    fun getCurrentTask(): SerializedAsyncTask? = mCurrentTask

    companion object {
        private const val MSG_AFTER_EXECUTE = 1
        private const val MSG_BEFORE_EXECUTE = 0
        private const val THREAD_NAME = "PackageProcessor"
    }
}
