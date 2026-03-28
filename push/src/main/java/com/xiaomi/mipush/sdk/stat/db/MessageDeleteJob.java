package com.xiaomi.mipush.sdk.stat.db;

import com.xiaomi.mipush.sdk.stat.db.base.DbManager;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/stat/db/MessageDeleteJob.class */
public class MessageDeleteJob extends DbManager.DeleteJob {
    protected String mDescription;

    public MessageDeleteJob(String str, String str2, String[] strArr, String str3) {
        super(str, str2, strArr);
        this.mDescription = "MessageDeleteJob";
        this.mDescription = str3;
    }

    public static MessageDeleteJob deleteUploadedJob(String str) {
        return new MessageDeleteJob(str, "status = ?", new String[]{String.valueOf(2)}, "a job build to delete uploaded job");
    }

    @Override // com.xiaomi.mipush.sdk.stat.db.base.DbManager.DeleteJob, com.xiaomi.mipush.sdk.stat.db.base.DbManager.BaseJob
    public String description() {
        return this.mDescription;
    }
}
