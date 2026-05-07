package com.xiaomi.push.mpcd.job

import android.content.Context
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.push.mpcd.Constants
import com.xiaomi.xmpush.thrift.ClientCollectionType

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/v9/b.java
 * Current override same-path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/push/mpcd/job/StorageCollectionJob.java
 * Stock class name is obfuscated as v9.b; this file keeps the deobfuscated StorageCollectionJob API.
 */
class StorageCollectionJob(context: Context, period: Int) : CollectionJob(context, period) {

    override fun collectInfo(): String {
        return "ram:${DeviceInfo.getRamSize()},rom:${DeviceInfo.getRomSize()}" +
            "${Constants.TYPE_SEPARATOR}ramOriginal:${DeviceInfo.getRamSizeOriginal()},romOriginal:${DeviceInfo.getRomSizeOriginal()}"
    }

    override fun getCollectionType(): ClientCollectionType = ClientCollectionType.Storage

    override fun getJobId(): String = "23"
}
