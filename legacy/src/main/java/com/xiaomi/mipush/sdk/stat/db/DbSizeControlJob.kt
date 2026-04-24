package com.xiaomi.mipush.sdk.stat.db
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import com.xiaomi.mipush.sdk.stat.db.base.DbManager
import com.xiaomi.mipush.sdk.stat.util.FileUtil
import java.lang.ref.WeakReference

class DbSizeControlJob(
    private val mPath: String,
    private val mWRContext: WeakReference<Context>,
) : Runnable {

    override fun run() {
        val context = mWRContext.get() ?: return
        if (FileUtil.getFileSize(mPath) <= DataBaseConfig.MAX_DB_SIZE) {
            MyLog.i("=====> do not need clean db")
            return
        }
        val messageDeleteJobDeleteUploadedJob = MessageDeleteJob.deleteUploadedJob(mPath)
        val messageCountJob = MessageCountQueryJob.getMessageCountJob(mPath)
        messageDeleteJobDeleteUploadedJob.append(messageCountJob)
        messageCountJob.append(
            HistoryDataDeleteJob.deleteHistoryJob(context, mPath, 1000),
        )
        DbManager.getInstance(context).exec(messageDeleteJobDeleteUploadedJob)
    }
}
