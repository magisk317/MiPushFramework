package com.xiaomi.mipush.sdk.stat.db

import android.content.Context
import android.database.Cursor
import com.xiaomi.mipush.sdk.stat.db.base.DbManager
import com.xiaomi.mipush.sdk.stat.PushStatClientManager
import com.xiaomi.push.mpcd.Constants

open class MessageQueryJob(
    str: String,
    list: List<String>,
    str2: String,
    strArr: Array<String>,
    str3: String?,
    str4: String?,
    str5: String?,
    i: Int,
    var mDescription: String,
    private var mCount: Int,
) : DbManager.BaseQueryJob<MessageInfoContract.MessageModel>(
    str,
    list,
    str2,
    strArr,
    str3,
    str4,
    str5,
    i,
) {

    companion object {
        fun newInstance(str: String, i: Int, i2: Int): MessageQueryJob {
            val arrayList = ArrayList<String>()
            arrayList.add(MessageInfoContract.MessageEntry.COLUMN_NAME_ID)
            arrayList.add("messageId")
            arrayList.add(MessageInfoContract.MessageEntry.COLUMN_NAME_MESSAGE_ITEM_ID)
            arrayList.add(MessageInfoContract.MessageEntry.COLUMN_NAME_MESSAGE_ITEM)
            arrayList.add(MessageInfoContract.MessageEntry.COLUMN_NAME_CREATE_TIMESTAMP)
            arrayList.add(MessageInfoContract.MessageEntry.COLUMN_NAME_UPLOAD_TIMESTAMP)
            arrayList.add(MessageInfoContract.MessageEntry.COLUMN_NAME_APP_ID)
            arrayList.add(MessageInfoContract.MessageEntry.COLUMN_NAME_APP_PACKAGE_NAME)
            arrayList.add("status")
            return MessageQueryJob(
                str,
                arrayList,
                "status = ? or (status = ? and uploadTimestamp <= ?${Constants.SEPARATOR_RIGHT_PARENTESIS}",
                arrayOf("0", "1", (System.currentTimeMillis() - MessageInfoContract.TIMEOUT).toString()),
                null,
                null,
                "createTimeStamp asc",
                i,
                "a job build to query db",
                i2,
            )
        }
    }

    override fun description(): String = "$mDescription  limit = $limitValue"

    override fun input(context: Context, obj: Any) {
        if (obj !is IntArray) return
        val i = obj[0]
        val i2 = obj[1]
        if (i <= 0 || i2 <= 0) return
        setLimitValue(i)
        mCount = i2
        super.input(context, obj)
    }

    override fun notifyResult(context: Context, list: List<MessageInfoContract.MessageModel>) {
        PushStatClientManager.getInstance(context).send(dataPath, list)
    }

    override fun output(): Any? {
        val limitVal = limitValue
        val i = mCount - limitVal
        return if (limitVal > 0 && i > 0) intArrayOf(limitVal, i) else super.output()
    }

    override fun processOneData(context: Context, cursor: Cursor): MessageInfoContract.MessageModel? {
        return if (cursor != null) MessageInfoContract.MessageModel.build(cursor) else null
    }
}
