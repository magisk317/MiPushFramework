package com.xiaomi.push.log;

import com.xiaomi.channel.commonutils.logger.LoggerInterface;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/log/MIPushDebugLog.class */
public class MIPushDebugLog implements LoggerInterface {
    private LoggerInterface sPushLogFileInterface;
    private LoggerInterface sUserLogInterface;

    public MIPushDebugLog(LoggerInterface loggerInterface, LoggerInterface loggerInterface2) {
        this.sUserLogInterface = null;
        this.sPushLogFileInterface = null;
        this.sUserLogInterface = loggerInterface;
        this.sPushLogFileInterface = loggerInterface2;
    }

    @Override // com.xiaomi.channel.commonutils.logger.LoggerInterface
    public void log(String str) {
        LoggerInterface loggerInterface = this.sUserLogInterface;
        if (loggerInterface != null) {
            loggerInterface.log(str);
        }
        LoggerInterface loggerInterface2 = this.sPushLogFileInterface;
        if (loggerInterface2 != null) {
            loggerInterface2.log(str);
        }
    }

    @Override // com.xiaomi.channel.commonutils.logger.LoggerInterface
    public void log(String str, Throwable th) {
        LoggerInterface loggerInterface = this.sUserLogInterface;
        if (loggerInterface != null) {
            loggerInterface.log(str, th);
        }
        LoggerInterface loggerInterface2 = this.sPushLogFileInterface;
        if (loggerInterface2 != null) {
            loggerInterface2.log(str, th);
        }
    }

    @Override // com.xiaomi.channel.commonutils.logger.LoggerInterface
    public void setTag(String str) {
    }
}
