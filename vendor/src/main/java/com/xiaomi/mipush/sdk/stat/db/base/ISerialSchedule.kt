package com.xiaomi.mipush.sdk.stat.db.base

import android.content.Context

interface ISerialSchedule {
    fun input(context: Context, obj: Any)

    fun output(): Any?
}
