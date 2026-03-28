package com.xiaomi.mipush.sdk.stat.upload;

import android.content.Context;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.file.IOUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.network.Network;
import com.xiaomi.mipush.sdk.Constants;
import com.xiaomi.mipush.sdk.PushServiceClient;
import com.xiaomi.mipush.sdk.stat.PushStatClientManager;
import com.xiaomi.mipush.sdk.stat.db.MessageDeleteJob;
import com.xiaomi.mipush.sdk.stat.db.MessageUpdateJob;
import com.xiaomi.mipush.sdk.stat.db.ScheduleQueryAndUploadJob;
import com.xiaomi.mipush.sdk.stat.db.base.DbManager;
import com.xiaomi.push.service.PacketHelper;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.push.service.TinyDataHelper;
import com.xiaomi.slim.Blob;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.ClientUploadData;
import com.xiaomi.xmpush.thrift.ClientUploadDataItem;
import com.xiaomi.xmpush.thrift.NotificationType;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/stat/upload/UploadDataHelper.class */
public class UploadDataHelper {
    private static final String NEED_ACK = "need_ack";
    public static final String REAL_SOURCE = "real_source";
    private static final String STAT_CATEGORY = "category_push_stat";
    public static final String STAT_CHANNEL = "push_sdk_stat_channel";
    private static final String STAT_NAME = "push_stat";
    private static String mDayPrefix;
    private static SimpleDateFormat mSdf;

    static {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd");
        mSdf = simpleDateFormat;
        mDayPrefix = simpleDateFormat.format(Long.valueOf(System.currentTimeMillis()));
    }

    public static void delete(Context context, String str) {
        DbManager.getInstance(context.getApplicationContext()).exec(MessageDeleteJob.deleteUploadedJob(str));
    }

    public static String getRowId(String str) {
        String strSubstring = null;
        if (!TextUtils.isEmpty(str)) {
            String strTrim = str.trim();
            strSubstring = strTrim.substring(strTrim.lastIndexOf(Constants.ACCEPT_TIME_SEPARATOR_SERVER) + 1);
        }
        return strSubstring;
    }

    public static String getTinyDataItemId(long j) {
        String str = mSdf.format(Long.valueOf(System.currentTimeMillis()));
        if (!TextUtils.equals(mDayPrefix, str)) {
            mDayPrefix = str;
        }
        return str + Constants.ACCEPT_TIME_SEPARATOR_SERVER + j;
    }

    private static ArrayList<XmPushActionNotification> pack(HashMap<String, String> map, List<ClientUploadDataItem> list, String str, String str2, int i) {
        if (list == null) {
            MyLog.e("requests can not be null in UploadDataHelper.transToThriftObj().");
            return null;
        }
        if (list.size() == 0) {
            MyLog.e("requests.length is 0 in UploadDataHelper.transToThriftObj().");
            return null;
        }
        ArrayList<XmPushActionNotification> arrayList = new ArrayList<>();
        ClientUploadData clientUploadData = new ClientUploadData();
        String strGeneratePacketID = PacketHelper.generatePacketID();
        int i2 = 0;
        for (int i3 = 0; i3 < list.size(); i3++) {
            ClientUploadDataItem clientUploadDataItem = list.get(i3);
            if (clientUploadDataItem != null) {
                int length = XmPushThriftSerializeUtils.convertThriftObjectToBytes(clientUploadDataItem).length;
                if (length > i) {
                    MyLog.e("data is too big, ignore upload request item:" + clientUploadDataItem.getId());
                } else {
                    ClientUploadData clientUploadData2 = clientUploadData;
                    String strGeneratePacketID2 = strGeneratePacketID;
                    int i4 = i2;
                    if (i2 + length > i) {
                        XmPushActionNotification xmPushActionNotification = new XmPushActionNotification(strGeneratePacketID, false);
                        xmPushActionNotification.setPackageName(str);
                        xmPushActionNotification.setAppId(str2);
                        xmPushActionNotification.setType(NotificationType.UploadTinyData.value);
                        xmPushActionNotification.setBinaryExtra(IOUtils.gZip(XmPushThriftSerializeUtils.convertThriftObjectToBytes(clientUploadData)));
                        arrayList.add(xmPushActionNotification);
                        clientUploadData2 = new ClientUploadData();
                        strGeneratePacketID2 = PacketHelper.generatePacketID();
                        i4 = 0;
                    }
                    clientUploadData2.addToUploadDataItems(clientUploadDataItem);
                    map.put(clientUploadDataItem.getId(), strGeneratePacketID2);
                    i2 = i4 + length;
                    strGeneratePacketID = strGeneratePacketID2;
                    clientUploadData = clientUploadData2;
                }
            }
        }
        if (clientUploadData.getUploadDataItemsSize() != 0) {
            XmPushActionNotification xmPushActionNotification2 = new XmPushActionNotification(strGeneratePacketID, true);
            xmPushActionNotification2.setPackageName(str);
            xmPushActionNotification2.setAppId(str2);
            xmPushActionNotification2.setType(NotificationType.UploadTinyData.value);
            xmPushActionNotification2.setBinaryExtra(IOUtils.gZip(XmPushThriftSerializeUtils.convertThriftObjectToBytes(clientUploadData)));
            arrayList.add(xmPushActionNotification2);
        }
        return arrayList;
    }

    public static HashMap<String, String> send(Context context, List<ClientUploadDataItem> list, String str, String str2, String str3) {
        HashMap<String, String> map = new HashMap<>();
        ArrayList<XmPushActionNotification> arrayListPack = pack(map, list, str, str2, Blob.MAX_BLOB_SIZE);
        if (arrayListPack != null) {
            for (XmPushActionNotification xmPushActionNotification : arrayListPack) {
                xmPushActionNotification.putToExtra(TinyDataHelper.KEY_UPLOAD_WAY, "longXMPushService");
                xmPushActionNotification.putToExtra(NEED_ACK, String.valueOf(true));
                xmPushActionNotification.putToExtra(REAL_SOURCE, str3);
                PushMetaInfo pushMetaInfo = new PushMetaInfo();
                pushMetaInfo.setId("-1");
                pushMetaInfo.putToInternal(PushConstants.EXTRA_TRAFFIC_SOURCE_PKG, str3);
                PushServiceClient.getInstance(context).sendMessage(xmPushActionNotification, ActionType.Notification, true, pushMetaInfo, true);
            }
        }
        return map;
    }

    public static void updateMessageStatus(Context context, String str, String str2, boolean z) {
        MyLog.v("start update item status");
        DbManager.getInstance(context.getApplicationContext()).exec(MessageUpdateJob.updateItemStatusAfterAck(str, str2, z));
    }

    public static void upload(Context context, String str) {
        if (Network.hasNetwork(context)) {
            MyLog.v("start upload  noUpload job");
            PushStatClientManager.getInstance(context.getApplicationContext()).exec(ScheduleQueryAndUploadJob.getScheduleJob(str));
        }
    }

    public static ClientUploadDataItem wrapperData(Context context, String str) {
        if (TextUtils.isEmpty(str)) {
            return null;
        }
        ClientUploadDataItem clientUploadDataItem = new ClientUploadDataItem();
        clientUploadDataItem.setCategory(STAT_CATEGORY);
        clientUploadDataItem.setChannel(STAT_CHANNEL);
        clientUploadDataItem.setCounter(1L);
        clientUploadDataItem.setData(str);
        clientUploadDataItem.setFromSdk(true);
        clientUploadDataItem.setTimestamp(System.currentTimeMillis());
        clientUploadDataItem.setPkgName(PushStatClientManager.getInstance(context).getPackageName());
        clientUploadDataItem.setSourcePackage(PushConstants.PUSH_SERVICE_PACKAGE_NAME);
        clientUploadDataItem.setId("");
        clientUploadDataItem.setName(STAT_NAME);
        return clientUploadDataItem;
    }
}
