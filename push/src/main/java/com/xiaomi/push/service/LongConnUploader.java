package com.xiaomi.push.service;

import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.XMPushService;
import com.xiaomi.slim.Blob;
import com.xiaomi.tinyData.TinyDataUploader;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.ClientUploadDataItem;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/LongConnUploader.class */
public class LongConnUploader implements TinyDataUploader {
    private final XMPushService mPushService;

    public LongConnUploader(XMPushService xMPushService) {
        this.mPushService = xMPushService;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public String getAppId(String str) {
        return PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(str) ? MIPushAccountUtils.MIPUSH_MIUI_APPID : this.mPushService.getSharedPreferences(PushServiceConstants.PREF_KEY_REGISTERED_PKGS, 0).getString(str, null);
    }

    @Override // com.xiaomi.tinyData.TinyDataUploader
    public boolean checkCanUpload(ClientUploadDataItem clientUploadDataItem, String str) {
        return getAppId(this.mPushService.getPackageName()) != null;
    }

    @Override // com.xiaomi.tinyData.TinyDataUploader
    public void upload(final List<ClientUploadDataItem> list, final String str, final String str2) {
        this.mPushService.executeJob(new XMPushService.Job(4) { // from class: com.xiaomi.push.service.LongConnUploader.1
            @Override // com.xiaomi.push.service.XMPushService.Job
            public String getDesc() {
                return "Send tiny data.";
            }

            @Override // com.xiaomi.push.service.XMPushService.Job
            public void process() {
                String appId = LongConnUploader.this.getAppId(str);
                ArrayList<XmPushActionNotification> arrayListPack = TinyDataHelper.pack(list, str, appId, Blob.MAX_BLOB_SIZE);
                if (arrayListPack == null) {
                    MyLog.e("TinyData LongConnUploader.upload Get a null XmPushActionNotification list when TinyDataHelper.pack() in XMPushService.");
                    return;
                }
                for (XmPushActionNotification xmPushActionNotification : arrayListPack) {
                    xmPushActionNotification.putToExtra(TinyDataHelper.KEY_UPLOAD_WAY, "longXMPushService");
                    XmPushActionContainer xmPushActionContainerGenerateRequestContainer = MIPushHelper.generateRequestContainer(str, appId, xmPushActionNotification, ActionType.Notification);
                    if (!TextUtils.isEmpty(str2) && !TextUtils.equals(str, str2)) {
                        if (xmPushActionContainerGenerateRequestContainer.getMetaInfo() == null) {
                            PushMetaInfo pushMetaInfo = new PushMetaInfo();
                            pushMetaInfo.setId("-1");
                            xmPushActionContainerGenerateRequestContainer.setMetaInfo(pushMetaInfo);
                        }
                        xmPushActionContainerGenerateRequestContainer.getMetaInfo().putToInternal(PushConstants.EXTRA_TRAFFIC_SOURCE_PKG, str2);
                    }
                    LongConnUploader.this.mPushService.sendMessage(str, XmPushThriftSerializeUtils.convertThriftObjectToBytes(xmPushActionContainerGenerateRequestContainer), true);
                }
            }
        });
    }
}
