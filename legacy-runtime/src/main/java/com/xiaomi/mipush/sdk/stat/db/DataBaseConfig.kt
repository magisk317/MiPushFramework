package com.xiaomi.mipush.sdk.stat.db

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/stat/db/DataBaseConfig.java
 * No stock 7.4.67-C same-path stat source was found in the split source tree.
 */
object DataBaseConfig {
    var DATABASE_NAME: String = "MessageInfo.db"
    private const val DEFAULT_MAX_DB_SIZE: Long = 52428800
    var MAX_DB_SIZE: Long = DEFAULT_MAX_DB_SIZE
    var DEFAULT_NUM: Int = 200
}
