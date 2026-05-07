package com.xiaomi.mipush.sdk.stat.db

import com.xiaomi.mipush.sdk.stat.db.base.DbManager

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/stat/db/MessageDeleteJob.java
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
