package com.xiaomi.mipush.sdk.stat.db

object DataBaseConfig {
    var DATABASE_NAME: String = "MessageInfo.db"
    private const val DEFAULT_MAX_DB_SIZE: Long = 52428800
    var MAX_DB_SIZE: Long = DEFAULT_MAX_DB_SIZE
    var DEFAULT_NUM: Int = 200
}
