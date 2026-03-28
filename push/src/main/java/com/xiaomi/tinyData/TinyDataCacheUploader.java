package com.xiaomi.tinyData;

import android.content.Context;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.TinyDataHelper;
import com.xiaomi.xmpush.thrift.ClientUploadDataItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/tinyData/TinyDataCacheUploader.class */
public class TinyDataCacheUploader {
    private static HashMap<String, ArrayList<ClientUploadDataItem>> prepareTinyDataItems(Context context, List<ClientUploadDataItem> list) {
        if (list == null || list.size() == 0) {
            return null;
        }
        HashMap<String, ArrayList<ClientUploadDataItem>> map = new HashMap<>();
        for (ClientUploadDataItem clientUploadDataItem : list) {
            verifyTinyDataUploadItemValue(context, clientUploadDataItem);
            ArrayList<ClientUploadDataItem> arrayList = map.get(clientUploadDataItem.getSourcePackage());
            ArrayList<ClientUploadDataItem> arrayList2 = arrayList;
            if (arrayList == null) {
                arrayList2 = new ArrayList<>();
                map.put(clientUploadDataItem.getSourcePackage(), arrayList2);
            }
            arrayList2.add(clientUploadDataItem);
        }
        return map;
    }

    private static void upload(Context context, TinyDataUploader tinyDataUploader, HashMap<String, ArrayList<ClientUploadDataItem>> map) {
        for (Map.Entry<String, ArrayList<ClientUploadDataItem>> entry : map.entrySet()) {
            try {
                ArrayList<ClientUploadDataItem> value = entry.getValue();
                if (value != null && value.size() != 0) {
                    tinyDataUploader.upload(value, value.get(0).getPkgName(), entry.getKey());
                }
            } catch (Exception e) {
            }
        }
    }

    public static void uploadTinyData(Context context, TinyDataUploader tinyDataUploader, List<ClientUploadDataItem> list) {
        HashMap<String, ArrayList<ClientUploadDataItem>> mapPrepareTinyDataItems = prepareTinyDataItems(context, list);
        if (mapPrepareTinyDataItems != null && mapPrepareTinyDataItems.size() != 0) {
            upload(context, tinyDataUploader, mapPrepareTinyDataItems);
            return;
        }
        MyLog.w("TinyData TinyDataCacheUploader.uploadTinyData itemsUploading == null || itemsUploading.size() == 0  ts:" + System.currentTimeMillis());
    }

    private static void verifyTinyDataUploadItemValue(Context context, ClientUploadDataItem clientUploadDataItem) {
        if (clientUploadDataItem.fromSdk) {
            clientUploadDataItem.setChannel("push_sdk_channel");
        }
        if (TextUtils.isEmpty(clientUploadDataItem.getId())) {
            clientUploadDataItem.setId(TinyDataHelper.nextTinyDataItemId());
        }
        clientUploadDataItem.setTimestamp(System.currentTimeMillis());
        if (TextUtils.isEmpty(clientUploadDataItem.getPkgName())) {
            clientUploadDataItem.setSourcePackage(context.getPackageName());
        }
        if (TextUtils.isEmpty(clientUploadDataItem.getSourcePackage())) {
            clientUploadDataItem.setSourcePackage(clientUploadDataItem.getPkgName());
        }
    }
}
