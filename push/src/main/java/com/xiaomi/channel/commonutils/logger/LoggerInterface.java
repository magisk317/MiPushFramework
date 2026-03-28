package com.xiaomi.channel.commonutils.logger;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/logger/LoggerInterface.class */
public interface LoggerInterface {
    void log(String str);

    void log(String str, Throwable th);

    void setTag(String str);
}
