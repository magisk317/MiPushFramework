package com.xiaomi.mipush.sdk.stat.upload

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.file.IOUtils
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.mipush.sdk.Constants
import com.xiaomi.mipush.sdk.PushServiceClient
import com.xiaomi.mipush.sdk.stat.PushStatClientManager
import com.xiaomi.mipush.sdk.stat.db.DataBaseConfig
import com.xiaomi.mipush.sdk.stat.db.MessageDeleteJob
import com.xiaomi.mipush.sdk.stat.db.MessageUpdateJob
import com.xiaomi.mipush.sdk.stat.db.MyLog
import com.xiaomi.mipush.sdk.stat.db.ScheduleQueryAndUploadJob
import com.xiaomi.mipush.sdk.stat.db.base.DbManager
import com.xiaomi.push.service.PacketHelper
import com.xiaomi.push.service.TinyDataHelper
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.ClientUploadData
import com.xiaomi.xmpush.thrift.ClientUploadDataItem
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.HashMap
import java.util.Locale

object UploadDataHelper {
    private const val NEED_ACK = "need_ack"
    const val REAL_SOURCE = "real_source"
    private const val STAT_CATEGORY = "category_push_stat"
    const val STAT_CHANNEL = "push_sdk_stat_channel"
    private const val STAT_NAME = "push_stat"
    private var mDayPrefix: String
    private var mSdf: SimpleDateFormat

    init {
        mSdf = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        mDayPrefix = mSdf.format(System.currentTimeMillis())
    }

    fun delete(context: Context, str: String) {
        DbManager.getInstance(context.applicationContext)
            .exec(MessageDeleteJob.deleteUploadedJob(str))
    }

    fun getRowId(str: String?): String? {
        if (TextUtils.isEmpty(str)) return null
        val strTrim = str!!.trim()
        return strTrim.substring(strTrim.lastIndexOf(Constants.ACCEPT_TIME_SEPARATOR_SERVER) + 1)
    }

    fun getTinyDataItemId(j: Long): String {
        val str = mSdf.format(System.currentTimeMillis())
        if (!TextUtils.equals(mDayPrefix, str)) {
            mDayPrefix = str
        }
        return "$str${Constants.ACCEPT_TIME_SEPARATOR_SERVER}$j"
    }

    private fun pack(
        map: HashMap<String, String>,
        list: List<ClientUploadDataItem>,
        str: String,
        str2: String,
        i: Int,
    ): ArrayList<XmPushActionNotification>? {
        if (list.isEmpty()) {
            MyLog.e("requests can not be null in UploadDataHelper.transToThriftObj().")
            return null
        }
        if (list.size == 0) {
            MyLog.e("requests.length is 0 in UploadDataHelper.transToThriftObj().")
            return null
        }
        val arrayList = ArrayList<XmPushActionNotification>()
        var clientUploadData = ClientUploadData()
        var strGeneratePacketID = PacketHelper.generatePacketID()
        var i2 = 0
        for (i3 in list.indices) {
            val clientUploadDataItem = list[i3]
            if (clientUploadDataItem != null) {
                val length =
                    XmPushThriftSerializeUtils.convertThriftObjectToBytes(clientUploadDataItem).size
                if (length > i) {
                    MyLog.e(
                        "data is too big, ignore upload request item:${clientUploadDataItem.id}",
                    )
                } else {
                    var clientUploadData2 = clientUploadData
                    var strGeneratePacketID2 = strGeneratePacketID
                    var i4 = i2
                    if (i2 + length > i) {
                        val xmPushActionNotification =
                            XmPushActionNotification(strGeneratePacketID, false)
                        xmPushActionNotification.packageName = str
                        xmPushActionNotification.appId = str2
                        xmPushActionNotification.type = NotificationType.UploadTinyData.value
                        xmPushActionNotification.setBinaryExtra(IOUtils.gZip(
                            XmPushThriftSerializeUtils.convertThriftObjectToBytes(
                                clientUploadData,
                            ),
                        ))
                        arrayList.add(xmPushActionNotification)
                        clientUploadData2 = ClientUploadData()
                        strGeneratePacketID2 = PacketHelper.generatePacketID()
                        i4 = 0
                    }
                    clientUploadData2.addToUploadDataItems(clientUploadDataItem)
                    map[clientUploadDataItem.id] = strGeneratePacketID2
                    i2 = i4 + length
                    strGeneratePacketID = strGeneratePacketID2
                    clientUploadData = clientUploadData2
                }
            }
        }
        if (clientUploadData.uploadDataItemsSize != 0) {
            val xmPushActionNotification2 = XmPushActionNotification(strGeneratePacketID, true)
            xmPushActionNotification2.packageName = str
            xmPushActionNotification2.appId = str2
            xmPushActionNotification2.type = NotificationType.UploadTinyData.value
            xmPushActionNotification2.setBinaryExtra(IOUtils.gZip(
                XmPushThriftSerializeUtils.convertThriftObjectToBytes(clientUploadData),
            ))
            arrayList.add(xmPushActionNotification2)
        }
        return arrayList
    }

    fun send(
        context: Context,
        list: List<ClientUploadDataItem>,
        str: String,
        str2: String,
        str3: String,
    ): HashMap<String, String> {
        val map = HashMap<String, String>()
        val arrayListPack = pack(map, list, str, str2, 32768)
        if (arrayListPack != null) {
            for (xmPushActionNotification in arrayListPack) {
                xmPushActionNotification.putToExtra(TinyDataHelper.KEY_UPLOAD_WAY, "longXMPushService")
                xmPushActionNotification.putToExtra(NEED_ACK, true.toString())
                xmPushActionNotification.putToExtra(REAL_SOURCE, str3)
                val pushMetaInfo = PushMetaInfo()
                pushMetaInfo.id = "-1"
                pushMetaInfo.putToInternal("ext_traffic_source_pkg", str3)
                PushServiceClient.getInstance(context).sendMessage(
                    xmPushActionNotification,
                    ActionType.Notification,
                    true,
                    pushMetaInfo,
                    true,
                )
            }
        }
        return map
    }

    fun updateMessageStatus(context: Context, str: String, str2: String, z: Boolean) {
        MyLog.v("start update item status")
        DbManager.getInstance(context.applicationContext)
            .exec(MessageUpdateJob.updateItemStatusAfterAck(str, str2, z))
    }

    fun upload(context: Context, str: String) {
        if (Network.hasNetwork(context)) {
            MyLog.v("start upload  noUpload job")
            PushStatClientManager.getInstance(context.applicationContext)
                .exec(ScheduleQueryAndUploadJob.getScheduleJob(str))
        }
    }

    fun wrapperData(context: Context, str: String?): ClientUploadDataItem? {
        if (TextUtils.isEmpty(str)) return null
        return ClientUploadDataItem().apply {
            category = STAT_CATEGORY
            channel = STAT_CHANNEL
            counter = 1L
            data = str
            isFromSdk = true
            timestamp = System.currentTimeMillis()
            pkgName = PushStatClientManager.getInstance(context).packageName ?: ""
            sourcePackage = "com.xiaomi.xmsf"
            id = ""
            name = STAT_NAME
        }
    }
}
