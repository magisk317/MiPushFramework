package com.xiaomi.tinyData

import android.content.Context
import io.github.magisk317.xposed.logging.XLog
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
            val pkg = item.sourcePackage.orEmpty()
            val arrayList = map.getOrPut(pkg) { ArrayList() }
            arrayList.add(item)
        }
        return map
    }

    private fun upload(context: Context, tinyDataUploader: TinyDataUploader, map: HashMap<String, ArrayList<ClientUploadDataItem>>) {
        for (entry in map.entries) {
            try {
                val value = entry.value
                if (value.isNotEmpty()) {
                    tinyDataUploader.upload(value, value[0].pkgName, entry.key)
                }
            } catch (e: Exception) {
                XLog.w("TinyData upload error", e)
            }
        }
    }

    fun uploadTinyData(context: Context, tinyDataUploader: TinyDataUploader?, list: List<ClientUploadDataItem>?) {
        val map = prepareTinyDataItems(context, list)
        if (!map.isNullOrEmpty()) {
            tinyDataUploader?.let { upload(context, it, map) }
            return
        }
        XLog.w("TinyData TinyDataCacheUploader.uploadTinyData itemsUploading == null || itemsUploading.size() == 0  ts:${System.currentTimeMillis()}")
    }

    private fun verifyTinyDataUploadItemValue(context: Context, item: ClientUploadDataItem) {
        if (item.fromSdk) {
            item.channel = "push_sdk_channel"
        }
        if (item.id.isNullOrEmpty()) {
            item.id = TinyDataHelper.nextTinyDataItemId()
        }
        item.timestamp = System.currentTimeMillis()
        if (item.pkgName.isNullOrEmpty()) {
            item.sourcePackage = context.packageName
        }
        if (item.sourcePackage.isNullOrEmpty()) {
            item.sourcePackage = item.pkgName
        }
    }
}
