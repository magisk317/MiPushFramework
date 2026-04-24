package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

class JobSchedulerTaskQueue {
    private val defaultHeapSize = 256
    private var deletedCancelledNumber = 0
    private var size = 0
    private var timers = arrayOfNulls<JobSchedulerTaskWrapper>(defaultHeapSize)

    private fun timerAt(index: Int): JobSchedulerTaskWrapper = checkNotNull(timers[index])

    private fun downHeap(index: Int) {
        var currentIndex = index
        var childIndex = currentIndex * 2 + 1
        while (childIndex < size && size > 0) {
            var candidateIndex = childIndex
            if (childIndex + 1 < size && timerAt(childIndex + 1).`when` < timerAt(childIndex).`when`) {
                candidateIndex = childIndex + 1
            }
            if (timerAt(currentIndex).`when` < timerAt(candidateIndex).`when`) {
                return
            }
            val current = timerAt(currentIndex)
            timers[currentIndex] = timers[candidateIndex]
            timers[candidateIndex] = current
            currentIndex = candidateIndex
            childIndex = currentIndex * 2 + 1
        }
    }

    fun getTask(taskWrapper: JobSchedulerTaskWrapper): Int {
        for (index in timers.indices) {
            if (timers[index] === taskWrapper) {
                return index
            }
        }
        return -1
    }

    private fun upHeap() {
        var index = size - 1
        var parent = (index - 1) / 2
        while (timers[index] != null && timers[parent] != null && timerAt(index).`when` < timerAt(parent).`when`) {
            val current = timerAt(index)
            timers[index] = timers[parent]
            timers[parent] = current
            index = parent
            parent = (index - 1) / 2
        }
    }

    fun adjustMinimum() {
        downHeap(0)
    }

    fun delete(index: Int) {
        if (index in 0 until size) {
            val lastIndex = size - 1
            size = lastIndex
            timers[index] = timers[lastIndex]
            timers[lastIndex] = null
            downHeap(index)
        }
    }

    fun deleteIfCancelled() {
        var index = 0
        while (index < size) {
            var nextIndex = index
            if (timerAt(index).cancelled) {
                deletedCancelledNumber++
                delete(index)
                nextIndex = index - 1
            }
            index = nextIndex + 1
        }
    }

    fun hasJob(type: Int): Boolean {
        for (index in 0 until size) {
            if (timerAt(index).type == type) {
                return true
            }
        }
        return false
    }

    fun hasJob(type: Int, job: JobScheduler.Job): Boolean {
        for (index in 0 until size) {
            if (timerAt(index).job == job) {
                return true
            }
        }
        return false
    }

    fun insert(taskWrapper: JobSchedulerTaskWrapper) {
        if (timers.size == size) {
            val expanded = arrayOfNulls<JobSchedulerTaskWrapper>(size * 2)
            System.arraycopy(timers, 0, expanded, 0, size)
            timers = expanded
        }
        timers[size] = taskWrapper
        size += 1
        upHeap()
    }

    fun isEmpty(): Boolean = size == 0

    fun minimum(): JobSchedulerTaskWrapper = timerAt(0)

    fun purge(): Int {
        if (isEmpty()) {
            return 0
        }
        deletedCancelledNumber = 0
        deleteIfCancelled()
        return deletedCancelledNumber
    }

    fun removeJobs(type: Int) {
        for (index in 0 until size) {
            if (timerAt(index).type == type) {
                timerAt(index).cancel()
            }
        }
        deleteIfCancelled()
    }

    fun removeJobs(type: Int, job: JobScheduler.Job) {
        for (index in 0 until size) {
            if (timerAt(index).job == job) {
                timerAt(index).cancel()
            }
        }
        deleteIfCancelled()
    }

    fun reset() {
        timers = arrayOfNulls(defaultHeapSize)
        size = 0
    }
}
