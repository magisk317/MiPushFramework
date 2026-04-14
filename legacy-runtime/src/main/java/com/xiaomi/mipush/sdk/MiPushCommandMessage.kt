package com.xiaomi.mipush.sdk

import android.os.Bundle
import com.xiaomi.mipush.sdk.PushMessageHandler.PushMessageInterface
import java.io.Serializable

class MiPushCommandMessage : PushMessageInterface, Serializable {
    private var category: String? = null
    private var command: String? = null
    private var commandArguments: List<String>? = null
    private var reason: String? = null
    private var resultCode: Long = 0

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

    fun getCategory(): String? = category
    fun getCommand(): String? = command
    fun getCommandArguments(): List<String>? = commandArguments
    fun getReason(): String? = reason
    fun getResultCode(): Long = resultCode

    fun setCategory(category: String?) { this.category = category }
    fun setCommand(command: String?) { this.command = command }
    fun setCommandArguments(commandArguments: List<String>?) { this.commandArguments = commandArguments }
    fun setReason(reason: String?) { this.reason = reason }
    fun setResultCode(resultCode: Long) { this.resultCode = resultCode }

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
