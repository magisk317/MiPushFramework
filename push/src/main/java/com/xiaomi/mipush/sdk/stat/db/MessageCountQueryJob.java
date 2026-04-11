package com.xiaomi.mipush.sdk.stat.db;

import android.content.Context;
import android.database.Cursor;
import com.xiaomi.mipush.sdk.stat.db.base.DbManager;
import com.xiaomi.push.mpcd.Constants;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/stat/db/MessageCountQueryJob.class */
public class MessageCountQueryJob extends DbManager.BaseQueryJob<Long> {
    private String mDescription;
    private long mResult;

    public MessageCountQueryJob(String str, List<String> list, String str2, String[] strArr, String str3, String str4, String str5, int i, String str6) {
        super(str, list, str2, strArr, str3, str4, str5, i);
        this.mResult = 0L;
        this.mDescription = str6;
    }

    public static MessageCountQueryJob getMessageCountJob(String str) {
        ArrayList<String> arrayList = new ArrayList<>();
        arrayList.add("count(*)");
        return new MessageCountQueryJob(str, arrayList, null, null, null, null, null, 0, "job to get count of all message");
    }

    public static MessageCountQueryJob getNoUploadMessageCountJob(String str) {
        ArrayList<String> arrayList = new ArrayList<>();
        arrayList.add("count(*)");
        return new MessageCountQueryJob(str, arrayList, "status = ? or (status = ? and uploadTimestamp <= ?" + Constants.SEPARATOR_RIGHT_PARENTESIS, new String[]{String.valueOf(0), String.valueOf(1), String.valueOf(System.currentTimeMillis() - MessageInfoContract.TIMEOUT)}, null, null, null, 0, "job to get count of noUpload message");
    }

    @Override // com.xiaomi.mipush.sdk.stat.db.base.DbManager.BaseJob
    public String description() {
        return this.mDescription;
    }

    @Override // com.xiaomi.mipush.sdk.stat.db.base.DbManager.BaseQueryJob
    public void notifyResult(Context context, List<Long> list) {
        if (context == null || list == null || list.size() <= 0) {
            return;
        }
        this.mResult = list.get(0).longValue();
    }

    @Override // com.xiaomi.mipush.sdk.stat.db.base.DbManager.BaseJob, com.xiaomi.mipush.sdk.stat.db.base.ISerialSchedule
    public Object output() {
        return Long.valueOf(this.mResult);
    }

    /* JADX WARN: Can't rename method to resolve collision */
    @Override // com.xiaomi.mipush.sdk.stat.db.base.DbManager.BaseQueryJob
    public Long processOneData(Context context, Cursor cursor) {
        return Long.valueOf(cursor.getLong(0));
    }
}
