package com.xiaomi.mipush.sdk.stat.db.base
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context

abstract class BaseDbHelperFactory {
    abstract fun getDbHelper(context: Context, str: String): BaseDbHelper
}
