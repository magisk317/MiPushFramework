package com.xiaomi.tinyData;

import android.content.Context;
import com.xiaomi.channel.commonutils.network.Network;
import com.xiaomi.xmpush.thrift.ClientUploadDataItem;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/tinyData/HttpUploader.class */
public class HttpUploader implements TinyDataUploader {
    private final Context mContext;

    public HttpUploader(Context context) {
        this.mContext = context;
    }

    @Override // com.xiaomi.tinyData.TinyDataUploader
    public boolean checkCanUpload(ClientUploadDataItem clientUploadDataItem, String str) {
        return Network.hasNetwork(this.mContext);
    }

    @Override // com.xiaomi.tinyData.TinyDataUploader
    public void upload(List<ClientUploadDataItem> list, String str, String str2) {
    }
}
