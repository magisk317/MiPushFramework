package com.xiaomi.mipush.sdk.stat.db;

import android.content.Context;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.mipush.sdk.stat.PushStatClientManager;
import com.xiaomi.mipush.sdk.stat.db.base.DbManager;
import com.xiaomi.mipush.sdk.stat.util.FileUtil;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/stat/db/HistoryDataDeleteJob.class */
public class HistoryDataDeleteJob extends MessageDeleteJob {
    public HistoryDataDeleteJob(String str, String str2, String[] strArr, String str3) {
        super(str, str2, strArr, str3);
    }

    public static HistoryDataDeleteJob deleteHistoryJob(Context context, String str, int i) {
        MyLog.i("delete  messages when db size is too bigger");
        String tableName = DbManager.getInstance(context).getTableName(str);
        if (TextUtils.isEmpty(tableName)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("rowDataId in (select ");
        sb.append("rowDataId from " + tableName);
        sb.append(" order by createTimeStamp asc");
        sb.append(" limit ?)");
        return new HistoryDataDeleteJob(str, sb.toString(), new String[]{String.valueOf(i)}, "a job build to delete history message");
    }

    private void setLimit(long j) {
        if (this.mWhereValues == null || this.mWhereValues.length <= 0) {
            return;
        }
        this.mWhereValues[0] = String.valueOf(j);
    }

    @Override // com.xiaomi.mipush.sdk.stat.db.MessageDeleteJob, com.xiaomi.mipush.sdk.stat.db.base.DbManager.DeleteJob, com.xiaomi.mipush.sdk.stat.db.base.DbManager.BaseJob
    public String description() {
        return this.mDescription + " " + this.mWhereValues[0];
    }

    @Override // com.xiaomi.mipush.sdk.stat.db.base.DbManager.BaseJob, com.xiaomi.mipush.sdk.stat.db.base.ISerialSchedule
    public void input(Context context, Object obj) {
        if (obj instanceof Long) {
            long jLongValue = ((Long) obj).longValue();
            long fileSize = FileUtil.getFileSize(getDataPath());
            long j = DataBaseConfig.MAX_DB_SIZE;
            if (fileSize <= j) {
                MyLog.i("db size is suitable");
                return;
            }
            long j2 = (long) (jLongValue * (((fileSize - j) * 1.2d) / j));
            setLimit(j2);
            PushStatClientManager.getInstance(context).record("begin delete " + j2 + "noUpload messages , because db size is " + fileSize + "B");
            super.input(context, obj);
        }
    }
}
