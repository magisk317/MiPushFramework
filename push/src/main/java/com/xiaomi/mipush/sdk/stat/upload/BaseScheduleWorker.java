package com.xiaomi.mipush.sdk.stat.upload;

import android.content.Context;
import android.text.TextUtils;
import com.xiaomi.mipush.sdk.stat.db.DataBaseConfig;
import java.io.File;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/stat/upload/BaseScheduleWorker.class */
public class BaseScheduleWorker implements IScheduleWorker, IDbPathGetter {
    private IDbPathGetter mDbPathGetter;

    public BaseScheduleWorker(IDbPathGetter iDbPathGetter) {
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

    @Override // com.xiaomi.mipush.sdk.stat.upload.IScheduleWorker
    public final void onDelete(Context context) {
        List<String> pathList = getPathList(context);
        if (pathList == null || pathList.size() <= 0) {
            return;
        }
        for (String str : pathList) {
            if (!TextUtils.isEmpty(str)) {
                UploadDataHelper.delete(context, new File(str, DataBaseConfig.DATABASE_NAME).getAbsolutePath());
            }
        }
    }

    @Override // com.xiaomi.mipush.sdk.stat.upload.IScheduleWorker
    public final void onUpload(Context context) {
        List<String> pathList = getPathList(context);
        if (pathList == null || pathList.size() <= 0) {
            return;
        }
        for (String str : pathList) {
            if (!TextUtils.isEmpty(str)) {
                UploadDataHelper.upload(context, new File(str, DataBaseConfig.DATABASE_NAME).getAbsolutePath());
            }
        }
    }
}
