package com.xiaomi.mipush.sdk.stat.upload;

import android.content.Context;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.mipush.sdk.stat.PushStatClientManager;
import com.xiaomi.mipush.sdk.stat.db.DataBaseConfig;
import com.xiaomi.mipush.sdk.stat.db.MessageInfoContract;
import com.xiaomi.mipush.sdk.stat.db.MessageUpdateJob;
import com.xiaomi.mipush.sdk.stat.db.base.DbManager;
import com.xiaomi.xmpush.thrift.ClientUploadDataItem;
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/stat/upload/BaseDataSender.class */
public class BaseDataSender implements IDataSender, IDbPathGetter {
    private IDbPathGetter mDbPathGetter;

    public BaseDataSender(IDbPathGetter iDbPathGetter) {
        this.mDbPathGetter = iDbPathGetter;
    }

    public void attachDbPathGetter(IDbPathGetter iDbPathGetter) {
        this.mDbPathGetter = iDbPathGetter;
    }

    @Override // com.xiaomi.mipush.sdk.stat.upload.IDbPathGetter
    public String getPath(Context context, String str) {
        return this.mDbPathGetter.getPath(context, str);
    }

    @Override // com.xiaomi.mipush.sdk.stat.upload.IDbPathGetter
    public List<String> getPathList(Context context) {
        return this.mDbPathGetter.getPathList(context);
    }

    @Override // com.xiaomi.mipush.sdk.stat.upload.IDataSender
    public final void onFailed(Context context, String str, String str2) {
        String absolutePath = new File(getPath(context, str), DataBaseConfig.DATABASE_NAME).getAbsolutePath();
        if (TextUtils.isEmpty(absolutePath)) {
            return;
        }
        UploadDataHelper.updateMessageStatus(context, absolutePath, str2, false);
    }

    @Override // com.xiaomi.mipush.sdk.stat.upload.IDataSender
    public final void onSend(Context context, String str, List<MessageInfoContract.MessageModel> list) {
        if (list == null || list.size() <= 0) {
            return;
        }
        ArrayList<ClientUploadDataItem> arrayList = new ArrayList<>();
        for (MessageInfoContract.MessageModel messageModel : list) {
            if (messageModel != null) {
                ClientUploadDataItem clientUploadDataItem = new ClientUploadDataItem();
                try {
                    XmPushThriftSerializeUtils.convertByteArrayToThriftObject(clientUploadDataItem, messageModel.getMessageItem());
                    if (TextUtils.isEmpty(clientUploadDataItem.getId()) || !String.valueOf(messageModel.getId()).equalsIgnoreCase(UploadDataHelper.getRowId(clientUploadDataItem.getId()))) {
                        clientUploadDataItem.setId(UploadDataHelper.getTinyDataItemId(messageModel.getId()));
                    }
                    arrayList.add(clientUploadDataItem);
                } catch (Exception e) {
                    MyLog.e(e);
                }
            }
        }
        HashMap<String, String> mapSend = UploadDataHelper.send(context, arrayList, PushStatClientManager.getInstance(context).getPackageName(), PushStatClientManager.getInstance(context).getAppId(), list.get(0).getPackageName());
        Set<String> setKeySet = mapSend.keySet();
        ArrayList<DbManager.BaseJob> arrayList2 = new ArrayList<>();
        for (String str2 : setKeySet) {
            if (!TextUtils.isEmpty(str2)) {
                arrayList2.add(MessageUpdateJob.updateMessageId(str, str2, mapSend.get(str2)));
            }
        }
        PushStatClientManager.getInstance(context).exec(arrayList2);
    }

    @Override // com.xiaomi.mipush.sdk.stat.upload.IDataSender
    public final void onSuccess(Context context, String str, String str2) {
        String absolutePath = new File(getPath(context, str), DataBaseConfig.DATABASE_NAME).getAbsolutePath();
        if (TextUtils.isEmpty(absolutePath)) {
            return;
        }
        UploadDataHelper.updateMessageStatus(context, absolutePath, str2, true);
    }
}
