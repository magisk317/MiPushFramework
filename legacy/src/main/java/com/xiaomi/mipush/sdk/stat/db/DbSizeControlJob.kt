package com.xiaomi.mipush.sdk.stat.db

import android.content.Context
import com.xiaomi.mipush.sdk.stat.db.base.DbManager
import com.xiaomi.mipush.sdk.stat.util.FileUtil
import java.lang.ref.WeakReference

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/stat/db/DbSizeControlJob.java
 * No stock 7.4.67-C same-path stat source was found in the split source tree.
 */
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
