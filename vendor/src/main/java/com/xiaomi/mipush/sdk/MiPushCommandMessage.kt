package com.xiaomi.mipush.sdk

import android.os.Bundle
import com.xiaomi.mipush.sdk.PushMessageHandler.PushMessageInterface
import java.io.Serializable

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/com/xiaomi/mipush/sdk/MiPushCommandMessage.java
 * Current override same-path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/MiPushCommandMessage.java
 */
class MiPushCommandMessage : PushMessageInterface, Serializable {
    var category: String? = null
    var command: String? = null
    var commandArguments: List<String>? = null
    var reason: String? = null
    var resultCode: Long = 0

    companion object {
        private const val KEY_CATEGORY = "category"
        private const val KEY_COMMAND = "command"
        private const val KEY_COMMAND_ARGUMENTS = "commandArguments"
        private const val KEY_REASON = "reason"
        private const val KEY_RESULT_CODE = "resultCode"
        private const val serialVersionUID: Long = 1

        @JvmStatic
        fun fromBundle(bundle: Bundle): MiPushCommandMessage {
            return MiPushCommandMessage().apply {
                command = bundle.getString(KEY_COMMAND)
                resultCode = bundle.getLong(KEY_RESULT_CODE)
                reason = bundle.getString(KEY_REASON)
                commandArguments = bundle.getStringArrayList(KEY_COMMAND_ARGUMENTS)
                category = bundle.getString(KEY_CATEGORY)
            }
        }
    }

    fun toBundle(): Bundle {
        return Bundle().apply {
            putString(KEY_COMMAND, command)
            putLong(KEY_RESULT_CODE, resultCode)
            putString(KEY_REASON, reason)
            commandArguments?.let {
                putStringArrayList(KEY_COMMAND_ARGUMENTS, ArrayList(it))
            }
            putString(KEY_CATEGORY, category)
        }
    }

    override fun toString(): String {
        return "command={$command}, resultCode={$resultCode}, reason={$reason}, category={$category}, commandArguments={$commandArguments}"
    }
}
