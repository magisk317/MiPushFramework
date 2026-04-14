package com.xiaomi.mipush.sdk.stat.db

import android.content.Context
import com.xiaomi.mipush.sdk.stat.db.base.BaseDbHelper
import com.xiaomi.mipush.sdk.stat.db.base.BaseDbHelperFactory

class MessageDbHelperFactory : BaseDbHelperFactory() {
    override fun getDbHelper(context: Context, str: String): BaseDbHelper {
        return MessageDbHelper.newInstance(context, str)
    }
}
