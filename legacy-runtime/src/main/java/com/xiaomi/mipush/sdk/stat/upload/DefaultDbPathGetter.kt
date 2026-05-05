package com.xiaomi.mipush.sdk.stat.upload

import android.content.Context
import com.xiaomi.mipush.sdk.stat.db.DataBaseConfig
import java.io.File

/*
 * Local legacy stat database path getter retained for compatibility.
 * No stock 7.4.67-C or 2026-04-13 current override same-path source was found in the dump.
 */
class DefaultDbPathGetter : IDbPathGetter {
    override fun getPath(context: Context, str: String): String {
        return context.getDatabasePath(DataBaseConfig.DATABASE_NAME)
            .parentFile!!.absolutePath
    }

    override fun getPathList(context: Context): List<String> {
        return listOf(
            context.getDatabasePath(DataBaseConfig.DATABASE_NAME)
                .parentFile!!.absolutePath,
        )
    }
}
