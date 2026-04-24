package com.xiaomi.tinyData
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.xmpush.thrift.ClientUploadDataItem

interface TinyDataUploader {
    fun checkCanUpload(clientUploadDataItem: ClientUploadDataItem, str: String): Boolean

    fun upload(list: MutableList<ClientUploadDataItem>, str: String, str2: String)
}
