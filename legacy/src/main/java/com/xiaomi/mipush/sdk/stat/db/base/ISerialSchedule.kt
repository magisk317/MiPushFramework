package com.xiaomi.mipush.sdk.stat.db.base
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context

interface ISerialSchedule {
    fun input(context: Context, obj: Any)

    fun output(): Any?
}
