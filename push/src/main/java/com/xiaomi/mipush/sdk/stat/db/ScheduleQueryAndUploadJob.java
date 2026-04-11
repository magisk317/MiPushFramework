package com.xiaomi.mipush.sdk.stat.db;

import android.content.Context;
import com.xiaomi.mipush.sdk.stat.db.base.DbManager;
import com.xiaomi.mipush.sdk.stat.util.FileUtil;
import com.xiaomi.push.mpcd.Constants;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/stat/db/ScheduleQueryAndUploadJob.class */
public class ScheduleQueryAndUploadJob extends MessageCountQueryJob {
    public ScheduleQueryAndUploadJob(String str, List<String> list, String str2, String[] strArr, String str3, String str4, String str5, int i) {
        super(str, list, str2, strArr, str3, str4, str5, i, "job to schedule upload jobs");
    }

    public static ScheduleQueryAndUploadJob getScheduleJob(String str) {
        ArrayList<String> arrayList = new ArrayList<>();
        arrayList.add("count(*)");
        return new ScheduleQueryAndUploadJob(str, arrayList, "status = ? or (status = ? and uploadTimestamp <= ?" + Constants.SEPARATOR_RIGHT_PARENTESIS, new String[]{String.valueOf(0), String.valueOf(1), String.valueOf(System.currentTimeMillis() - MessageInfoContract.TIMEOUT)}, null, null, null, 0);
    }

    @Override // com.xiaomi.mipush.sdk.stat.db.MessageCountQueryJob, com.xiaomi.mipush.sdk.stat.db.base.DbManager.BaseQueryJob
    public void notifyResult(Context context, List<Long> list) {
        MessageQueryJob messageQueryJob;
        int suitableLimit = FileUtil.getSuitableLimit(context);
        long jLongValue = list.get(0).longValue();
        if (jLongValue <= suitableLimit) {
            DbManager.getInstance(context).execDelay(MessageQueryJob.newInstance(getDataPath(), (int) jLongValue, (int) jLongValue), 0);
            return;
        }
        long j = suitableLimit;
        int i = 0;
        MessageQueryJob messageQueryJob2 = null;
        MessageQueryJob messageQueryJob3 = null;
        while (true) {
            messageQueryJob = messageQueryJob3;
            if (j >= jLongValue) {
                break;
            }
            MessageQueryJob messageQueryJobNewInstance = MessageQueryJob.newInstance(getDataPath(), suitableLimit, (int) jLongValue);
            MessageQueryJob messageQueryJob4 = messageQueryJob;
            if (messageQueryJob == null) {
                messageQueryJob4 = messageQueryJobNewInstance;
            }
            if (messageQueryJob2 != null) {
                messageQueryJob2.append(messageQueryJobNewInstance);
            }
            messageQueryJob2 = messageQueryJobNewInstance;
            j += (long) suitableLimit;
            i += 10;
            messageQueryJob3 = messageQueryJob4;
        }
        int i2 = (int) ((jLongValue - j) + ((long) suitableLimit));
        MessageQueryJob messageQueryJobNewInstance2 = MessageQueryJob.newInstance(getDataPath(), i2, i2);
        if (messageQueryJob2 != null) {
            messageQueryJob2.append(messageQueryJobNewInstance2);
        }
        DbManager.getInstance(context).exec(messageQueryJob);
    }
}
