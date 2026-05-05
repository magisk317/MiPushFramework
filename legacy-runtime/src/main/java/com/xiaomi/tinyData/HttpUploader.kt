package com.xiaomi.tinyData

import android.content.Context
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.xmpush.thrift.ClientUploadDataItem

/*
 * Local compatibility shim for the deobfuscated com.xiaomi.tinyData.HttpUploader API.
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067) does not keep
 * an HttpUploader counterpart in split-XiaomiServiceFrameworkCN-master; tiny data upload
 * is driven by wa.e and com.xiaomi.push.service/b0.java.
 */
class HttpUploader(private val mContext: Context) : TinyDataUploader {
    override fun checkCanUpload(clientUploadDataItem: ClientUploadDataItem, str: String): Boolean {
        return Network.hasNetwork(mContext)
    }

    override fun upload(list: MutableList<ClientUploadDataItem>, str: String, str2: String) {
    }
}
