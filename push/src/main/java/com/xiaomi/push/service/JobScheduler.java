package com.xiaomi.push.service;

import com.xiaomi.channel.commonutils.logger.MyLog;
import java.util.concurrent.RejectedExecutionException;

public class JobScheduler {
    private final FinalizerHelper finalizer;
    private final JobSchedulerThread impl;

    private static final class FinalizerHelper {
        private final JobSchedulerThread impl;

        FinalizerHelper(JobSchedulerThread jobSchedulerThread) {
            this.impl = jobSchedulerThread;
        }

        protected void finalize() throws Throwable {
            try {
                this.impl.finish();
            } finally {
                super.finalize();
            }
        }
    }

    public static abstract class Job implements Runnable {
        protected int type;

        public Job(int i) {
            this.type = i;
        }
    }

    public JobScheduler() {
        this(false);
    }

    public JobScheduler(String str) {
        this(str, false);
    }

    public JobScheduler(String str, boolean z) {
        if (str == null) {
            throw new NullPointerException("name == null");
        }
        JobSchedulerThread jobSchedulerThread = new JobSchedulerThread(str, z);
        this.impl = jobSchedulerThread;
        this.finalizer = new FinalizerHelper(jobSchedulerThread);
    }

    public JobScheduler(boolean z) {
        this("Timer-" + JobSchedulerClock.nextId(), z);
    }

    private void scheduleImpl(Job job, long j) {
        synchronized (this.impl) {
            if (this.impl.isCancelled()) {
                throw new IllegalStateException("Timer was canceled");
            }
            long currentTime = JobSchedulerClock.getCurrentTime() + j;
            if (currentTime < 0) {
                throw new IllegalArgumentException("Illegal delay to start the TimerTask: " + currentTime);
            }
            JobSchedulerTaskWrapper jobSchedulerTaskWrapper = new JobSchedulerTaskWrapper();
            jobSchedulerTaskWrapper.type = job.type;
            jobSchedulerTaskWrapper.job = job;
            jobSchedulerTaskWrapper.when = currentTime;
            this.impl.insertTask(jobSchedulerTaskWrapper);
        }
    }

    public void executeJobDelayed(Job job, long j) {
        if (j < 0) {
            throw new IllegalArgumentException("delay < 0: " + j);
        }
        scheduleImpl(job, j);
    }

    public void executeJobNow(Job job) {
        if (MyLog.getLogLevel() >= 1 || Thread.currentThread() == this.impl) {
            job.run();
            return;
        }
        MyLog.e("run job outside job job thread");
        throw new RejectedExecutionException("Run job outside job thread");
    }

    public boolean hasJob(int i) {
        boolean hasJob;
        synchronized (this.impl) {
            hasJob = this.impl.tasks().hasJob(i);
        }
        return hasJob;
    }

    public boolean hasJob(int i, Job job) {
        boolean hasJob;
        synchronized (this.impl) {
            hasJob = this.impl.tasks().hasJob(i, job);
        }
        return hasJob;
    }

    public boolean isBlocked() {
        return this.impl.isBlocked();
    }

    public int purge() {
        int purge;
        synchronized (this.impl) {
            purge = this.impl.purge();
        }
        return purge;
    }

    public void quit() {
        MyLog.w("quit. finalizer:" + this.finalizer);
        this.impl.cancelScheduler();
    }

    public void removeAllJobs() {
        synchronized (this.impl) {
            this.impl.tasks().reset();
        }
    }

    public void removeJobs(int i) {
        synchronized (this.impl) {
            this.impl.tasks().removeJobs(i);
        }
    }

    public void removeJobs(int i, Job job) {
        synchronized (this.impl) {
            this.impl.tasks().removeJobs(i, job);
        }
    }
}
