package com.xiaomi.mipush.sdk.stat.upload

import android.content.Context
import android.text.TextUtils
import com.xiaomi.mipush.sdk.stat.db.DataBaseConfig
import java.io.File

class BaseScheduleWorker(
    private var mDbPathGetter: IDbPathGetter,
) : IScheduleWorker, IDbPathGetter {

    fun attachDbPathGetter(iDbPathGetter: IDbPathGetter) {
        mDbPathGetter = iDbPathGetter
    }

    override fun getPath(context: Context, str: String): String {
        return mDbPathGetter.getPath(context, str)
    }

    override fun getPathList(context: Context): List<String> {
        return mDbPathGetter.getPathList(context)
    }

    override fun onDelete(context: Context) {
        val pathList = getPathList(context)
        if (pathList.isNullOrEmpty()) return
        for (str in pathList) {
            if (!TextUtils.isEmpty(str)) {
                UploadDataHelper.delete(context, File(str, DataBaseConfig.DATABASE_NAME).absolutePath)
            }
        }
    }

    override fun onUpload(context: Context) {
        val pathList = getPathList(context)
        if (pathList.isNullOrEmpty()) return
        for (str in pathList) {
            if (!TextUtils.isEmpty(str)) {
                UploadDataHelper.upload(context, File(str, DataBaseConfig.DATABASE_NAME).absolutePath)
            }
        }
    }
}
