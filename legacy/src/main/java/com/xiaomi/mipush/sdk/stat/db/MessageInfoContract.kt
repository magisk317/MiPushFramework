package com.xiaomi.mipush.sdk.stat.db
import io.github.magisk317.mipush.protocol.model.*

import android.database.Cursor
import android.provider.BaseColumns

object MessageInfoContract {
    const val LIMIT: Int = 10
    const val NO_UPLOAD: Int = 0
    const val TIMEOUT: Long = 30000
    const val UPLOADED: Int = 2
    const val UPLOADING: Int = 1

    abstract class MessageEntry : BaseColumns {
        companion object {
            const val COLUMN_NAME_ID: String = "rowDataId"
            const val COLUMN_NAME_APP_ID: String = "appId"
            const val COLUMN_NAME_MESSAGE_ID: String = "messageId"
            const val COLUMN_NAME_MESSAGE_ITEM_ID: String = "messageItemId"
            const val COLUMN_NAME_MESSAGE_ITEM: String = "messageItem"
            const val COLUMN_NAME_CREATE_TIMESTAMP: String = "createTimeStamp"
            const val COLUMN_NAME_UPLOAD_TIMESTAMP: String = "uploadTimestamp"
            const val COLUMN_NAME_STATUS: String = "status"
            const val COLUMN_NAME_APP_PACKAGE_NAME: String = "packageName"
            const val TABLE_NAME: String = "messageTable"
        }
    }

    class MessageModel {
        var id: Long = 0
        var appId: String? = null
        var messageId: String? = null
        var messageItemId: String? = null
        var messageItem: ByteArray? = null
        var packageName: String? = null
        var createTimeStamp: Long = 0
        var uploadTimeStamp: Long = 0
        var status: Int = 0

        companion object {
            fun build(cursor: Cursor): MessageModel {
                return MessageModel().apply {
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(MessageEntry.COLUMN_NAME_ID))
                    appId = cursor.getString(cursor.getColumnIndexOrThrow(MessageEntry.COLUMN_NAME_APP_ID))
                    messageItem = cursor.getBlob(cursor.getColumnIndexOrThrow(MessageEntry.COLUMN_NAME_MESSAGE_ITEM))
                    messageItemId = cursor.getString(cursor.getColumnIndexOrThrow(MessageEntry.COLUMN_NAME_MESSAGE_ITEM_ID))
                    messageId = cursor.getString(cursor.getColumnIndexOrThrow(MessageEntry.COLUMN_NAME_MESSAGE_ID))
                    packageName = cursor.getString(cursor.getColumnIndexOrThrow(MessageEntry.COLUMN_NAME_APP_PACKAGE_NAME))
                    createTimeStamp = cursor.getLong(cursor.getColumnIndexOrThrow(MessageEntry.COLUMN_NAME_CREATE_TIMESTAMP))
                    uploadTimeStamp = cursor.getLong(cursor.getColumnIndexOrThrow(MessageEntry.COLUMN_NAME_UPLOAD_TIMESTAMP))
                    status = cursor.getInt(cursor.getColumnIndexOrThrow(MessageEntry.COLUMN_NAME_STATUS))
                }
            }
        }
    }
}
