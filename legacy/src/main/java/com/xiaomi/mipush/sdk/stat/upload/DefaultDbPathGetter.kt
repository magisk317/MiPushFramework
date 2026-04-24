package com.xiaomi.mipush.sdk.stat.upload
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import com.xiaomi.mipush.sdk.stat.db.DataBaseConfig
import java.io.File

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
