package com.xiaomi.mipush.sdk

import android.os.Bundle
import com.xiaomi.mipush.sdk.PushMessageHandler.PushMessageInterface
import java.io.Serializable

class MiPushMessage : PushMessageInterface, Serializable {
    var alias: String? = null
    var category: String? = null
    var content: String? = null
    var description: String? = null
    var isNotified: Boolean = false
    var messageId: String? = null
    var messageType: Int = 0
    var notifyId: Int = 0
    var notifyType: Int = 0
    var passThrough: Int = 0
    var title: String? = null
    var topic: String? = null
    var userAccount: String? = null
    var extra: Map<String, String>? = null

    companion object {
        private const val KEY_ALIAS = "alias"
        private const val KEY_CATEGORY = "category"
        private const val KEY_CONTENT = "content"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_IS_NOTIFIED = "isNotified"
        private const val KEY_MESSAGE_ID = "messageId"
        private const val KEY_MESSAGE_TYPE = "messageType"
        private const val KEY_NOTIFY_ID = "notifyId"
        private const val KEY_NOTIFY_TYPE = "notifyType"
        private const val KEY_PASS_THROUGH = "passThrough"
        private const val KEY_TITLE = "title"
        private const val KEY_TOPIC = "topic"
        private const val KEY_USER_ACCOUNT = "userAccount"
        private const val KEY_EXTRA = "extra"
        private const val serialVersionUID: Long = 1

        @JvmStatic
        fun fromBundle(bundle: Bundle): MiPushMessage {
            return MiPushMessage().apply {
                alias = bundle.getString(KEY_ALIAS)
                category = bundle.getString(KEY_CATEGORY)
                content = bundle.getString(KEY_CONTENT)
                description = bundle.getString(KEY_DESCRIPTION)
                isNotified = bundle.getBoolean(KEY_IS_NOTIFIED, false)
                messageId = bundle.getString(KEY_MESSAGE_ID)
                messageType = bundle.getInt(KEY_MESSAGE_TYPE)
                notifyId = bundle.getInt(KEY_NOTIFY_ID)
                notifyType = bundle.getInt(KEY_NOTIFY_TYPE)
                passThrough = bundle.getInt(KEY_PASS_THROUGH)
                title = bundle.getString(KEY_TITLE)
                topic = bundle.getString(KEY_TOPIC)
                userAccount = bundle.getString(KEY_USER_ACCOUNT)
                bundle.getBundle(KEY_EXTRA)?.let {
                    extra = it.keySet().associateWith { key -> it.getString(key) ?: "" }
                }
            }
        }
    }

    fun isArrivedMessage(): Boolean = false
    fun isBusinessMessage(): Boolean = false
    fun isPassThrough(): Boolean = passThrough == 1

    fun setArrivedMessage(value: Boolean) {}
    fun setBusinessMessage(value: Boolean) {}

    fun toBundle(): Bundle {
        return Bundle().apply {
            putString(KEY_ALIAS, alias)
            putString(KEY_CATEGORY, category)
            putString(KEY_CONTENT, content)
            putString(KEY_DESCRIPTION, description)
            putBoolean(KEY_IS_NOTIFIED, isNotified)
            putString(KEY_MESSAGE_ID, messageId)
            putInt(KEY_MESSAGE_TYPE, messageType)
            putInt(KEY_NOTIFY_ID, notifyId)
            putInt(KEY_NOTIFY_TYPE, notifyType)
            putInt(KEY_PASS_THROUGH, passThrough)
            putString(KEY_TITLE, title)
            putString(KEY_TOPIC, topic)
            putString(KEY_USER_ACCOUNT, userAccount)
            extra?.let {
                putBundle(KEY_EXTRA, Bundle().apply {
                    it.forEach { (k, v) -> putString(k, v) }
                })
            }
        }
    }

    override fun toString(): String {
        return "messageId={$messageId}, title={$title}, description={$description}, content={$content}, passThrough={$passThrough}, notifyType={$notifyType}, messageType={$messageType}, alias={$alias}, topic={$topic}, userAccount={$userAccount}, notifyId={$notifyId}, category={$category}, isNotified={$isNotified}"
    }
}
