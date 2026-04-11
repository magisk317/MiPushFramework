package com.xiaomi.common.logger.thrift.mfs;

import java.util.Map;

public class LandNodeInfo {
    private String ip;
    private Map<String, Integer> expInfo;
    private int successCount;
    private int failedCount;
    private long duration;
    private int size;

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setExp_info(Map<String, Integer> expInfo) {
        this.expInfo = expInfo;
    }

    public void setSuccess_count(int successCount) {
        this.successCount = successCount;
    }

    public void setFailed_count(int failedCount) {
        this.failedCount = failedCount;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
