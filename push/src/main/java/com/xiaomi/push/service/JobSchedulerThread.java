package com.xiaomi.push.service;

import android.os.SystemClock;

final class JobSchedulerThread extends Thread {
    private static final int MAX_SLEEP_TIME = 500;
    private static final int SLEEP_TIME_STEP = 50;
    private boolean cancelled;
    private boolean finished;
    private volatile long lastJob = 0;
    private volatile boolean executing = false;
    private long currentSleepDuration = 50;
    private final JobSchedulerTaskQueue tasks = new JobSchedulerTaskQueue();

    JobSchedulerThread(String str, boolean z) {
        setName(str);
        setDaemon(z);
        start();
    }

    void insertTask(JobSchedulerTaskWrapper jobSchedulerTaskWrapper) {
        this.tasks.insert(jobSchedulerTaskWrapper);
        notify();
    }

    void cancelScheduler() {
        synchronized (this) {
            this.cancelled = true;
            this.tasks.reset();
            notify();
        }
    }

    boolean isBlocked() {
        return this.executing && SystemClock.uptimeMillis() - this.lastJob > 600000;
    }

    int purge() {
        return this.tasks.purge();
    }

    JobSchedulerTaskQueue tasks() {
        return this.tasks;
    }

    void finish() {
        synchronized (this) {
            this.finished = true;
            notify();
        }
    }

    boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void run() {
        while (true) {
            try {
                JobSchedulerTaskWrapper jobSchedulerTaskWrapper = null;
                synchronized (this) {
                    if (this.cancelled) {
                        return;
                    }
                    if (this.tasks.isEmpty()) {
                        if (this.finished) {
                            return;
                        }
                        try {
                            wait();
                        } catch (InterruptedException e) {
                        }
                        continue;
                    }
                    long currentTime = JobSchedulerClock.getCurrentTime();
                    jobSchedulerTaskWrapper = this.tasks.minimum();
                    synchronized (jobSchedulerTaskWrapper.lock) {
                        if (jobSchedulerTaskWrapper.cancelled) {
                            this.tasks.delete(0);
                            continue;
                        }
                        long j = jobSchedulerTaskWrapper.when - currentTime;
                        if (j > 0) {
                            long j2 = this.currentSleepDuration;
                            if (j > j2) {
                                j = j2;
                            }
                            long j3 = j2 + SLEEP_TIME_STEP;
                            this.currentSleepDuration = j3;
                            if (j3 > MAX_SLEEP_TIME) {
                                this.currentSleepDuration = MAX_SLEEP_TIME;
                            }
                            try {
                                wait(j);
                            } catch (InterruptedException e2) {
                            }
                            continue;
                        }
                        this.currentSleepDuration = SLEEP_TIME_STEP;
                        int task = 0;
                        if (this.tasks.minimum().when != jobSchedulerTaskWrapper.when) {
                            task = this.tasks.getTask(jobSchedulerTaskWrapper);
                        }
                        if (jobSchedulerTaskWrapper.cancelled) {
                            this.tasks.delete(this.tasks.getTask(jobSchedulerTaskWrapper));
                            continue;
                        }
                        jobSchedulerTaskWrapper.setScheduledTime(jobSchedulerTaskWrapper.when);
                        this.tasks.delete(task);
                        jobSchedulerTaskWrapper.when = 0L;
                    }
                }
                this.lastJob = SystemClock.uptimeMillis();
                this.executing = true;
                jobSchedulerTaskWrapper.job.run();
                this.executing = false;
            } catch (Throwable th) {
                synchronized (this) {
                    this.cancelled = true;
                }
                throw th;
            }
        }
    }
}
