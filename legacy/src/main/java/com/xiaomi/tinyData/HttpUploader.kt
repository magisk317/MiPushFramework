package com.xiaomi.tinyData
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.xmpush.thrift.ClientUploadDataItem

class HttpUploader(private val mContext: Context) : TinyDataUploader {
    override fun checkCanUpload(clientUploadDataItem: ClientUploadDataItem, str: String): Boolean {
        return Network.hasNetwork(mContext)
    }

    override fun upload(list: MutableList<ClientUploadDataItem>, str: String, str2: String) {
    }
}
