package com.xiaomi.push.service;

final class JobSchedulerTaskWrapper {
    boolean cancelled;
    JobScheduler.Job job;
    final Object lock = new Object();
    private long scheduledTime;
    int type;
    long when;

    boolean cancel() {
        boolean z;
        synchronized (this.lock) {
            z = !this.cancelled && this.when > 0;
            this.cancelled = true;
        }
        return z;
    }

    long getWhen() {
        long j;
        synchronized (this.lock) {
            j = this.when;
        }
        return j;
    }

    long scheduledExecutionTime() {
        long j;
        synchronized (this.lock) {
            j = this.scheduledTime;
        }
        return j;
    }

    void setScheduledTime(long j) {
        synchronized (this.lock) {
            this.scheduledTime = j;
        }
    }
}
