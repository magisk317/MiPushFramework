package com.xiaomi.mipush.sdk.stat.db.base

import android.content.Context

abstract class BaseDbHelperFactory {
    abstract fun getDbHelper(context: Context, str: String): BaseDbHelper
}
