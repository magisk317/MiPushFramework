package com.xiaomi.mipush.sdk.stat.db

import android.content.Context
import android.text.TextUtils
import com.xiaomi.mipush.sdk.stat.db.base.DbManager
import com.xiaomi.mipush.sdk.stat.util.FileUtil
import com.xiaomi.mipush.sdk.stat.PushStatClientManager

open class HistoryDataDeleteJob(
    str: String,
    str2: String,
    strArr: Array<String>,
    str3: String,
) : MessageDeleteJob(str, str2, strArr, str3) {

    companion object {
        fun deleteHistoryJob(context: Context, str: String, i: Int): HistoryDataDeleteJob? {
            MyLog.i("delete  messages when db size is too bigger")
            val tableName = DbManager.getInstance(context).getTableName(str)
            if (TextUtils.isEmpty(tableName)) {
                return null
            }
            val sb = StringBuilder()
            sb.append("rowDataId in (select ")
            sb.append("rowDataId from $tableName")
            sb.append(" order by createTimeStamp asc")
            sb.append(" limit ?)")
            return HistoryDataDeleteJob(
                str,
                sb.toString(),
                arrayOf(i.toString()),
                "a job build to delete history message",
            )
        }
    }

    private fun setLimit(j: Long) {
        if (mWhereValues == null || mWhereValues.size <= 0) return
        mWhereValues[0] = j.toString()
    }

    override fun description(): String = "$mDescription ${mWhereValues?.get(0)}"

    override fun input(context: Context, obj: Any) {
        if (obj is Long) {
            val jLongValue = obj
            val fileSize = FileUtil.getFileSize(dataPath)
            val j = DataBaseConfig.MAX_DB_SIZE
            if (fileSize <= j) {
                MyLog.i("db size is suitable")
                return
            }
            val j2 = (jLongValue * ((fileSize - j) * 1.2 / j)).toLong()
            setLimit(j2)
            PushStatClientManager.getInstance(context).record(
                "begin delete ${j2}noUpload messages , because db size is ${fileSize}B",
            )
            super.input(context, obj)
        }
    }
}
