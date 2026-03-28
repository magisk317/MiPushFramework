package com.xiaomi.mipush.sdk.stat;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.SharedPrefsCompat;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.ScheduledJobConstants;
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager;
import com.xiaomi.mipush.sdk.stat.db.DataBaseConfig;
import com.xiaomi.mipush.sdk.stat.db.DbSizeControlJob;
import com.xiaomi.mipush.sdk.stat.db.MessageDbHelperFactory;
import com.xiaomi.mipush.sdk.stat.db.MessageInfoContract;
import com.xiaomi.mipush.sdk.stat.db.MessageInsertJob;
import com.xiaomi.mipush.sdk.stat.db.base.DbManager;
import com.xiaomi.mipush.sdk.stat.upload.BaseDataSender;
import com.xiaomi.mipush.sdk.stat.upload.BaseScheduleWorker;
import com.xiaomi.mipush.sdk.stat.upload.DefaultDbPathGetter;
import com.xiaomi.mipush.sdk.stat.upload.IDataSender;
import com.xiaomi.mipush.sdk.stat.upload.IDbPathGetter;
import com.xiaomi.mipush.sdk.stat.upload.IScheduleWorker;
import com.xiaomi.mipush.sdk.stat.upload.UploadDataHelper;
import com.xiaomi.push.service.OnlineConfig;
import com.xiaomi.push.service.TinyDataHelper;
import com.xiaomi.xmpush.thrift.ClientUploadDataItem;
import com.xiaomi.xmpush.thrift.ConfigKey;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/stat/PushStatClientManager.class */
public class PushStatClientManager {
    public static final int DEFAULT_FREQUENCY = 3600;
    private static final String TAG = "PUSH_STAT";
    private static volatile PushStatClientManager sInstance;
    private String mAppId;
    private String mChannel;
    private Context mContext;
    private IDataSender mDataSender;
    private IDbPathGetter mDbPathGetter;
    private int mDeleteFrequency;
    private String mPackageName;
    private IScheduleWorker mScheduleJob;
    private int mUploadFrequency;
    private final String mSPName = "push_stat_sp";
    private final String mSPUploadKey = "upload_time";
    private final String mSPDeleteKey = "delete_time";
    private final String mSPCheckKey = "check_time";
    private ScheduledJobManager.Job mUploadJob = new ScheduledJobManager.Job() { // from class: com.xiaomi.mipush.sdk.stat.PushStatClientManager.1
        @Override // com.xiaomi.channel.commonutils.misc.ScheduledJobManager.Job
        public String getJobId() {
            return ScheduledJobConstants.STAT_UPLOAD_JOB_ID;
        }

        @Override // java.lang.Runnable
        public void run() {
            MyLog.v("exec== mUploadJob");
            if (PushStatClientManager.this.mScheduleJob != null) {
                PushStatClientManager.this.mScheduleJob.onUpload(PushStatClientManager.this.mContext);
                PushStatClientManager.this.updateTime("upload_time");
            }
        }
    };
    private ScheduledJobManager.Job mCheckDbSizeJob = new ScheduledJobManager.Job() { // from class: com.xiaomi.mipush.sdk.stat.PushStatClientManager.2
        @Override // com.xiaomi.channel.commonutils.misc.ScheduledJobManager.Job
        public String getJobId() {
            return ScheduledJobConstants.STAT_CHECK_DB_ID;
        }

        @Override // java.lang.Runnable
        public void run() {
            MyLog.v("exec== DbSizeControlJob");
            DbManager.getInstance(PushStatClientManager.this.mContext).execR(new DbSizeControlJob(PushStatClientManager.this.getDbPath(), new WeakReference(PushStatClientManager.this.mContext)));
            PushStatClientManager.this.updateTime("check_time");
        }
    };
    private ScheduledJobManager.Job mDeleteJob = new ScheduledJobManager.Job() { // from class: com.xiaomi.mipush.sdk.stat.PushStatClientManager.3
        @Override // com.xiaomi.channel.commonutils.misc.ScheduledJobManager.Job
        public String getJobId() {
            return ScheduledJobConstants.STAT_DELETE_JOB_ID;
        }

        @Override // java.lang.Runnable
        public void run() {
            if (PushStatClientManager.this.mScheduleJob != null) {
                PushStatClientManager.this.mScheduleJob.onDelete(PushStatClientManager.this.mContext);
                PushStatClientManager.this.updateTime("delete_time");
            }
        }
    };

    private PushStatClientManager(Context context) {
        this.mContext = context;
    }

    private boolean OCSwitch() {
        return OnlineConfig.getInstance(this.mContext).getBooleanValue(ConfigKey.StatDataSwitch.getValue(), true);
    }

    private boolean checkTime(String str, int i) {
        long jCurrentTimeMillis = System.currentTimeMillis() - getLastTime(str);
        MyLog.v("checkTime:  period = " + jCurrentTimeMillis + "   frequency = " + (i * 1000));
        return jCurrentTimeMillis > ((long) (i * 1000));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public String getDbPath() {
        return this.mContext.getDatabasePath(DataBaseConfig.DATABASE_NAME).getAbsolutePath();
    }

    public static PushStatClientManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (PushStatClientManager.class) {
                try {
                    if (sInstance == null) {
                        sInstance = new PushStatClientManager(context);
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
        return sInstance;
    }

    private long getLastTime(String str) {
        return this.mContext.getSharedPreferences("push_stat_sp", 0).getLong(str, 0L);
    }

    private void initDataSender() {
        if (this.mDataSender == null) {
            synchronized (PushStatClientManager.class) {
                try {
                    if (this.mDataSender == null) {
                        if (this.mDbPathGetter == null) {
                            this.mDbPathGetter = new DefaultDbPathGetter();
                        }
                        this.mDataSender = new BaseDataSender(this.mDbPathGetter);
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
    }

    private void initSchedule() {
        if (this.mScheduleJob == null) {
            synchronized (PushStatClientManager.class) {
                try {
                    if (this.mScheduleJob == null) {
                        if (this.mDbPathGetter == null) {
                            this.mDbPathGetter = new DefaultDbPathGetter();
                        }
                        this.mScheduleJob = new BaseScheduleWorker(this.mDbPathGetter);
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
    }

    private void onInit() {
        if (OCSwitch()) {
            DbManager.getInstance(this.mContext).init(new MessageDbHelperFactory());
            initDataSender();
            initSchedule();
            scheduleCheckJob();
            recorderChannel(this.mChannel);
            OnlineConfig.getInstance(this.mContext).addOCUpdateCallbacks(new OnlineConfig.OCUpdateCallback(103, "stat data updata job") { // from class: com.xiaomi.mipush.sdk.stat.PushStatClientManager.4
                @Override // com.xiaomi.push.service.OnlineConfig.OCUpdateCallback
                protected void onCallback() {
                    PushStatClientManager.this.resetUploadFrequency((int) OnlineConfig.getInstance(PushStatClientManager.this.mContext).getLongValue(ConfigKey.StatDataUploadFrequency.getValue(), 3600L), (int) OnlineConfig.getInstance(PushStatClientManager.this.mContext).getLongValue(ConfigKey.StatDataDeleteFrequency.getValue(), 3600L));
                }
            });
        }
    }

    private void recorderChannel(String str) {
        if (TextUtils.isEmpty(str)) {
            return;
        }
        JSONObject jSONObject = new JSONObject();
        try {
            jSONObject.put("channel", str);
            record(jSONObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void resetUploadFrequency(int i, int i2) {
        if (OCSwitch()) {
            if (i != this.mUploadFrequency) {
                this.mUploadFrequency = i;
                ScheduledJobManager.getInstance(this.mContext).cancelJob(ScheduledJobConstants.STAT_UPLOAD_JOB_ID);
                ScheduledJobManager.getInstance(this.mContext).addRepeatJob(this.mUploadJob, this.mUploadFrequency);
            }
            if (i2 != this.mDeleteFrequency) {
                this.mDeleteFrequency = i2;
                ScheduledJobManager.getInstance(this.mContext).cancelJob(ScheduledJobConstants.STAT_DELETE_JOB_ID);
                ScheduledJobManager.getInstance(this.mContext).addRepeatJob(this.mDeleteJob, this.mDeleteFrequency);
            }
        }
    }

    private void scheduleCheckJob() {
        if (checkTime("check_time", 43200)) {
            ScheduledJobManager.getInstance(this.mContext).addOneShootJob(new Runnable() { // from class: com.xiaomi.mipush.sdk.stat.PushStatClientManager.7
                @Override // java.lang.Runnable
                public void run() {
                    PushStatClientManager.this.mCheckDbSizeJob.run();
                }
            }, 20);
        }
        ScheduledJobManager.getInstance(this.mContext).addRepeatJob(this.mCheckDbSizeJob, 43200);
    }

    private void scheduleDeleteJob() {
        if (checkTime("delete_time", this.mDeleteFrequency)) {
            ScheduledJobManager.getInstance(this.mContext).addOneShootJob(new Runnable() { // from class: com.xiaomi.mipush.sdk.stat.PushStatClientManager.6
                @Override // java.lang.Runnable
                public void run() {
                    PushStatClientManager.this.mDeleteJob.run();
                }
            }, 15);
        }
        ScheduledJobManager.getInstance(this.mContext).addRepeatJob(this.mDeleteJob, this.mDeleteFrequency);
    }

    private void scheduleUploadJob() {
        if (checkTime("upload_time", this.mUploadFrequency)) {
            ScheduledJobManager.getInstance(this.mContext).addOneShootJob(new Runnable() { // from class: com.xiaomi.mipush.sdk.stat.PushStatClientManager.5
                @Override // java.lang.Runnable
                public void run() {
                    PushStatClientManager.this.mUploadJob.run();
                }
            }, 10);
        }
        ScheduledJobManager.getInstance(this.mContext).addRepeatJob(this.mUploadJob, this.mUploadFrequency);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateTime(String str) {
        SharedPreferences.Editor editorEdit = this.mContext.getSharedPreferences("push_stat_sp", 0).edit();
        editorEdit.putLong(str, System.currentTimeMillis());
        SharedPrefsCompat.apply(editorEdit);
    }

    public void exec(DbManager.BaseJob baseJob) {
        DbManager.getInstance(this.mContext).exec(baseJob);
    }

    public void exec(ArrayList<DbManager.BaseJob> arrayList) {
        DbManager.getInstance(this.mContext).exec(arrayList);
    }

    public void execDelay(DbManager.BaseJob baseJob, int i) {
        DbManager.getInstance(this.mContext).execDelay(baseJob, i);
    }

    public String getAppId() {
        return this.mAppId;
    }

    public int getDeleteFrequency() {
        return this.mDeleteFrequency;
    }

    public String getPackageName() {
        return this.mPackageName;
    }

    public int getUploadFrequency() {
        return this.mUploadFrequency;
    }

    public void init(String str, String str2) {
        init(this.mContext.getPackageName(), str, str2, new DefaultDbPathGetter());
    }

    public void init(String str, String str2, IDbPathGetter iDbPathGetter) {
        init(this.mContext.getPackageName(), str, str2, iDbPathGetter);
    }

    public void init(String str, String str2, String str3) {
        init(str, str2, str3, new DefaultDbPathGetter());
    }

    public void init(String str, String str2, String str3, IDbPathGetter iDbPathGetter) {
        if (TextUtils.isEmpty(str)) {
            MyLog.e("packageName can not be null; ");
            return;
        }
        if (TextUtils.isEmpty(str2)) {
            MyLog.e("appId can not be null; ");
            return;
        }
        long longValue = OnlineConfig.getInstance(this.mContext).getLongValue(ConfigKey.StatDataUploadFrequency.getValue(), 3600L);
        long longValue2 = OnlineConfig.getInstance(this.mContext).getLongValue(ConfigKey.StatDataDeleteFrequency.getValue(), 3600L);
        this.mAppId = str2;
        this.mPackageName = str;
        this.mUploadFrequency = (int) longValue;
        this.mDeleteFrequency = (int) longValue2;
        this.mChannel = str3;
        this.mDbPathGetter = iDbPathGetter;
        onInit();
    }

    public void onResult(String str, String str2, Boolean bool) {
        if (this.mDataSender != null) {
            if (bool.booleanValue()) {
                this.mDataSender.onSuccess(this.mContext, str2, str);
            } else {
                this.mDataSender.onFailed(this.mContext, str2, str);
            }
        }
    }

    public void record(ClientUploadDataItem clientUploadDataItem) {
        if (OCSwitch() && TinyDataHelper.shouldUpload(clientUploadDataItem.getPkgName())) {
            exec(MessageInsertJob.buildInsertJob(this.mContext, getDbPath(), clientUploadDataItem));
        }
    }

    public void record(String str) {
        if (OCSwitch() && !TextUtils.isEmpty(str)) {
            record(UploadDataHelper.wrapperData(this.mContext, str));
        }
    }

    public void schedule() {
        if (OCSwitch()) {
            scheduleUploadJob();
            scheduleDeleteJob();
        }
    }

    public void send(String str, List<MessageInfoContract.MessageModel> list) {
        IDataSender iDataSender;
        if (OCSwitch() && (iDataSender = this.mDataSender) != null) {
            iDataSender.onSend(this.mContext, str, list);
        }
    }
}
