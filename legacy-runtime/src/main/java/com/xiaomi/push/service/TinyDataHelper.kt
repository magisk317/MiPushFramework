package com.xiaomi.push.service

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.SystemUtils
import com.xiaomi.channel.commonutils.file.IOUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.string.XMStringUtils
import com.xiaomi.xmpush.thrift.ClientUploadData
import com.xiaomi.xmpush.thrift.ClientUploadDataItem
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import java.text.SimpleDateFormat
import java.util.concurrent.atomic.AtomicLong

object TinyDataHelper {
    const val DATA_MAX_SIZE = 10240
    const val KEY_UPLOAD_WAY = "uploadWay"
    const val SDK_CHANNEL = "push_sdk_channel"

    private var sdf = SimpleDateFormat("yyyy/MM/dd")
    private var dayPrefix = sdf.format(System.currentTimeMillis())
    private val idGen = AtomicLong(0)

    @JvmStatic
    fun cacheTinyData(context: Context, category: String, name: String, counter: Long, data: String) {
        val item = ClientUploadDataItem().apply {
            setCategory(category)
            setName(name)
            setCounter(counter)
            setData(data)
            channel = SDK_CHANNEL
            pkgName = context.packageName
            sourcePackage = context.packageName
            fromSdk = true
            timestamp = System.currentTimeMillis()
            id = nextTinyDataItemId()
        }
        TinyDataStorage.cacheTinyData(context, item)
    }

    private fun generateUploadTinyDataNotification(
        packageName: String,
        appId: String,
        uploadData: ClientUploadData,
    ): XmPushActionNotification {
        return XmPushActionNotification("-1", false)
            .setPackageName(packageName)
            .setAppId(appId)
            .setBinaryExtra(IOUtils.gZip(XmPushThriftSerializeUtils.convertThriftObjectToBytes(uploadData)))
            .setType(NotificationType.UploadTinyData.value)
    }

    @JvmStatic
    fun nextTinyDataItemId(): String {
        synchronized(TinyDataHelper::class.java) {
            val today = sdf.format(System.currentTimeMillis())
            if (!TextUtils.equals(dayPrefix, today)) {
                idGen.set(0L)
                dayPrefix = today
            }
            return today + "-" + idGen.incrementAndGet()
        }
    }

    @JvmStatic
    fun pack(items: List<ClientUploadDataItem>?, packageName: String, appId: String?, maxSize: Int): ArrayList<XmPushActionNotification>? {
        if (items == null) {
            MyLog.e("requests can not be null in TinyDataHelper.transToThriftObj().")
            return null
        }
        if (items.isEmpty()) {
            MyLog.e("requests.length is 0 in TinyDataHelper.transToThriftObj().")
            return null
        }

        val notifications = arrayListOf<XmPushActionNotification>()
        val resolvedAppId = appId ?: return null
        var uploadData = ClientUploadData()
        var currentSize = 0
        for (item in items) {
            var declaredSize = 0
            val extra = item.extra
            if (extra != null && extra.containsKey("item_size")) {
                val sizeValue = extra["item_size"]
                if (sizeValue != null) {
                    declaredSize = sizeValue.toIntOrNull() ?: 0
                }
                extra.remove("item_size")
            }
            val actualSize = if (declaredSize > 0) declaredSize else XmPushThriftSerializeUtils.convertThriftObjectToBytes(item).size
            if (actualSize > maxSize) {
                MyLog.e("TinyData is too big, ignore upload request item:${item.id}")
                continue
            }
            if (currentSize + actualSize > maxSize) {
                notifications.add(generateUploadTinyDataNotification(packageName, resolvedAppId, uploadData))
                uploadData = ClientUploadData()
                currentSize = 0
            }
            uploadData.addToUploadDataItems(item)
            currentSize += actualSize
        }
        if (uploadData.uploadDataItemsSize != 0) {
            notifications.add(generateUploadTinyDataNotification(packageName, resolvedAppId, uploadData))
        }
        return notifications
    }

    @JvmStatic
    fun shouldUpload(packageName: String): Boolean {
        return !SystemUtils.isGlobalVersion() || packageName == "com.miui.hybrid"
    }

    @JvmStatic
    fun verify(item: ClientUploadDataItem?, channelOptional: Boolean): Boolean {
        if (item == null) {
            MyLog.w("item is null, verfiy ClientUploadDataItem failed.")
            return true
        }
        if (!channelOptional && TextUtils.isEmpty(item.channel)) {
            MyLog.w("item.channel is null or empty, verfiy ClientUploadDataItem failed.")
            return true
        }
        if (TextUtils.isEmpty(item.category)) {
            MyLog.w("item.category is null or empty, verfiy ClientUploadDataItem failed.")
            return true
        }
        if (TextUtils.isEmpty(item.name)) {
            MyLog.w("item.name is null or empty, verfiy ClientUploadDataItem failed.")
            return true
        }
        if (!XMStringUtils.checkAllAscii(item.category)) {
            MyLog.w("item.category can only contain ascii char, verfiy ClientUploadDataItem failed.")
            return true
        }
        if (!XMStringUtils.checkAllAscii(item.name)) {
            MyLog.w("item.name can only contain ascii char, verfiy ClientUploadDataItem failed.")
            return true
        }
        if (item.data == null || item.data.length <= DATA_MAX_SIZE) {
            return false
        }
        MyLog.w("item.data is too large(${item.data.length}), max size for data is $DATA_MAX_SIZE , verfiy ClientUploadDataItem failed.")
        return true
    }
}
