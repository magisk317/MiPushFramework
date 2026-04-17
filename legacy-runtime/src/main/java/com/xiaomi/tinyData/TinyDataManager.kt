package com.xiaomi.tinyData

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.LoggerInterface
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.TinyDataHelper
import com.xiaomi.push.service.TinyDataStorage
import com.xiaomi.xmpush.thrift.ClientUploadDataItem

class TinyDataManager private constructor(private val mContext: Context) {
    private val mUploaders: MutableMap<String, TinyDataUploader> = HashMap()

    private fun upload(
        sourcePackage: String,
        pkgName: String,
        category: String,
        name: String,
        counter: Long,
        data: String
    ): Boolean {
        val item = ClientUploadDataItem()
        item.category = category
        item.name = name
        item.counter = counter
        item.data = data
        item.setFromSdk(true)
        item.channel = "push_sdk_channel"
        item.sourcePackage = sourcePackage
        return upload(item, pkgName)
    }

    fun addUploader(tinyDataUploader: TinyDataUploader, str: String?) {
        if (TextUtils.isEmpty(str)) {
            MyLog.e("[TinyDataManager]: can not add a provider from unkown resource.")
            return
        }
        uploaders[str!!] = tinyDataUploader
    }

    internal fun getContext(): Context = mContext

    val uploader: TinyDataUploader?
        get() {
            mUploaders[UPLOADER_PUSH_CHANNEL]?.let { return it }
            mUploaders[UPLOADER_HTTP]?.let { return it }
            return null
        }

    val uploaders: MutableMap<String, TinyDataUploader>
        get() = mUploaders

    fun upload(item: ClientUploadDataItem, pkgName: String?): Boolean {
        if (TextUtils.isEmpty(pkgName)) {
            MyLog.w("pkgName is null or empty, upload ClientUploadDataItem failed.")
            return false
        }
        if (TinyDataHelper.verify(item, false)) {
            return false
        }
        if (TextUtils.isEmpty(item.id)) {
            item.id = TinyDataHelper.nextTinyDataItemId()
        }
        item.pkgName = pkgName
        TinyDataStorage.cacheTinyData(mContext, item)
        return true
    }

    fun upload(category: String, name: String, counter: Long, data: String): Boolean {
        return upload(mContext.packageName, mContext.packageName, category, name, counter, data)
    }

    fun upload(pkgName: String, category: String, name: String, counter: Long, data: String): Boolean {
        return upload(mContext.packageName, pkgName, category, name, counter, data)
    }

    companion object {
        const val UPLOADER_HTTP = "UPLOADER_HTTP"
        const val UPLOADER_PUSH_CHANNEL = "UPLOADER_PUSH_CHANNEL"

        @Volatile
        private var sInstance: TinyDataManager? = null

        fun getInstance(context: Context): TinyDataManager? {
            if (sInstance == null) {
                synchronized(TinyDataManager::class.java) {
                    if (sInstance == null) {
                        sInstance = TinyDataManager(context)
                    }
                }
            }
            return sInstance
        }
    }
}
