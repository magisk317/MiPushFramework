package com.xiaomi.mipush.sdk.stat.db

/*
 * No stock 7.4.67-C same-path stat source was found in the split source tree.
 */
object DataBaseConfig {
    var DATABASE_NAME: String = "MessageInfo.db"
    private const val DEFAULT_MAX_DB_SIZE: Long = 52428800
    var MAX_DB_SIZE: Long = DEFAULT_MAX_DB_SIZE
    var DEFAULT_NUM: Int = 200
}
