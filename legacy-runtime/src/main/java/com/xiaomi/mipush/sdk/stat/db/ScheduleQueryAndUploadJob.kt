package com.xiaomi.mipush.sdk.stat.db

import android.content.Context
import com.xiaomi.mipush.sdk.stat.db.base.DbManager
import com.xiaomi.push.mpcd.Constants

/*
 * Local legacy stat upload scheduler query retained for compatibility.
 * No stock 7.4.67-C or 2026-04-13 current override same-path source was found in the dump.
 */
open class ScheduleQueryAndUploadJob(
    str: String,
    list: List<String>,
    str2: String,
    strArr: Array<String>,
    str3: String?,
    str4: String?,
    str5: String?,
    i: Int,
) : MessageCountQueryJob(
    str,
    list,
    str2,
    strArr,
    str3,
    str4,
    str5,
    i,
    "job to schedule upload jobs",
) {

    companion object {
        fun getScheduleJob(str: String): ScheduleQueryAndUploadJob {
            val arrayList = ArrayList<String>()
            arrayList.add("count(*)")
            return ScheduleQueryAndUploadJob(
                str,
                arrayList,
                "status = ? or (status = ? and uploadTimestamp <= ?${Constants.SEPARATOR_RIGHT_PARENTESIS}",
                arrayOf("0", "1", (System.currentTimeMillis() - MessageInfoContract.TIMEOUT).toString()),
                null,
                null,
                null,
                0,
            )
        }
    }

    override fun notifyResult(context: Context, list: List<Long>) {
        val suitableLimit = com.xiaomi.mipush.sdk.stat.util.FileUtil.getSuitableLimit(context)
        val jLongValue = list[0]
        if (jLongValue <= suitableLimit) {
            DbManager.getInstance(context).execDelay(
                MessageQueryJob.newInstance(dataPath, jLongValue.toInt(), jLongValue.toInt()),
                0,
            )
            return
        }
        var j: Long = suitableLimit.toLong()
        var messageQueryJob: MessageQueryJob? = null
        var messageQueryJob2: MessageQueryJob? = null
        while (j < jLongValue) {
            val messageQueryJobNewInstance =
                MessageQueryJob.newInstance(dataPath, suitableLimit, jLongValue.toInt())
            val messageQueryJob4 = if (messageQueryJob == null) messageQueryJobNewInstance else messageQueryJob
            if (messageQueryJob2 != null) {
                messageQueryJob2.append(messageQueryJobNewInstance)
            }
            messageQueryJob2 = messageQueryJobNewInstance
            j += suitableLimit.toLong()
            messageQueryJob = messageQueryJob4
        }
        val i2 = ((jLongValue - j) + suitableLimit.toLong()).toInt()
        val messageQueryJobNewInstance2 = MessageQueryJob.newInstance(dataPath, i2, i2)
        if (messageQueryJob2 != null) {
            messageQueryJob2.append(messageQueryJobNewInstance2)
        }
        DbManager.getInstance(context).exec(messageQueryJob)
    }
}
