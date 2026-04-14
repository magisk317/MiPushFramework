package com.xiaomi.mipush.sdk

import com.xiaomi.channel.commonutils.logger.MyLog

import android.content.Context
import android.content.pm.PackageInfo
import android.text.TextUtils
import com.xiaomi.push.service.PacketHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.TinyDataHelper
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.ClientUploadDataItem
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

object MiTinyDataClient {
    const val PENDING_REASON_APPID = "com.xiaomi.xmpushsdk.tinydataPending.appId"
    const val PENDING_REASON_CHANNEL = "com.xiaomi.xmpushsdk.tinydataPending.channel"
    const val PENDING_REASON_INIT = "com.xiaomi.xmpushsdk.tinydataPending.init"

    @JvmStatic
    fun init(context: Context?, channel: String?) {
        if (context == null) {
            MyLog.w("context is null, MiTinyDataClient.init(Context, String) failed.")
            return
        }
        MiTinyDataClientImp.getInstance().init(context)
        if (TextUtils.isEmpty(channel)) {
            MyLog.w("channel is null or empty, MiTinyDataClient.init(Context, String) failed.")
        } else {
            MiTinyDataClientImp.getInstance().channel = channel
        }
    }

    @JvmStatic
    fun upload(context: Context, clientUploadDataItem: ClientUploadDataItem): Boolean {
        MyLog.v("MiTinyDataClient.upload ${clientUploadDataItem.id}")
        if (!MiTinyDataClientImp.getInstance().alreadyInit()) {
            MiTinyDataClientImp.getInstance().init(context)
        }
        return MiTinyDataClientImp.getInstance().processUploadRequest(clientUploadDataItem)
    }

    @JvmStatic
    fun upload(context: Context, category: String, name: String, counter: Long, data: String): Boolean {
        val clientUploadDataItem = ClientUploadDataItem().apply {
            setCategory(category)
            setName(name)
            setCounter(counter)
            setData(data)
            isFromSdk = true
            channel = "push_sdk_channel"
        }
        return upload(context, clientUploadDataItem)
    }

    @JvmStatic
    fun upload(category: String, name: String, counter: Long, data: String): Boolean {
        val clientUploadDataItem = ClientUploadDataItem().apply {
            setCategory(category)
            setName(name)
            setCounter(counter)
            setData(data)
        }
        return MiTinyDataClientImp.getInstance().processUploadRequest(clientUploadDataItem)
    }

    class MiTinyDataClientImp private constructor() {
        companion object {
            private const val MAX_PENDING_SIZE = 100
            private const val MAX_SIZE = 30720
            private const val MIN_SEND_TIMESPAN = 1000L
            private const val MIN_TINYDATA_XMSF_VERSION = 108

            @Volatile
            private var sInstance: MiTinyDataClientImp? = null

            @JvmStatic
            fun getInstance(): MiTinyDataClientImp {
                return sInstance ?: synchronized(this) {
                    sInstance ?: MiTinyDataClientImp().also { sInstance = it }
                }
            }
        }

        private var mContext: Context? = null
        private var mChannel: String? = null
        private var mPushServiceAcceptTinyData: Boolean? = null
        private val mSmoothSender = SmoothSender()
        private val mPendingList = ArrayList<ClientUploadDataItem>()

        inner class SmoothSender {
            private var mFuture: ScheduledFuture<*>? = null
            private val mExecutor = ScheduledThreadPoolExecutor(1)
            val mList = ArrayList<ClientUploadDataItem>()

            private val repeatTask = Runnable {
                if (mList.isEmpty()) {
                    mFuture?.cancel(false)
                    mFuture = null
                } else {
                    doSend()
                }
            }

            private fun awake() {
                if (mFuture == null) {
                    mFuture = mExecutor.scheduleAtFixedRate(repeatTask, MIN_SEND_TIMESPAN, MIN_SEND_TIMESPAN, TimeUnit.MILLISECONDS)
                }
            }

            private fun doSend() {
                val item = mList.removeAt(0)
                val notifications = TinyDataHelper.pack(
                    listOf(item),
                    this@MiTinyDataClientImp.mContext!!.packageName,
                    AppInfoHolder.getInstance(this@MiTinyDataClientImp.mContext!!).appID,
                    MAX_SIZE
                )
                if (notifications != null) {
                    for (notification in notifications) {
                        MyLog.v("MiTinyDataClient Send item by PushServiceClient.sendMessage(XmActionNotification).${item.id}")
                        PushServiceClient.getInstance(this@MiTinyDataClientImp.mContext!!).sendMessage(notification, ActionType.Notification, true, null)
                    }
                }
            }

            fun add(clientUploadDataItem: ClientUploadDataItem) {
                mExecutor.execute {
                    mList.add(clientUploadDataItem)
                    awake()
                }
            }
        }

        private fun addToPendingList(clientUploadDataItem: ClientUploadDataItem) {
            synchronized(mPendingList) {
                if (!mPendingList.contains(clientUploadDataItem)) {
                    mPendingList.add(clientUploadDataItem)
                    if (mPendingList.size > MAX_PENDING_SIZE) {
                        mPendingList.removeAt(0)
                    }
                }
            }
        }

        private fun checkSupportTinyData(context: Context): Boolean {
            if (!PushServiceClient.getInstance(context).shouldUseMIUIPush()) return true
            return try {
                val packageInfo: PackageInfo? = context.packageManager.getPackageInfo(PushConstants.PUSH_SERVICE_PACKAGE_NAME, 4)
                packageInfo != null && packageInfo.longVersionCode >= MIN_TINYDATA_XMSF_VERSION
            } catch (e: Exception) {
                false
            }
        }

        fun alreadyInit(): Boolean = mContext != null

        fun init(context: Context?) {
            if (context == null) {
                MyLog.w("context is null, MiTinyDataClientImp.init() failed.")
                return
            }
            mContext = context
            mPushServiceAcceptTinyData = checkSupportTinyData(context)
            processPendingList(PENDING_REASON_INIT)
        }

        private fun missingAppId(context: Context): Boolean {
            return AppInfoHolder.getInstance(context).appID == null && !checkSupportTinyData(mContext!!)
        }

        fun processPendingList(reason: String) {
            MyLog.v("MiTinyDataClient.processPendingList($reason)")
            val arrayList = ArrayList<ClientUploadDataItem>()
            synchronized(mPendingList) {
                arrayList.addAll(mPendingList)
                mPendingList.clear()
            }
            for (item in arrayList) {
                processUploadRequest(item)
            }
        }

        fun processUploadRequest(clientUploadDataItem: ClientUploadDataItem?): Boolean {
            synchronized(this) {
                if (clientUploadDataItem == null) return false
                if (TinyDataHelper.verify(clientUploadDataItem, true)) return false
                val z2 = TextUtils.isEmpty(clientUploadDataItem.channel) && TextUtils.isEmpty(mChannel)
                val z3 = !alreadyInit()
                val context = mContext
                val z = context == null || missingAppId(context)
                if (!z3 && !z2 && !z) {
                    MyLog.v("MiTinyDataClient Send item immediately.${clientUploadDataItem.id}")
                    if (TextUtils.isEmpty(clientUploadDataItem.id)) {
                        clientUploadDataItem.id = PacketHelper.generatePacketID()
                    }
                    if (TextUtils.isEmpty(clientUploadDataItem.channel)) {
                        clientUploadDataItem.channel = mChannel
                    }
                    if (TextUtils.isEmpty(clientUploadDataItem.sourcePackage)) {
                        clientUploadDataItem.sourcePackage = mContext!!.packageName
                    }
                    if (clientUploadDataItem.timestamp <= 0) {
                        clientUploadDataItem.timestamp = System.currentTimeMillis()
                    }
                    return upload(clientUploadDataItem)
                }
                if (z2) {
                    MyLog.v("MiTinyDataClient Pending ${clientUploadDataItem.name} reason is $PENDING_REASON_CHANNEL")
                } else if (z3) {
                    MyLog.v("MiTinyDataClient Pending ${clientUploadDataItem.name} reason is $PENDING_REASON_INIT")
                } else if (z) {
                    MyLog.v("MiTinyDataClient Pending ${clientUploadDataItem.name} reason is $PENDING_REASON_APPID")
                }
                addToPendingList(clientUploadDataItem)
                return true
            }
        }

        var channel: String?
            get() = mChannel
            set(value) {
                synchronized(this) {
                    if (TextUtils.isEmpty(value)) {
                        MyLog.w("channel is null, MiTinyDataClientImp.setChannel(String) failed.")
                    } else {
                        mChannel = value
                        processPendingList(PENDING_REASON_CHANNEL)
                    }
                }
            }

        private fun upload(clientUploadDataItem: ClientUploadDataItem): Boolean {
            if (TinyDataHelper.verify(clientUploadDataItem, false)) return false
            if (mPushServiceAcceptTinyData != true) {
                mSmoothSender.add(clientUploadDataItem)
                return true
            }
            MyLog.v("MiTinyDataClient Send item by PushServiceClient.sendTinyData(ClientUploadDataItem).${clientUploadDataItem.id}")
            PushServiceClient.getInstance(mContext!!).sendTinyData(clientUploadDataItem)
            return true
        }
    }
}
