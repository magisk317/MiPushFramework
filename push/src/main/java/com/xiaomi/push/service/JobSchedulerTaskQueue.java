package com.xiaomi.push.service;

final class JobSchedulerTaskQueue {
    private final int defaultHeapSize = 256;
    private int deletedCancelledNumber;
    private int size;
    private JobSchedulerTaskWrapper[] timers = new JobSchedulerTaskWrapper[this.defaultHeapSize];

    private void downHeap(int i) {
        int i2 = i;
        int i3 = (i2 * 2) + 1;
        while (true) {
            int i4 = this.size;
            if (i3 >= i4 || i4 <= 0) {
                return;
            }
            int i5 = i3;
            if (i3 + 1 < i4 && this.timers[i3 + 1].when < this.timers[i3].when) {
                i5 = i3 + 1;
            }
            if (this.timers[i2].when < this.timers[i5].when) {
                return;
            }
            JobSchedulerTaskWrapper[] jobSchedulerTaskWrapperArr = this.timers;
            JobSchedulerTaskWrapper jobSchedulerTaskWrapper = jobSchedulerTaskWrapperArr[i2];
            jobSchedulerTaskWrapperArr[i2] = jobSchedulerTaskWrapperArr[i5];
            jobSchedulerTaskWrapperArr[i5] = jobSchedulerTaskWrapper;
            i3 = (i5 * 2) + 1;
            i2 = i5;
        }
    }

    int getTask(JobSchedulerTaskWrapper jobSchedulerTaskWrapper) {
        for (int i = 0; i < this.timers.length; i++) {
            if (this.timers[i] == jobSchedulerTaskWrapper) {
                return i;
            }
        }
        return -1;
    }

    private void upHeap() {
        int i = this.size - 1;
        int i2 = (i - 1) / 2;
        while (this.timers[i].when < this.timers[i2].when) {
            JobSchedulerTaskWrapper[] jobSchedulerTaskWrapperArr = this.timers;
            JobSchedulerTaskWrapper jobSchedulerTaskWrapper = jobSchedulerTaskWrapperArr[i];
            jobSchedulerTaskWrapperArr[i] = jobSchedulerTaskWrapperArr[i2];
            jobSchedulerTaskWrapperArr[i2] = jobSchedulerTaskWrapper;
            i = i2;
            i2 = (i2 - 1) / 2;
        }
    }

    void adjustMinimum() {
        downHeap(0);
    }

    void delete(int i) {
        if (i >= 0 && i < this.size) {
            JobSchedulerTaskWrapper[] jobSchedulerTaskWrapperArr = this.timers;
            int i2 = this.size - 1;
            this.size = i2;
            jobSchedulerTaskWrapperArr[i] = jobSchedulerTaskWrapperArr[i2];
            jobSchedulerTaskWrapperArr[i2] = null;
            downHeap(i);
        }
    }

    void deleteIfCancelled() {
        int i = 0;
        while (i < this.size) {
            int i2 = i;
            if (this.timers[i].cancelled) {
                this.deletedCancelledNumber++;
                delete(i);
                i2 = i - 1;
            }
            i = i2 + 1;
        }
    }

    boolean hasJob(int i) {
        for (int i2 = 0; i2 < this.size; i2++) {
            if (this.timers[i2].type == i) {
                return true;
            }
        }
        return false;
    }

    boolean hasJob(int i, JobScheduler.Job job) {
        for (int i2 = 0; i2 < this.size; i2++) {
            if (this.timers[i2].job == job) {
                return true;
            }
        }
        return false;
    }

    void insert(JobSchedulerTaskWrapper jobSchedulerTaskWrapper) {
        JobSchedulerTaskWrapper[] jobSchedulerTaskWrapperArr = this.timers;
        int length = jobSchedulerTaskWrapperArr.length;
        int i = this.size;
        if (length == i) {
            JobSchedulerTaskWrapper[] jobSchedulerTaskWrapperArr2 = new JobSchedulerTaskWrapper[i * 2];
            System.arraycopy(jobSchedulerTaskWrapperArr, 0, jobSchedulerTaskWrapperArr2, 0, i);
            this.timers = jobSchedulerTaskWrapperArr2;
        }
        JobSchedulerTaskWrapper[] jobSchedulerTaskWrapperArr3 = this.timers;
        int i2 = this.size;
        this.size = i2 + 1;
        jobSchedulerTaskWrapperArr3[i2] = jobSchedulerTaskWrapper;
        upHeap();
    }

    boolean isEmpty() {
        return this.size == 0;
    }

    JobSchedulerTaskWrapper minimum() {
        return this.timers[0];
    }

    int purge() {
        if (isEmpty()) {
            return 0;
        }
        this.deletedCancelledNumber = 0;
        deleteIfCancelled();
        return this.deletedCancelledNumber;
    }

    void removeJobs(int i) {
        for (int i2 = 0; i2 < this.size; i2++) {
            if (this.timers[i2].type == i) {
                this.timers[i2].cancel();
            }
        }
        deleteIfCancelled();
    }

    void removeJobs(int i, JobScheduler.Job job) {
        for (int i2 = 0; i2 < this.size; i2++) {
            if (this.timers[i2].job == job) {
                this.timers[i2].cancel();
            }
        }
        deleteIfCancelled();
    }

    void reset() {
        this.timers = new JobSchedulerTaskWrapper[this.defaultHeapSize];
        this.size = 0;
    }
}
