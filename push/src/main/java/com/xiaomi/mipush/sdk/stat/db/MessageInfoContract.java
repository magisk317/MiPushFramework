package com.xiaomi.mipush.sdk.stat.db;

import android.database.Cursor;
import android.provider.BaseColumns;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/stat/db/MessageInfoContract.class */
public final class MessageInfoContract {
    public static final int LIMIT = 10;
    public static final int NO_UPLOAD = 0;
    public static final long TIMEOUT = 30000;
    public static final int UPLOADED = 2;
    public static final int UPLOADING = 1;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/stat/db/MessageInfoContract$MessageEntry.class */
    public static abstract class MessageEntry implements BaseColumns {
        public static final String COLUMN_NAME_APP_ID = "appId";
        public static final String COLUMN_NAME_APP_PACKAGE_NAME = "packageName";
        public static final String COLUMN_NAME_CREATE_TIMESTAMP = "createTimeStamp";
        public static final String COLUMN_NAME_ID = "rowDataId";
        public static final String COLUMN_NAME_MESSAGE_ID = "messageId";
        public static final String COLUMN_NAME_MESSAGE_ITEM = "messageItem";
        public static final String COLUMN_NAME_MESSAGE_ITEM_ID = "messageItemId";
        public static final String COLUMN_NAME_STATUS = "status";
        public static final String COLUMN_NAME_UPLOAD_TIMESTAMP = "uploadTimestamp";
        public static final String TABLE_NAME = "messageTable";
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/stat/db/MessageInfoContract$MessageModel.class */
    public static class MessageModel extends MessageEntry {
        private String mAppId;
        private long mCreateTimeStamp;
        private long mId;
        private String mMessageId;
        private byte[] mMessageItem;
        private String mMessageItemId;
        private String mPackageName;
        private int mStatus;
        private long mUploadTimeStamp;

        public static MessageModel build(Cursor cursor) {
            MessageModel messageModel = new MessageModel();
            messageModel.setId(cursor.getLong(cursor.getColumnIndex(MessageEntry.COLUMN_NAME_ID)));
            messageModel.setAppId(cursor.getString(cursor.getColumnIndex(MessageEntry.COLUMN_NAME_APP_ID)));
            messageModel.setMessageItem(cursor.getBlob(cursor.getColumnIndex(MessageEntry.COLUMN_NAME_MESSAGE_ITEM)));
            messageModel.setMessageItemId(cursor.getString(cursor.getColumnIndex(MessageEntry.COLUMN_NAME_MESSAGE_ITEM_ID)));
            messageModel.setMessageId(cursor.getString(cursor.getColumnIndex("messageId")));
            messageModel.setPackageName(cursor.getString(cursor.getColumnIndex(MessageEntry.COLUMN_NAME_APP_PACKAGE_NAME)));
            messageModel.setCreateTimeStamp(cursor.getLong(cursor.getColumnIndex(MessageEntry.COLUMN_NAME_CREATE_TIMESTAMP)));
            messageModel.setUploadTimeStamp(cursor.getLong(cursor.getColumnIndex(MessageEntry.COLUMN_NAME_UPLOAD_TIMESTAMP)));
            return messageModel;
        }

        public String getAppId() {
            return this.mAppId;
        }

        public long getCreateTimeStamp() {
            return this.mCreateTimeStamp;
        }

        public long getId() {
            return this.mId;
        }

        public String getMessageId() {
            return this.mMessageId;
        }

        public byte[] getMessageItem() {
            return this.mMessageItem;
        }

        public String getMessageItemId() {
            return this.mMessageItemId;
        }

        public String getPackageName() {
            return this.mPackageName;
        }

        public int getStatus() {
            return this.mStatus;
        }

        public long getUploadTimeStamp() {
            return this.mUploadTimeStamp;
        }

        public void setAppId(String str) {
            this.mAppId = str;
        }

        public void setCreateTimeStamp(long j) {
            this.mCreateTimeStamp = j;
        }

        public void setId(long j) {
            this.mId = j;
        }

        public void setMessageId(String str) {
            this.mMessageId = str;
        }

        public void setMessageItem(byte[] bArr) {
            this.mMessageItem = bArr;
        }

        public void setMessageItemId(String str) {
            this.mMessageItemId = str;
        }

        public void setPackageName(String str) {
            this.mPackageName = str;
        }

        public void setStatus(int i) {
            this.mStatus = i;
        }

        public void setUploadTimeStamp(long j) {
            this.mUploadTimeStamp = j;
        }
    }
}
