package com.xiaomi.channel.commonutils.misc;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import com.xiaomi.channel.commonutils.logger.MyLog;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/misc/SerializedAsyncTaskProcessor.class */
public class SerializedAsyncTaskProcessor {
    private static final int MSG_AFTER_EXECUTE = 1;
    private static final int MSG_BEFORE_EXECUTE = 0;
    private volatile SerializedAsyncTask mCurrentTask;
    private final boolean mIsDaemon;
    private int mKeepAliveTime;
    private Handler mMainThreadHandler;
    private ProcessPackageThread mProcessThread;
    private volatile boolean threadQuit;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/misc/SerializedAsyncTaskProcessor$ProcessPackageThread.class */
    private class ProcessPackageThread extends Thread {
        private static final String THREAD_NAME = "PackageProcessor";
        private final LinkedBlockingQueue<SerializedAsyncTask> mTasks;

        public ProcessPackageThread() {
            super(THREAD_NAME);
            this.mTasks = new LinkedBlockingQueue<>();
        }

        private void notifyUI(int i, SerializedAsyncTask serializedAsyncTask) {
            try {
                SerializedAsyncTaskProcessor.this.mMainThreadHandler.sendMessage(SerializedAsyncTaskProcessor.this.mMainThreadHandler.obtainMessage(i, serializedAsyncTask));
            } catch (Exception e) {
                MyLog.e(e);
            }
        }

        public void insertTask(SerializedAsyncTask serializedAsyncTask) {
            try {
                this.mTasks.add(serializedAsyncTask);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            long j = SerializedAsyncTaskProcessor.this.mKeepAliveTime > 0 ? SerializedAsyncTaskProcessor.this.mKeepAliveTime : Long.MAX_VALUE;
            while (!SerializedAsyncTaskProcessor.this.threadQuit) {
                try {
                    SerializedAsyncTask serializedAsyncTaskPoll = this.mTasks.poll(j, TimeUnit.SECONDS);
                    SerializedAsyncTaskProcessor.this.mCurrentTask = serializedAsyncTaskPoll;
                    if (serializedAsyncTaskPoll != null) {
                        notifyUI(0, serializedAsyncTaskPoll);
                        serializedAsyncTaskPoll.process();
                        notifyUI(1, serializedAsyncTaskPoll);
                    } else if (SerializedAsyncTaskProcessor.this.mKeepAliveTime > 0) {
                        SerializedAsyncTaskProcessor.this.stopTaskProcessor();
                    }
                } catch (InterruptedException e) {
                    MyLog.e(e);
                }
            }
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/misc/SerializedAsyncTaskProcessor$SerializedAsyncTask.class */
    public static abstract class SerializedAsyncTask {
        public void postProcess() {
        }

        public void preProcess() {
        }

        public abstract void process();
    }

    public SerializedAsyncTaskProcessor() {
        this(false);
    }

    public SerializedAsyncTaskProcessor(boolean z) {
        this(z, 0);
    }

    public SerializedAsyncTaskProcessor(boolean z, int i) {
        this.mMainThreadHandler = null;
        this.threadQuit = false;
        this.mKeepAliveTime = 0;
        this.mMainThreadHandler = new Handler(Looper.getMainLooper()) { // from class: com.xiaomi.channel.commonutils.misc.SerializedAsyncTaskProcessor.1
            @Override // android.os.Handler
            public void handleMessage(Message message) {
                SerializedAsyncTask serializedAsyncTask = (SerializedAsyncTask) message.obj;
                if (message.what == 0) {
                    serializedAsyncTask.preProcess();
                } else if (message.what == 1) {
                    serializedAsyncTask.postProcess();
                }
                super.handleMessage(message);
            }
        };
        this.mIsDaemon = z;
        this.mKeepAliveTime = i;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void stopTaskProcessor() {
        synchronized (this) {
            this.mProcessThread = null;
            this.threadQuit = true;
        }
    }

    public void addNewTask(SerializedAsyncTask serializedAsyncTask) {
        synchronized (this) {
            if (this.mProcessThread == null) {
                ProcessPackageThread processPackageThread = new ProcessPackageThread();
                this.mProcessThread = processPackageThread;
                processPackageThread.setDaemon(this.mIsDaemon);
                this.threadQuit = false;
                this.mProcessThread.start();
            }
            this.mProcessThread.insertTask(serializedAsyncTask);
        }
    }

    public void addNewTaskWithDelayed(final SerializedAsyncTask serializedAsyncTask, long j) {
        this.mMainThreadHandler.postDelayed(new Runnable() { // from class: com.xiaomi.channel.commonutils.misc.SerializedAsyncTaskProcessor.2
            @Override // java.lang.Runnable
            public void run() {
                SerializedAsyncTaskProcessor.this.addNewTask(serializedAsyncTask);
            }
        }, j);
    }

    public void clearTask() {
        ProcessPackageThread processPackageThread = this.mProcessThread;
        if (processPackageThread != null) {
            processPackageThread.mTasks.clear();
        }
    }

    public void destroy() {
        this.threadQuit = true;
    }

    public SerializedAsyncTask getCurrentTask() {
        return this.mCurrentTask;
    }
}
