package com.xiaomi.smack.util

import com.xiaomi.channel.commonutils.misc.SerializedAsyncTaskProcessor

/*
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
abstract class TaskExecutor {
    companion object {
        private val mAsyncProcessor = SerializedAsyncTaskProcessor(true, 20)

        @JvmStatic
        fun execute(serializedAsyncTask: SerializedAsyncTaskProcessor.SerializedAsyncTask) {
            mAsyncProcessor.addNewTask(serializedAsyncTask)
        }

        @JvmStatic
        fun execute(serializedAsyncTask: SerializedAsyncTaskProcessor.SerializedAsyncTask, delay: Long) {
            mAsyncProcessor.addNewTaskWithDelayed(serializedAsyncTask, delay)
        }

        @JvmStatic
        fun execute(runnable: Runnable) {
            mAsyncProcessor.addNewTask(
                object : SerializedAsyncTaskProcessor.SerializedAsyncTask() {
                    override fun process() {
                        runnable.run()
                    }
                }
            )
        }
    }
}
