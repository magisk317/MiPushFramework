package com.xiaomi.common.logger.thrift.mfs

class LandNodeInfo {
    private var ip: String? = null
    private var expInfo: Map<String, Int>? = null
    private var successCount = 0
    private var failedCount = 0
    private var duration = 0L
    private var size = 0

    fun setIp(ip: String?) {
        this.ip = ip
    }

    fun setExp_info(expInfo: Map<String, Int>?) {
        this.expInfo = expInfo
    }

    fun setSuccess_count(successCount: Int) {
        this.successCount = successCount
    }

    fun setFailed_count(failedCount: Int) {
        this.failedCount = failedCount
    }

    fun setDuration(duration: Long) {
        this.duration = duration
    }

    fun setSize(size: Int) {
        this.size = size
    }
}
