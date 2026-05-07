package com.xiaomi.mipush.sdk

import android.content.Context

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/OperatePushHelper.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
class OperatePushHelper private constructor(context: Context) {
    private val appContext: Context = context.applicationContext ?: context
    private val operateMessages: MutableList<MessageBean> = ArrayList()

    fun getRetryCount(messageId: String?): Int {
        synchronized(operateMessages) {
            val probe = MessageBean().apply { this.messageId = messageId ?: "" }
            if (operateMessages.contains(probe)) {
                for (message in operateMessages) {
                    if (message == probe) {
                        return message.count
                    }
                }
            }
            return 0
        }
    }

    fun getSyncStatus(retryType: RetryType): String? {
        synchronized(this) {
            return appContext.getSharedPreferences("mipush_extra", 0).getString(retryType.name, "")
        }
    }

    fun increaseRetryCount(messageId: String?) {
        synchronized(operateMessages) {
            val probe = MessageBean().apply { this.messageId = messageId ?: "" }
            var target = probe
            if (operateMessages.contains(probe)) {
                val iterator = operateMessages.iterator()
                do {
                    target = probe
                    if (!iterator.hasNext()) {
                        break
                    }
                    target = iterator.next()
                } while (probe != target)
            }
            target.count++
            operateMessages.remove(target)
            operateMessages.add(target)
        }
    }

    fun isMessageOperating(messageId: String?): Boolean {
        synchronized(operateMessages) {
            val probe = MessageBean().apply { this.messageId = messageId ?: "" }
            return operateMessages.contains(probe)
        }
    }

    fun putSyncStatus(retryType: RetryType, status: String?) {
        synchronized(this) {
            val sharedPreferences = appContext.getSharedPreferences("mipush_extra", 0)
            sharedPreferences.edit().putString(retryType.name, status).commit()
        }
    }

    fun removeOperateMessage(messageId: String?) {
        synchronized(operateMessages) {
            val probe = MessageBean().apply { this.messageId = messageId ?: "" }
            if (operateMessages.contains(probe)) {
                operateMessages.remove(probe)
            }
        }
    }

    fun resetOperateMessage(messageId: String?) {
        synchronized(operateMessages) {
            val message = MessageBean().apply {
                count = 0
                this.messageId = messageId ?: ""
            }
            if (operateMessages.contains(message)) {
                operateMessages.remove(message)
            }
            operateMessages.add(message)
        }
    }

    companion object {
        const val MAX_RETRY_COUNT = 10
        const val SYNCED = "synced"
        const val SYNCING = "syncing"
        const val TIME_OUT = 5000

        @Volatile
        private var sInstance: OperatePushHelper? = null

        @JvmStatic
        fun getInstance(context: Context): OperatePushHelper {
            return sInstance ?: synchronized(OperatePushHelper::class.java) {
                sInstance ?: OperatePushHelper(context).also { sInstance = it }
            }
        }
    }
}
