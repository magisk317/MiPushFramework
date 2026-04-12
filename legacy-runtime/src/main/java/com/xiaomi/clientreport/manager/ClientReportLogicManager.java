package com.xiaomi.clientreport.manager;

import android.content.Context;
import com.xiaomi.channel.commonutils.android.MIUIUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.ScheduledJobConstants;
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager;
import com.xiaomi.channel.commonutils.string.XMStringUtils;
import com.xiaomi.clientreport.data.BaseClientReport;
import com.xiaomi.clientreport.data.ClientReportConstants;
import com.xiaomi.clientreport.data.Config;
import com.xiaomi.clientreport.data.EventClientReport;
import com.xiaomi.clientreport.data.PerfClientReport;
import com.xiaomi.clientreport.job.EventUploadJob;
import com.xiaomi.clientreport.job.PerfUploadJob;
import com.xiaomi.clientreport.job.ReadAndSendJob;
import com.xiaomi.clientreport.processor.IEventProcessor;
import com.xiaomi.clientreport.processor.IPerfProcessor;
import com.xiaomi.clientreport.util.ClientReportUtil;
import com.xiaomi.clientreport.util.SPManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/clientreport/manager/ClientReportLogicManager.class */
public class ClientReportLogicManager {
    private static final int DELAY_TO_FILE;
    private static final int WRITE_THRESHOLD = 10;
    private static volatile ClientReportLogicManager sInstance;
    private String PUSH_VERSION_NAME;
    private Config mConfig;
    private Context mContext;
    private IEventProcessor mEventProcessor;
    private IPerfProcessor mPerfProcessor;
    private ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private HashMap<String, HashMap<String, BaseClientReport>> mPerfMap = new HashMap<>();
    private HashMap<String, ArrayList<BaseClientReport>> mEventMap = new HashMap<>();

    static {
        DELAY_TO_FILE = MIUIUtils.isMIUI() ? 30 : 10;
    }

    private ClientReportLogicManager(Context context) {
        this.mContext = context;
    }

    private void delayRunJob(ScheduledJobManager.Job job, int i) {
        ScheduledJobManager.getInstance(this.mContext).addOneShootJob(job, i);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int getEventCacheCountInMemory() {
        int i = 0;
        int size = 0;
        HashMap<String, ArrayList<BaseClientReport>> map = this.mEventMap;
        if (map != null) {
            Iterator<String> it = map.keySet().iterator();
            while (true) {
                i = size;
                if (!it.hasNext()) {
                    break;
                }
                ArrayList<BaseClientReport> arrayList = this.mEventMap.get(it.next());
                size += arrayList != null ? arrayList.size() : 0;
            }
        }
        return i;
    }

    public static ClientReportLogicManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (ClientReportLogicManager.class) {
                try {
                    if (sInstance == null) {
                        sInstance = new ClientReportLogicManager(context);
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
        return sInstance;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int getPerfCacheCountInMemory() {
        int i = 0;
        int i2 = 0;
        HashMap<String, HashMap<String, BaseClientReport>> map = this.mPerfMap;
        if (map != null) {
            Iterator<String> it = map.keySet().iterator();
            while (true) {
                i = i2;
                if (!it.hasNext()) {
                    break;
                }
                HashMap<String, BaseClientReport> map2 = this.mPerfMap.get(it.next());
                int i3 = i2;
                if (map2 != null) {
                    Iterator<String> it2 = map2.keySet().iterator();
                    while (true) {
                        i3 = i2;
                        if (it2.hasNext()) {
                            BaseClientReport baseClientReport = map2.get(it2.next());
                            int i4 = i2;
                            if (baseClientReport instanceof PerfClientReport) {
                                i4 = (int) (((long) i2) + ((PerfClientReport) baseClientReport).perfCounts);
                            }
                            i2 = i4;
                        }
                    }
                }
                i2 = i3;
            }
        }
        return i;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void processEventData(EventClientReport eventClientReport) {
        IEventProcessor iEventProcessor = this.mEventProcessor;
        if (iEventProcessor != null) {
            iEventProcessor.preProcess(eventClientReport);
            if (getEventCacheCountInMemory() < 10) {
                delayRunJob(new ScheduledJobManager.Job() { // from class: com.xiaomi.clientreport.manager.ClientReportLogicManager.3
                    @Override // com.xiaomi.channel.commonutils.misc.ScheduledJobManager.Job
                    public String getJobId() {
                        return ScheduledJobConstants.CHECK_WRITE_EVENT_JOB_ID;
                    }

                    @Override // java.lang.Runnable
                    public void run() {
                        if (ClientReportLogicManager.this.getEventCacheCountInMemory() > 0) {
                            ClientReportLogicManager.this.mExecutor.execute(new Runnable() { // from class: com.xiaomi.clientreport.manager.ClientReportLogicManager.3.1
                                @Override // java.lang.Runnable
                                public void run() {
                                    ClientReportLogicManager.this.writeEvent2File();
                                }
                            });
                        }
                    }
                }, DELAY_TO_FILE);
            } else {
                writeEvent2File();
                ScheduledJobManager.getInstance(this.mContext).cancelJob(ScheduledJobConstants.CHECK_WRITE_EVENT_JOB_ID);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void processPerfData(PerfClientReport perfClientReport) {
        IPerfProcessor iPerfProcessor = this.mPerfProcessor;
        if (iPerfProcessor != null) {
            iPerfProcessor.preProcess(perfClientReport);
            if (getPerfCacheCountInMemory() < 10) {
                delayRunJob(new ScheduledJobManager.Job() { // from class: com.xiaomi.clientreport.manager.ClientReportLogicManager.4
                    @Override // com.xiaomi.channel.commonutils.misc.ScheduledJobManager.Job
                    public String getJobId() {
                        return ScheduledJobConstants.CHECK_WRITE_PERF_JOB_ID;
                    }

                    @Override // java.lang.Runnable
                    public void run() {
                        if (ClientReportLogicManager.this.getPerfCacheCountInMemory() > 0) {
                            ClientReportLogicManager.this.mExecutor.execute(new Runnable() { // from class: com.xiaomi.clientreport.manager.ClientReportLogicManager.4.1
                                @Override // java.lang.Runnable
                                public void run() {
                                    ClientReportLogicManager.this.writePerf2File();
                                }
                            });
                        }
                    }
                }, DELAY_TO_FILE);
            } else {
                writePerf2File();
                ScheduledJobManager.getInstance(this.mContext).cancelJob(ScheduledJobConstants.CHECK_WRITE_PERF_JOB_ID);
            }
        }
    }

    private void startEventUploadJob() {
        if (getInstance(this.mContext).getConfig().isEventUploadSwitchOpen()) {
            final EventUploadJob eventUploadJob = new EventUploadJob(this.mContext);
            int eventUploadFrequency = (int) getInstance(this.mContext).getConfig().getEventUploadFrequency();
            int i = eventUploadFrequency;
            if (eventUploadFrequency < 1800) {
                i = 1800;
            }
            if (System.currentTimeMillis() - SPManager.getInstance(this.mContext).getLongValue(ClientReportConstants.SP_FILE_STATUS, "event_last_upload_time", 0L) > i * 1000) {
                ScheduledJobManager.getInstance(this.mContext).addOneShootJob(new Runnable() { // from class: com.xiaomi.clientreport.manager.ClientReportLogicManager.5
                    @Override // java.lang.Runnable
                    public void run() {
                        eventUploadJob.run();
                    }
                }, 10);
            }
            synchronized (ClientReportLogicManager.class) {
                try {
                    if (!ScheduledJobManager.getInstance(this.mContext).addRepeatJob(eventUploadJob, i)) {
                        ScheduledJobManager.getInstance(this.mContext).cancelJob(ScheduledJobConstants.EVENT_UPLOAD_JOB_ID);
                        ScheduledJobManager.getInstance(this.mContext).addRepeatJob(eventUploadJob, i);
                    }
                } finally {
                }
            }
        }
    }

    private void startPerfUploadJob() {
        if (getInstance(this.mContext).getConfig().isPerfUploadSwitchOpen()) {
            final PerfUploadJob perfUploadJob = new PerfUploadJob(this.mContext);
            int perfUploadFrequency = (int) getInstance(this.mContext).getConfig().getPerfUploadFrequency();
            int i = perfUploadFrequency;
            if (perfUploadFrequency < 1800) {
                i = 1800;
            }
            if (System.currentTimeMillis() - SPManager.getInstance(this.mContext).getLongValue(ClientReportConstants.SP_FILE_STATUS, "perf_last_upload_time", 0L) > i * 1000) {
                ScheduledJobManager.getInstance(this.mContext).addOneShootJob(new Runnable() { // from class: com.xiaomi.clientreport.manager.ClientReportLogicManager.6
                    @Override // java.lang.Runnable
                    public void run() {
                        perfUploadJob.run();
                    }
                }, 15);
            }
            synchronized (ClientReportLogicManager.class) {
                try {
                    if (!ScheduledJobManager.getInstance(this.mContext).addRepeatJob(perfUploadJob, i)) {
                        ScheduledJobManager.getInstance(this.mContext).cancelJob(ScheduledJobConstants.PERF_UPLOAD_JOB_ID);
                        ScheduledJobManager.getInstance(this.mContext).addRepeatJob(perfUploadJob, i);
                    }
                } finally {
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void writeEvent2File() {
        try {
            this.mEventProcessor.process();
        } catch (Exception e) {
            MyLog.e("we: " + e.getMessage());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void writePerf2File() {
        try {
            this.mPerfProcessor.process();
        } catch (Exception e) {
            MyLog.e("wp: " + e.getMessage());
        }
    }

    public Config getConfig() {
        Config config;
        synchronized (this) {
            if (this.mConfig == null) {
                this.mConfig = Config.defaultConfig(this.mContext);
            }
            config = this.mConfig;
        }
        return config;
    }

    public void init(Config config, IEventProcessor iEventProcessor, IPerfProcessor iPerfProcessor) {
        this.mConfig = config;
        this.mEventProcessor = iEventProcessor;
        this.mPerfProcessor = iPerfProcessor;
        iEventProcessor.setEventMap(this.mEventMap);
        this.mPerfProcessor.setPerfMap(this.mPerfMap);
    }

    public EventClientReport newEvent(int i, String str) {
        EventClientReport eventClientReport = new EventClientReport();
        eventClientReport.eventContent = str;
        eventClientReport.eventTime = System.currentTimeMillis();
        eventClientReport.eventType = i;
        eventClientReport.eventId = XMStringUtils.generateRandomString(6);
        eventClientReport.production = 1000;
        eventClientReport.reportType = 1001;
        eventClientReport.clientInterfaceId = "E100004";
        eventClientReport.setAppPackageName(this.mContext.getPackageName());
        eventClientReport.setSdkVersion(this.PUSH_VERSION_NAME);
        return eventClientReport;
    }

    public void prepareInit(String str) {
        this.PUSH_VERSION_NAME = str;
    }

    public void sendEvent() {
        if (getConfig().isEventUploadSwitchOpen()) {
            ReadAndSendJob readAndSendJob = new ReadAndSendJob();
            readAndSendJob.setContext(this.mContext);
            readAndSendJob.setReadAndSender(this.mEventProcessor);
            this.mExecutor.execute(readAndSendJob);
        }
    }

    public void sendPerf() {
        if (getConfig().isPerfUploadSwitchOpen()) {
            ReadAndSendJob readAndSendJob = new ReadAndSendJob();
            readAndSendJob.setReadAndSender(this.mPerfProcessor);
            readAndSendJob.setContext(this.mContext);
            this.mExecutor.execute(readAndSendJob);
        }
    }

    public void startScheduleJob() {
        getInstance(this.mContext).startEventUploadJob();
        getInstance(this.mContext).startPerfUploadJob();
    }

    public void updateConfig(boolean z, boolean z2, long j, long j2) {
        Config config = this.mConfig;
        if (config != null) {
            if (z == config.isEventUploadSwitchOpen() && z2 == this.mConfig.isPerfUploadSwitchOpen() && j == this.mConfig.getEventUploadFrequency() && j2 == this.mConfig.getPerfUploadFrequency()) {
                return;
            }
            long eventUploadFrequency = this.mConfig.getEventUploadFrequency();
            long perfUploadFrequency = this.mConfig.getPerfUploadFrequency();
            Config configBuild = Config.getBuilder().setAESKey(ClientReportUtil.getEventKeyWithDefault(this.mContext)).setEventEncrypted(this.mConfig.isEventEncrypted()).setEventUploadSwitchOpen(z).setEventUploadFrequency(j).setPerfUploadSwitchOpen(z2).setPerfUploadFrequency(j2).build(this.mContext);
            this.mConfig = configBuild;
            if (!configBuild.isEventUploadSwitchOpen()) {
                ScheduledJobManager.getInstance(this.mContext).cancelJob(ScheduledJobConstants.EVENT_UPLOAD_JOB_ID);
            } else if (eventUploadFrequency != configBuild.getEventUploadFrequency()) {
                MyLog.v(this.mContext.getPackageName() + "reset event job " + configBuild.getEventUploadFrequency());
                startEventUploadJob();
            }
            if (!this.mConfig.isPerfUploadSwitchOpen()) {
                ScheduledJobManager.getInstance(this.mContext).cancelJob(ScheduledJobConstants.PERF_UPLOAD_JOB_ID);
                return;
            }
            if (perfUploadFrequency != configBuild.getPerfUploadFrequency()) {
                MyLog.v(this.mContext.getPackageName() + "reset perf job " + configBuild.getPerfUploadFrequency());
                startPerfUploadJob();
            }
        }
    }

    public void writeEvent(final EventClientReport eventClientReport) {
        if (getConfig().isEventUploadSwitchOpen()) {
            this.mExecutor.execute(new Runnable() { // from class: com.xiaomi.clientreport.manager.ClientReportLogicManager.1
                @Override // java.lang.Runnable
                public void run() {
                    ClientReportLogicManager.this.processEventData(eventClientReport);
                }
            });
        }
    }

    public void writePerf(final PerfClientReport perfClientReport) {
        if (getConfig().isPerfUploadSwitchOpen()) {
            this.mExecutor.execute(new Runnable() { // from class: com.xiaomi.clientreport.manager.ClientReportLogicManager.2
                @Override // java.lang.Runnable
                public void run() {
                    ClientReportLogicManager.this.processPerfData(perfClientReport);
                }
            });
        }
    }
}
