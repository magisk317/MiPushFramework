package com.xiaomi.mipush.sdk.stat.upload
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context

interface IScheduleWorker {
    fun onDelete(context: Context)

    fun onUpload(context: Context)
}
