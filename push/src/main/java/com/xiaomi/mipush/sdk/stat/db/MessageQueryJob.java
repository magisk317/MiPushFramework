package com.xiaomi.mipush.sdk.stat.db;

import android.content.Context;
import android.database.Cursor;
import com.xiaomi.mipush.sdk.stat.PushStatClientManager;
import com.xiaomi.mipush.sdk.stat.db.MessageInfoContract;
import com.xiaomi.mipush.sdk.stat.db.base.DbManager;
import com.xiaomi.push.mpcd.Constants;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/stat/db/MessageQueryJob.class */
public class MessageQueryJob extends DbManager.BaseQueryJob<MessageInfoContract.MessageModel> {
    private int mCount;
    private String mDescription;

    public MessageQueryJob(String str, List<String> list, String str2, String[] strArr, String str3, String str4, String str5, int i, int i2, String str6) {
        super(str, list, str2, strArr, str3, str4, str5, i);
        this.mDescription = "MessageQueryJob";
        this.mDescription = str6;
        this.mCount = i2;
    }

    public static MessageQueryJob newInstance(String str, int i, int i2) {
        ArrayList<String> arrayList = new ArrayList<>();
        arrayList.add(MessageInfoContract.MessageEntry.COLUMN_NAME_ID);
        arrayList.add("messageId");
        arrayList.add(MessageInfoContract.MessageEntry.COLUMN_NAME_MESSAGE_ITEM_ID);
        arrayList.add(MessageInfoContract.MessageEntry.COLUMN_NAME_MESSAGE_ITEM);
        arrayList.add(MessageInfoContract.MessageEntry.COLUMN_NAME_CREATE_TIMESTAMP);
        arrayList.add(MessageInfoContract.MessageEntry.COLUMN_NAME_UPLOAD_TIMESTAMP);
        arrayList.add(MessageInfoContract.MessageEntry.COLUMN_NAME_APP_ID);
        arrayList.add(MessageInfoContract.MessageEntry.COLUMN_NAME_APP_PACKAGE_NAME);
        arrayList.add("status");
        return new MessageQueryJob(str, arrayList, "status = ? or (status = ? and uploadTimestamp <= ?" + Constants.SEPARATOR_RIGHT_PARENTESIS, new String[]{String.valueOf(0), String.valueOf(1), String.valueOf(System.currentTimeMillis() - MessageInfoContract.TIMEOUT)}, null, null, "createTimeStamp asc", i, i2, "a job build to query db");
    }

    @Override // com.xiaomi.mipush.sdk.stat.db.base.DbManager.BaseJob
    public String description() {
        return this.mDescription + "  limit = " + getLimit();
    }

    @Override // com.xiaomi.mipush.sdk.stat.db.base.DbManager.BaseJob, com.xiaomi.mipush.sdk.stat.db.base.ISerialSchedule
    public void input(Context context, Object obj) {
        if (obj == null || !(obj instanceof int[])) {
            return;
        }
        int[] iArr = (int[]) obj;
        int i = iArr[0];
        int i2 = iArr[1];
        if (i <= 0 || i2 <= 0) {
            return;
        }
        setLimit(i);
        this.mCount = i2;
        super.input(context, obj);
    }

    @Override // com.xiaomi.mipush.sdk.stat.db.base.DbManager.BaseQueryJob
    public void notifyResult(Context context, List<MessageInfoContract.MessageModel> list) {
        PushStatClientManager.getInstance(context).send(getDataPath(), list);
    }

    @Override // com.xiaomi.mipush.sdk.stat.db.base.DbManager.BaseJob, com.xiaomi.mipush.sdk.stat.db.base.ISerialSchedule
    public Object output() {
        int limit = getLimit();
        int i = this.mCount - limit;
        return (limit <= 0 || i <= 0) ? super.output() : new int[]{limit, i};
    }

    /* JADX WARN: Can't rename method to resolve collision */
    @Override // com.xiaomi.mipush.sdk.stat.db.base.DbManager.BaseQueryJob
    public MessageInfoContract.MessageModel processOneData(Context context, Cursor cursor) {
        if (cursor != null) {
            return MessageInfoContract.MessageModel.build(cursor);
        }
        return null;
    }
}
