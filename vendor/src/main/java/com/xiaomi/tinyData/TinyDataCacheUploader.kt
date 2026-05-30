package com.xiaomi.tinyData

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.TinyDataHelper
import com.xiaomi.xmpush.thrift.ClientUploadDataItem

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/wa/c.java
 * Stock class name is obfuscated as wa.c; this file keeps the deobfuscated com.xiaomi.tinyData.TinyDataCacheUploader API.
 */
object TinyDataCacheUploader {
    private fun prepareTinyDataItems(context: Context, list: List<ClientUploadDataItem>?): HashMap<String, ArrayList<ClientUploadDataItem>>? {
        if (list.isNullOrEmpty()) return null
        val map = HashMap<String, ArrayList<ClientUploadDataItem>>()
        for (item in list) {
            verifyTinyDataUploadItemValue(context, item)
            val listForPackage = map[item.sourcePackage]
            val arrayList = listForPackage ?: ArrayList<ClientUploadDataItem>().also {
                map[item.sourcePackage] = it
            }
            arrayList.add(item)
        }
        return map
    }

    private fun upload(context: Context, tinyDataUploader: TinyDataUploader, map: HashMap<String, ArrayList<ClientUploadDataItem>>) {
        for (entry in map.entries) {
            try {
                val value = entry.value
                if (!value.isNullOrEmpty()) {
                    tinyDataUploader.upload(value, value[0].pkgName, entry.key)
                }
            } catch (e: Exception) {
            }
        }
    }

    fun uploadTinyData(context: Context, tinyDataUploader: TinyDataUploader?, list: List<ClientUploadDataItem>?) {
        val map = prepareTinyDataItems(context, list)
        if (map != null && map.isNotEmpty()) {
            tinyDataUploader?.let { upload(context, it, map) }
            return
        }
        MyLog.w("TinyData TinyDataCacheUploader.uploadTinyData itemsUploading == null || itemsUploading.size() == 0  ts:${System.currentTimeMillis()}")
    }

    private fun verifyTinyDataUploadItemValue(context: Context, item: ClientUploadDataItem) {
        if (item.fromSdk) {
            item.channel = "push_sdk_channel"
        }
        if (TextUtils.isEmpty(item.id)) {
            item.id = TinyDataHelper.nextTinyDataItemId()
        }
        item.timestamp = System.currentTimeMillis()
        if (TextUtils.isEmpty(item.pkgName)) {
            item.sourcePackage = context.packageName
        }
        if (TextUtils.isEmpty(item.sourcePackage)) {
            item.sourcePackage = item.pkgName
        }
    }
}
