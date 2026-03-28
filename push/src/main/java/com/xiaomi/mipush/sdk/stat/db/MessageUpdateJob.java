package com.xiaomi.mipush.sdk.stat.db;

import android.content.ContentValues;
import com.xiaomi.mipush.sdk.stat.db.MessageInfoContract;
import com.xiaomi.mipush.sdk.stat.db.base.DbManager;
import com.xiaomi.mipush.sdk.stat.upload.UploadDataHelper;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/stat/db/MessageUpdateJob.class */
public class MessageUpdateJob extends DbManager.UpdateJob {
    private String mDescription;

    public MessageUpdateJob(String str, String str2, String[] strArr, ContentValues contentValues, String str3) {
        super(str, str2, strArr, contentValues);
        this.mDescription = "MessageUpdateJob";
        this.mDescription = str3;
    }

    public static MessageUpdateJob updateItemStatusAfterAck(String str, String str2, boolean z) {
        int i = 0;
        ContentValues contentValues = new ContentValues();
        if (z) {
            i = 2;
        }
        contentValues.put("status", Integer.valueOf(i));
        return new MessageUpdateJob(str, "messageId = ?", new String[]{str2}, contentValues, "a job build to update message status after receive ack");
    }

    public static MessageUpdateJob updateMessageId(String str, String str2, String str3) {
        StringBuilder sb = new StringBuilder();
        String rowId = UploadDataHelper.getRowId(str2);
        sb.append("rowDataId = ?");
        ContentValues contentValues = new ContentValues();
        contentValues.put(MessageInfoContract.MessageEntry.COLUMN_NAME_UPLOAD_TIMESTAMP, Long.valueOf(System.currentTimeMillis()));
        contentValues.put("status", (Integer) 1);
        contentValues.put("messageId", str3);
        contentValues.put(MessageInfoContract.MessageEntry.COLUMN_NAME_MESSAGE_ITEM_ID, str2);
        return new MessageUpdateJob(str, sb.toString(), new String[]{rowId}, contentValues, "a job build to update message upload time and messageId");
    }

    @Override // com.xiaomi.mipush.sdk.stat.db.base.DbManager.UpdateJob, com.xiaomi.mipush.sdk.stat.db.base.DbManager.BaseJob
    public String description() {
        return this.mDescription + "";
    }
}
