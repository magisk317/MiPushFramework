package com.xiaomi.push.mpcd.job
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.push.mpcd.Constants
import com.xiaomi.xmpush.thrift.ClientCollectionType

class StorageCollectionJob(context: Context, period: Int) : CollectionJob(context, period) {

    override fun collectInfo(): String {
        return "ram:${DeviceInfo.getRamSize()},rom:${DeviceInfo.getRomSize()}" +
            "${Constants.TYPE_SEPARATOR}ramOriginal:${DeviceInfo.getRamSizeOriginal()},romOriginal:${DeviceInfo.getRomSizeOriginal()}"
    }

    override fun getCollectionType(): ClientCollectionType = ClientCollectionType.Storage

    override fun getJobId(): String = "23"
}
