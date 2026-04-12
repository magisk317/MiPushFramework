package com.xiaomi.clientreport.job;

import android.content.Context;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.ScheduledJobConstants;
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager;
import com.xiaomi.clientreport.manager.ClientReportLogicManager;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/clientreport/job/EventUploadJob.class */
public class EventUploadJob extends ScheduledJobManager.Job {
    private Context mContext;

    public EventUploadJob(Context context) {
        this.mContext = context;
    }

    private boolean checkEventNeedUpload() {
        return ClientReportLogicManager.getInstance(this.mContext).getConfig().isEventUploadSwitchOpen();
    }

    @Override // com.xiaomi.channel.commonutils.misc.ScheduledJobManager.Job
    public String getJobId() {
        return ScheduledJobConstants.EVENT_UPLOAD_JOB_ID;
    }

    @Override // java.lang.Runnable
    public void run() {
        try {
            if (checkEventNeedUpload()) {
                MyLog.v(this.mContext.getPackageName() + " begin upload event");
                ClientReportLogicManager.getInstance(this.mContext).sendEvent();
            }
        } catch (Exception e) {
            MyLog.e(e);
        }
    }
}
