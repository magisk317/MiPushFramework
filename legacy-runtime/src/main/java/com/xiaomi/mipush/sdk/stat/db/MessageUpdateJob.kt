package com.xiaomi.mipush.sdk.stat.db

import android.content.ContentValues
import com.xiaomi.mipush.sdk.stat.db.base.DbManager
import com.xiaomi.mipush.sdk.stat.upload.UploadDataHelper

open class MessageUpdateJob(
    str: String,
    str2: String,
    strArr: Array<String>?,
    contentValues: ContentValues,
    var mDescription: String,
) : DbManager.UpdateJob(str, str2, strArr, contentValues) {

    companion object {
        fun updateItemStatusAfterAck(str: String, str2: String, z: Boolean): MessageUpdateJob {
            val contentValues = ContentValues()
            contentValues.put("status", if (z) 2 else 0)
            return MessageUpdateJob(
                str,
                "messageId = ?",
                arrayOf(str2),
                contentValues,
                "a job build to update message status after receive ack",
            )
        }

        fun updateMessageId(str: String, str2: String, str3: String): MessageUpdateJob {
            val rowId = UploadDataHelper.getRowId(str2) ?: ""
            val contentValues = ContentValues()
            contentValues.put(
                MessageInfoContract.MessageEntry.COLUMN_NAME_UPLOAD_TIMESTAMP,
                System.currentTimeMillis(),
            )
            contentValues.put("status", 1)
            contentValues.put("messageId", str3)
            contentValues.put(MessageInfoContract.MessageEntry.COLUMN_NAME_MESSAGE_ITEM_ID, str2)
            return MessageUpdateJob(
                str,
                "rowDataId = ?",
                arrayOf(rowId),
                contentValues,
                "a job build to update message upload time and messageId",
            )
        }
    }

    override fun description(): String = mDescription
}
