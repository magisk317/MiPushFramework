package com.xiaomi.mipush.sdk.stat.upload;

import android.content.Context;
import com.xiaomi.mipush.sdk.stat.db.MessageInfoContract;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/stat/upload/IDataSender.class */
public interface IDataSender {
    void onFailed(Context context, String str, String str2);

    void onSend(Context context, String str, List<MessageInfoContract.MessageModel> list);

    void onSuccess(Context context, String str, String str2);
}
