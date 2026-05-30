package com.xiaomi.mipush.sdk.stat.db

import android.content.Context
import android.database.Cursor
import com.xiaomi.mipush.sdk.stat.db.base.DbManager
import com.xiaomi.push.mpcd.Constants

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/stat/db/MessageCountQueryJob.java
 * No stock 7.4.67-C same-path stat source was found in the split source tree.
 */
open class MessageCountQueryJob(
    str: String,
    list: List<String>,
    str2: String?,
    strArr: Array<String>?,
    str3: String?,
    str4: String?,
    str5: String?,
    i: Int,
    var mDescription: String,
) : DbManager.BaseQueryJob<Long>(str, list, str2, strArr, str3, str4, str5, i) {

    private var mResult: Long = 0L

    companion object {
        fun getMessageCountJob(str: String): MessageCountQueryJob {
            val arrayList = ArrayList<String>()
            arrayList.add("count(*)")
            return MessageCountQueryJob(
                str,
                arrayList,
                null,
                null,
                null,
                null,
                null,
                0,
                "job to get count of all message",
            )
        }

        fun getNoUploadMessageCountJob(str: String): MessageCountQueryJob {
            val arrayList = ArrayList<String>()
            arrayList.add("count(*)")
            return MessageCountQueryJob(
                str,
                arrayList,
                "status = ? or (status = ? and uploadTimestamp <= ?${Constants.SEPARATOR_RIGHT_PARENTESIS}",
                arrayOf("0", "1", (System.currentTimeMillis() - MessageInfoContract.TIMEOUT).toString()),
                null,
                null,
                null,
                0,
                "job to get count of noUpload message",
            )
        }
    }

    override fun description(): String = mDescription

    override fun notifyResult(context: Context, list: List<Long>) {
        if (list.isEmpty()) return
        mResult = list[0]
    }

    override fun output(): Any = mResult

    override fun processOneData(context: Context, cursor: Cursor): Long = cursor.getLong(0)
}
