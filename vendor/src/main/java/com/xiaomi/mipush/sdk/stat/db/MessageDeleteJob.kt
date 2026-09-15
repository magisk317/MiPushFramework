package com.xiaomi.mipush.sdk.stat.db

import com.xiaomi.mipush.sdk.stat.db.base.DbManager

/*
 * No stock 7.4.67-C same-path stat source was found in the split source tree.
 */
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
