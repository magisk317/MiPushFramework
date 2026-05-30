package com.xiaomi.mipush.sdk.stat.db

import android.content.Context
import com.xiaomi.mipush.sdk.stat.db.base.BaseDbHelper
import com.xiaomi.mipush.sdk.stat.db.base.BaseDbHelperFactory

/*
 * Local legacy stat database helper factory retained for compatibility.
 * No stock 7.4.67-C or 2026-04-13 current override same-path source was found in the dump.
 */
class MessageDbHelperFactory : BaseDbHelperFactory() {
    override fun getDbHelper(context: Context, str: String): BaseDbHelper {
        return MessageDbHelper.newInstance(context, str)
    }
}
