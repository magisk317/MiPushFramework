package com.xiaomi.mipush.sdk.stat.upload
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context

interface IDbPathGetter {
    fun getPath(context: Context, str: String): String

    fun getPathList(context: Context): List<String>
}
