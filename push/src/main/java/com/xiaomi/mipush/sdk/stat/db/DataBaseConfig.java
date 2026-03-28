package com.xiaomi.mipush.sdk.stat.db;

import com.xiaomi.slim.Blob;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/stat/db/DataBaseConfig.class */
public class DataBaseConfig {
    public static String DATABASE_NAME = "MessageInfo.db";
    private static final long DEFAULT_MAX_DB_SIZE = 52428800;
    public static long MAX_DB_SIZE = DEFAULT_MAX_DB_SIZE;
    public static int DEFAULT_NUM = Blob.ERROR_INVALID_CHID;
}
