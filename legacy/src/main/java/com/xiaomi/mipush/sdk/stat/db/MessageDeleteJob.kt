package com.xiaomi.mipush.sdk.stat.db
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.mipush.sdk.stat.db.base.DbManager

open class MessageDeleteJob(
    str: String,
    str2: String,
    strArr: Array<String>,
    var mDescription: String,
) : DbManager.DeleteJob(str, str2, strArr) {

    companion object {
        fun deleteUploadedJob(str: String): MessageDeleteJob {
            return MessageDeleteJob(
                str,
                "status = ?",
                arrayOf("2"),
                "a job build to delete uploaded job",
            )
        }
    }

    override fun description(): String = mDescription
}
