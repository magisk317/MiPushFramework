package com.xiaomi.mipush.sdk.stat.db

import android.content.ContentValues
import android.content.Context
import com.xiaomi.mipush.sdk.stat.db.base.DbManager
import com.xiaomi.mipush.sdk.stat.PushStatClientManager
import com.xiaomi.xmpush.thrift.ClientUploadDataItem
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils

open class MessageInsertJob(
    str: String,
    contentValues: ContentValues,
    var mDescription: String,
) : DbManager.InsertJob(str, contentValues) {

    companion object {
        fun buildInsertJob(
            context: Context,
            str: String,
            clientUploadDataItem: ClientUploadDataItem,
        ): MessageInsertJob? {
            val bArrConvertThriftObjectToBytes =
                XmPushThriftSerializeUtils.convertThriftObjectToBytes(clientUploadDataItem)
            if (bArrConvertThriftObjectToBytes == null || bArrConvertThriftObjectToBytes.isEmpty()) {
                return null
            }
            val contentValues = ContentValues()
            contentValues.put("status", 0)
            contentValues.put("messageId", "")
            contentValues.put(
                MessageInfoContract.MessageEntry.COLUMN_NAME_MESSAGE_ITEM_ID,
                clientUploadDataItem.id,
            )
            contentValues.put(
                MessageInfoContract.MessageEntry.COLUMN_NAME_MESSAGE_ITEM,
                bArrConvertThriftObjectToBytes,
            )
            contentValues.put(
                MessageInfoContract.MessageEntry.COLUMN_NAME_APP_ID,
                PushStatClientManager.getInstance(context).appId,
            )
            contentValues.put(
                MessageInfoContract.MessageEntry.COLUMN_NAME_APP_PACKAGE_NAME,
                PushStatClientManager.getInstance(context).packageName,
            )
            contentValues.put(
                MessageInfoContract.MessageEntry.COLUMN_NAME_CREATE_TIMESTAMP,
                System.currentTimeMillis(),
            )
            contentValues.put(MessageInfoContract.MessageEntry.COLUMN_NAME_UPLOAD_TIMESTAMP, 0)
            return MessageInsertJob(
                str,
                contentValues,
                "a job build to insert message to db",
            )
        }
    }

    override fun description(): String = mDescription
}
