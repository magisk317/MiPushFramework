package com.xiaomi.push.service

import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.slim.Blob
import com.xiaomi.tinyData.TinyDataUploader
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.ClientUploadDataItem
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils

class LongConnUploader(
    private val pushService: XMPushService,
) : TinyDataUploader {
    private fun getAppId(packageName: String): String? {
        return if (PushConstants.PUSH_SERVICE_PACKAGE_NAME == packageName) {
            MIPushAccountUtils.MIPUSH_MIUI_APPID
        } else {
            pushService.getSharedPreferences(PushServiceConstants.PREF_KEY_REGISTERED_PKGS, 0)
                .getString(packageName, null)
        }
    }

    override fun checkCanUpload(clientUploadDataItem: ClientUploadDataItem, str: String): Boolean {
        return getAppId(pushService.packageName) != null
    }

    override fun upload(list: MutableList<ClientUploadDataItem>, str: String, str2: String) {
        pushService.executeJob(
            object : XMPushService.Job(XMPushServiceJob.TYPE_SEND_MSG) {
                override fun getDesc(): String = "Send tiny data."
                override fun process() {
                    val appId = getAppId(str)
                    if (appId == null) {
                        MyLog.e("TinyData LongConnUploader.upload missing appId for $str")
                        return
                    }
                    val notifications = TinyDataHelper.pack(list, str, appId, Blob.MAX_BLOB_SIZE)
                    if (notifications == null) {
                        MyLog.e("TinyData LongConnUploader.upload Get a null XmPushActionNotification list when TinyDataHelper.pack() in XMPushService.")
                        return
                    }
                    for (notification in notifications) {
                        notification.putToExtra(TinyDataHelper.KEY_UPLOAD_WAY, "longXMPushService")
                        val requestContainer = MIPushHelper.generateRequestContainer(
                            str,
                            appId,
                            notification,
                            ActionType.Notification,
                        )
                        if (!TextUtils.isEmpty(str2) && !TextUtils.equals(str, str2)) {
                            if (requestContainer.metaInfo == null) {
                                requestContainer.metaInfo = PushMetaInfo().apply { id = "-1" }
                            }
                            requestContainer.metaInfo.putToInternal(PushConstants.EXTRA_TRAFFIC_SOURCE_PKG, str2)
                        }
                        pushService.sendMessage(
                            str,
                            XmPushThriftSerializeUtils.convertThriftObjectToBytes(requestContainer),
                            true,
                        )
                    }
                }
            },
        )
    }
}
