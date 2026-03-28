package com.xiaomi.mipush.sdk.stat.upload;

import android.content.Context;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/stat/upload/IDbPathGetter.class */
public interface IDbPathGetter {
    String getPath(Context context, String str);

    List<String> getPathList(Context context);
}
