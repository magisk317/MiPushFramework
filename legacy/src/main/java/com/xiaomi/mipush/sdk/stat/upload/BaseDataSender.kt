package com.xiaomi.mipush.sdk.stat.upload

import android.content.Context
import android.text.TextUtils
import com.xiaomi.mipush.sdk.stat.PushStatClientManager
import com.xiaomi.mipush.sdk.stat.db.DataBaseConfig
import com.xiaomi.mipush.sdk.stat.db.MessageInfoContract
import com.xiaomi.mipush.sdk.stat.db.MessageUpdateJob
import com.xiaomi.mipush.sdk.stat.db.MyLog
import com.xiaomi.mipush.sdk.stat.db.base.DbManager
import com.xiaomi.xmpush.thrift.ClientUploadDataItem
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils

/*
 * Local legacy stat data sender retained for compatibility.
 * No stock 7.4.67-C or 2026-04-13 current override same-path source was found in the dump.
 */
class BaseDataSender(
    private var mDbPathGetter: IDbPathGetter,
) : IDataSender, IDbPathGetter {

    fun attachDbPathGetter(iDbPathGetter: IDbPathGetter) {
        mDbPathGetter = iDbPathGetter
    }

    override fun getPath(context: Context, str: String): String {
        return mDbPathGetter.getPath(context, str)
    }

    override fun getPathList(context: Context): List<String> {
        return mDbPathGetter.getPathList(context)
    }

    override fun onFailed(context: Context, str: String, str2: String) {
        val absolutePath = java.io.File(getPath(context, str), DataBaseConfig.DATABASE_NAME).absolutePath
        if (TextUtils.isEmpty(absolutePath)) return
        UploadDataHelper.updateMessageStatus(context, absolutePath, str2, false)
    }

    override fun onSend(context: Context, str: String, list: MutableList<MessageInfoContract.MessageModel>) {
        if (list.isEmpty()) return
        val arrayList = ArrayList<ClientUploadDataItem>()
        for (messageModel in list) {
            val clientUploadDataItem = ClientUploadDataItem()
            try {
                XmPushThriftSerializeUtils.convertByteArrayToThriftObject(
                    clientUploadDataItem,
                    messageModel.messageItem,
                )
                if (TextUtils.isEmpty(clientUploadDataItem.id) ||
                    !messageModel.id.toString()
                        .equals(UploadDataHelper.getRowId(clientUploadDataItem.id), ignoreCase = true)
                ) {
                    clientUploadDataItem.id = UploadDataHelper.getTinyDataItemId(messageModel.id)
                }
                arrayList.add(clientUploadDataItem)
            } catch (e: Exception) {
                MyLog.e(e)
            }
        }
        val mapSend = UploadDataHelper.send(
            context,
            arrayList,
            PushStatClientManager.getInstance(context).packageName ?: "",
            PushStatClientManager.getInstance(context).appId ?: "",
            list[0].packageName ?: "",
        )
        val arrayList2 = ArrayList<DbManager.BaseJob>()
        for (str2 in mapSend.keys) {
            if (str2.isNotEmpty()) {
                arrayList2.add(
                    MessageUpdateJob.updateMessageId(str, str2, mapSend[str2] ?: ""),
                )
            }
        }
        PushStatClientManager.getInstance(context).exec(arrayList2)
    }

    override fun onSuccess(context: Context, str: String, str2: String) {
        val absolutePath = java.io.File(getPath(context, str), DataBaseConfig.DATABASE_NAME).absolutePath
        if (TextUtils.isEmpty(absolutePath)) return
        UploadDataHelper.updateMessageStatus(context, absolutePath, str2, true)
    }
}
