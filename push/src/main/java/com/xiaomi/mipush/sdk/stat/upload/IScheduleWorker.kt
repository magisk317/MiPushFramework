package com.xiaomi.mipush.sdk.stat.upload

import android.content.Context

interface IScheduleWorker {
    fun onDelete(context: Context)

    fun onUpload(context: Context)
}
