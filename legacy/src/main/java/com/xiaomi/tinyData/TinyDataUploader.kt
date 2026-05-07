package com.xiaomi.tinyData

import com.xiaomi.xmpush.thrift.ClientUploadDataItem

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/wa/e.java
 * Stock class name is obfuscated as wa.e; this file keeps the deobfuscated com.xiaomi.tinyData.TinyDataUploader API.
 */
interface TinyDataUploader {
    fun checkCanUpload(clientUploadDataItem: ClientUploadDataItem, str: String): Boolean

    fun upload(list: MutableList<ClientUploadDataItem>, str: String, str2: String)
}
