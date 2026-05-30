package com.xiaomi.smack.util

import com.xiaomi.channel.commonutils.misc.SerializedAsyncTaskProcessor

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/smack/util/TaskExecutor.java
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
