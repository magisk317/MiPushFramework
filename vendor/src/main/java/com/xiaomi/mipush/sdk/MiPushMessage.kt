package com.xiaomi.mipush.sdk

import android.os.Bundle
import com.xiaomi.mipush.sdk.PushMessageHandler.PushMessageInterface
import java.io.Serializable
import java.util.HashMap

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/com/xiaomi/mipush/sdk/MiPushMessage.java
 * Current override same-path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/MiPushMessage.java
 */
class MiPushMessage : PushMessageInterface, Serializable {
    private var arrived: Boolean = false

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
    var extra: Map<String, String>? = HashMap()
        set(value) {
            field = value?.let { entries -> HashMap(entries) }
        }

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
        private const val KEY_USER_ACCOUNT = "user_account"
        private const val LEGACY_KEY_USER_ACCOUNT = "userAccount"
        private const val KEY_EXTRA = "extra"
        private const val serialVersionUID: Long = 1

        const val MESSAGE_TYPE_REG = 0
        const val MESSAGE_TYPE_ALIAS = 1
        const val MESSAGE_TYPE_TOPIC = 2
        const val MESSAGE_TYPE_ACCOUNT = 3

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
                // Stock 3.7.9 and 7.4.67-C both use `user_account` and a Serializable HashMap.
                // Older MiPushFramework builds emitted `userAccount` plus a nested Bundle, so
                // keep read-only migration support while all new output follows the stock wire.
                userAccount = bundle.getString(KEY_USER_ACCOUNT)
                    ?: bundle.getString(LEGACY_KEY_USER_ACCOUNT)
                extra = readExtra(bundle)
            }
        }

        @Suppress("DEPRECATION")
        private fun readExtra(bundle: Bundle): Map<String, String>? {
            val stockValue = bundle.getSerializable(KEY_EXTRA)
            if (stockValue is Map<*, *>) {
                return stockValue.entries.mapNotNull { (key, value) ->
                    val stringKey = key as? String ?: return@mapNotNull null
                    val stringValue = value as? String ?: return@mapNotNull null
                    stringKey to stringValue
                }.toMap()
            }
            return bundle.getBundle(KEY_EXTRA)?.keySet()?.associateWith { key ->
                bundle.getBundle(KEY_EXTRA)?.getString(key).orEmpty()
            }
        }
    }

    fun isArrivedMessage(): Boolean = arrived
    fun isBusinessMessage(): Boolean = false
    fun isPassThrough(): Boolean = passThrough == 1

    fun setArrivedMessage(value: Boolean) {
        arrived = value
    }

    fun setBusinessMessage(value: Boolean) {}

    fun toBundle(): Bundle {
        return Bundle().apply {
            alias?.takeIf(String::isNotEmpty)?.let { putString(KEY_ALIAS, it) }
            category?.takeIf(String::isNotEmpty)?.let { putString(KEY_CATEGORY, it) }
            putString(KEY_CONTENT, content)
            description?.takeIf(String::isNotEmpty)?.let { putString(KEY_DESCRIPTION, it) }
            putBoolean(KEY_IS_NOTIFIED, isNotified)
            putString(KEY_MESSAGE_ID, messageId)
            putInt(KEY_MESSAGE_TYPE, messageType)
            putInt(KEY_NOTIFY_ID, notifyId)
            putInt(KEY_NOTIFY_TYPE, notifyType)
            putInt(KEY_PASS_THROUGH, passThrough)
            title?.takeIf(String::isNotEmpty)?.let { putString(KEY_TITLE, it) }
            topic?.takeIf(String::isNotEmpty)?.let { putString(KEY_TOPIC, it) }
            userAccount?.takeIf(String::isNotEmpty)?.let { putString(KEY_USER_ACCOUNT, it) }
            extra?.let {
                putSerializable(KEY_EXTRA, HashMap(it))
            }
        }
    }

    override fun toString(): String {
        return "messageId={$messageId}, title={$title}, description={$description}, content={$content}, passThrough={$passThrough}, notifyType={$notifyType}, messageType={$messageType}, alias={$alias}, topic={$topic}, userAccount={$userAccount}, notifyId={$notifyId}, category={$category}, isNotified={$isNotified}"
    }
}
