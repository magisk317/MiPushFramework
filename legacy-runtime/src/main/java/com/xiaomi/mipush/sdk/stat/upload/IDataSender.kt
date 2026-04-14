package com.xiaomi.mipush.sdk.stat.upload

import android.content.Context
import com.xiaomi.mipush.sdk.stat.db.MessageInfoContract

interface IDataSender {
    fun onFailed(context: Context, str: String, str2: String)

    fun onSend(context: Context, str: String, list: MutableList<MessageInfoContract.MessageModel>)

    fun onSuccess(context: Context, str: String, str2: String)
}
