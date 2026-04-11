package com.xiaomi.push.log;

import android.content.Context;
import android.content.SharedPreferences;
import com.xiaomi.channel.commonutils.file.SDCardUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.SerializedAsyncTaskProcessor;
import com.xiaomi.channel.commonutils.network.Network;
import com.xiaomi.miui.pushads.sdk.NotifyAdsDef;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.push.service.ServiceConfig;
import com.xiaomi.smack.util.TaskExecutor;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/log/LogUploader.class */
public class LogUploader {
    private static final int EXPIRE_TIME = 172800000;
    private static final int MAX_PENDING_TASKS = 6;
    private static final int MAX_TIMES_PER_DAY = 10;
    private static final String PREF_KEY_REQUEST = "log.requst";
    private static final String PREF_NAME = "log.timestamp";
    private static final String ZIPPED_LOG_PATH = "/.logcache";
    private static volatile LogUploader sInstance = null;
    private Context mContext;
    private final ConcurrentLinkedQueue<Task> mTasks;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/log/LogUploader$CleanUpTask.class */
    class CleanUpTask extends Task {
        CleanUpTask() {
            super();
        }

        @Override // com.xiaomi.push.log.LogUploader.Task, com.xiaomi.channel.commonutils.misc.SerializedAsyncTaskProcessor.SerializedAsyncTask
        public void process() {
            LogUploader.this.cleanUp();
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/log/LogUploader$Task.class */
    class Task extends SerializedAsyncTaskProcessor.SerializedAsyncTask {
        long timestamp = System.currentTimeMillis();

        Task() {
        }

        public boolean canExcuteNow() {
            return true;
        }

        final boolean isExpired() {
            return System.currentTimeMillis() - this.timestamp > 172800000;
        }

        @Override // com.xiaomi.channel.commonutils.misc.SerializedAsyncTaskProcessor.SerializedAsyncTask
        public void process() {
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/log/LogUploader$UploadTask.class */
    class UploadTask extends Task {
        File file;
        boolean force;
        int retryNum;
        String token;
        boolean uploaded;
        String url;

        UploadTask(String str, String str2, File file, boolean z) {
            super();
            this.url = str;
            this.token = str2;
            this.file = file;
            this.force = z;
        }

        private boolean checkLimit() throws JSONException {
            int i;
            SharedPreferences sharedPreferences = LogUploader.this.mContext.getSharedPreferences(LogUploader.PREF_NAME, 0);
            String string = sharedPreferences.getString(LogUploader.PREF_KEY_REQUEST, "");
            int i2 = 0;
            long jCurrentTimeMillis = System.currentTimeMillis();
            try {
                JSONObject jSONObject = new JSONObject(string);
                long j = jSONObject.getLong(NotifyAdsDef.JSON_TAG_ACTIONTIME);
                jCurrentTimeMillis = j;
                i2 = jSONObject.getInt("times");
                jCurrentTimeMillis = j;
            } catch (JSONException e) {
            }
            if (System.currentTimeMillis() - jCurrentTimeMillis < 86400000) {
                i = i2;
                if (i2 > 10) {
                    return false;
                }
            } else {
                jCurrentTimeMillis = System.currentTimeMillis();
                i = 0;
            }
            JSONObject jSONObject2 = new JSONObject();
            try {
                jSONObject2.put(NotifyAdsDef.JSON_TAG_ACTIONTIME, jCurrentTimeMillis);
                jSONObject2.put("times", i + 1);
                sharedPreferences.edit().putString(LogUploader.PREF_KEY_REQUEST, jSONObject2.toString()).commit();
                return true;
            } catch (JSONException e2) {
                MyLog.v("JSONException on put " + e2.getMessage());
                return true;
            }
        }

        @Override // com.xiaomi.push.log.LogUploader.Task
        public boolean canExcuteNow() {
            return Network.isWIFIConnected(LogUploader.this.mContext) || (this.force && Network.hasNetwork(LogUploader.this.mContext));
        }

        @Override // com.xiaomi.channel.commonutils.misc.SerializedAsyncTaskProcessor.SerializedAsyncTask
        public void postProcess() {
            if (!this.uploaded) {
                int i = this.retryNum + 1;
                this.retryNum = i;
                if (i < 3) {
                    LogUploader.this.mTasks.add(this);
                }
            }
            if (this.uploaded || this.retryNum >= 3) {
                this.file.delete();
            }
            LogUploader.this.uploadIfNeed((1 << this.retryNum) * 1000);
        }

        @Override // com.xiaomi.push.log.LogUploader.Task, com.xiaomi.channel.commonutils.misc.SerializedAsyncTaskProcessor.SerializedAsyncTask
        public void process() {
            try {
                if (checkLimit()) {
                    Map<String, String> map = new HashMap<>();
                    map.put("uid", ServiceConfig.getDeviceUUID());
                    map.put("token", this.token);
                    map.put("net", Network.getActiveConnPoint(LogUploader.this.mContext));
                    Network.uploadFile(this.url, map, this.file, PushConstants.UPLOAD_FILE_POST_KEY);
                }
                this.uploaded = true;
            } catch (Exception e) {
            }
        }
    }

    private LogUploader(Context context) {
        ConcurrentLinkedQueue<Task> concurrentLinkedQueue = new ConcurrentLinkedQueue<>();
        this.mTasks = concurrentLinkedQueue;
        this.mContext = context;
        concurrentLinkedQueue.add(new CleanUpTask());
        executeTask(0L);
    }

    private void cleanExpiredTask() {
        while (!this.mTasks.isEmpty()) {
            Task taskPeek = this.mTasks.peek();
            if (taskPeek != null) {
                if (!taskPeek.isExpired() && this.mTasks.size() <= 6) {
                    return;
                }
                MyLog.v("remove Expired task");
                this.mTasks.remove(taskPeek);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void cleanUp() {
        if (SDCardUtils.isSDCardBusy() || SDCardUtils.isSDCardUnavailable()) {
            return;
        }
        try {
            File file = new File(this.mContext.getExternalFilesDir(null) + ZIPPED_LOG_PATH);
            if (file.exists() && file.isDirectory()) {
                for (File file2 : file.listFiles()) {
                    file2.delete();
                }
            }
        } catch (NullPointerException e) {
        }
    }

    private void executeTask(long j) {
        if (this.mTasks.isEmpty()) {
            return;
        }
        TaskExecutor.execute(new SerializedAsyncTaskProcessor.SerializedAsyncTask() { // from class: com.xiaomi.push.log.LogUploader.2
            SerializedAsyncTaskProcessor.SerializedAsyncTask current;

            @Override // com.xiaomi.channel.commonutils.misc.SerializedAsyncTaskProcessor.SerializedAsyncTask
            public void postProcess() {
                SerializedAsyncTaskProcessor.SerializedAsyncTask serializedAsyncTask = this.current;
                if (serializedAsyncTask != null) {
                    serializedAsyncTask.postProcess();
                }
            }

            @Override // com.xiaomi.channel.commonutils.misc.SerializedAsyncTaskProcessor.SerializedAsyncTask
            public void process() {
                Task task = (Task) LogUploader.this.mTasks.peek();
                if (task == null || !task.canExcuteNow()) {
                    return;
                }
                if (LogUploader.this.mTasks.remove(task)) {
                    this.current = task;
                }
                SerializedAsyncTaskProcessor.SerializedAsyncTask serializedAsyncTask = this.current;
                if (serializedAsyncTask != null) {
                    serializedAsyncTask.process();
                }
            }
        }, j);
    }

    public static LogUploader getInstance(Context context) {
        if (sInstance == null) {
            synchronized (LogUploader.class) {
                try {
                    if (sInstance == null) {
                        sInstance = new LogUploader(context);
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
        sInstance.mContext = context;
        return sInstance;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void uploadIfNeed(long j) {
        Task taskPeek = this.mTasks.peek();
        if (taskPeek == null || !taskPeek.canExcuteNow()) {
            return;
        }
        executeTask(j);
    }

    public void checkUpload() {
        cleanExpiredTask();
        uploadIfNeed(0L);
    }

    public void upload(final String str, final String str2, final Date date, final Date date2, final int i, final boolean z) {
        this.mTasks.add(new Task() { // from class: com.xiaomi.push.log.LogUploader.1
            File file;

            @Override // com.xiaomi.channel.commonutils.misc.SerializedAsyncTaskProcessor.SerializedAsyncTask
            public void postProcess() {
                File file = this.file;
                if (file != null && file.exists()) {
                    LogUploader.this.mTasks.add(LogUploader.this.new UploadTask(str, str2, this.file, z));
                }
                LogUploader.this.uploadIfNeed(0L);
            }

            @Override // com.xiaomi.push.log.LogUploader.Task, com.xiaomi.channel.commonutils.misc.SerializedAsyncTaskProcessor.SerializedAsyncTask
            public void process() {
                if (SDCardUtils.isSDCardUseful()) {
                    try {
                        File file = new File(LogUploader.this.mContext.getExternalFilesDir(null) + LogUploader.ZIPPED_LOG_PATH);
                        file.mkdirs();
                        if (file.isDirectory()) {
                            LogFilter logFilter = new LogFilter();
                            logFilter.setMaxLen(i);
                            this.file = logFilter.filter(LogUploader.this.mContext, date, date2, file);
                        }
                    } catch (NullPointerException e) {
                    }
                }
            }
        });
        executeTask(0L);
    }
}
