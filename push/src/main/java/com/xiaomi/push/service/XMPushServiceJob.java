package com.xiaomi.push.service;

import com.xiaomi.channel.commonutils.logger.LogTag;
import com.xiaomi.channel.commonutils.logger.MyLog;

abstract class XMPushServiceJob extends JobScheduler.Job {
    static final int TYPE_BIND_TIMEOUT = 12;
    static final int TYPE_BIND_UNBIND = 9;
    static final int TYPE_CLEAR_ACCOUNT_CACHE = 14;
    static final int TYPE_CONNECT = 1;
    static final int TYPE_CONNECTING_TIMEOUT = 10;
    static final int TYPE_DISCONNECT = 2;
    static final int TYPE_HANDLE_INTENT = 15;
    static final int TYPE_INIT = 65535;
    static final int TYPE_MAX = 16;
    static final int TYPE_MIN = 1;
    static final int TYPE_NOTYPE_JOB = 0;
    static final int TYPE_PING_TIMEOUT = 13;
    static final int TYPE_PREPARE_MIPUSH_ACCOUNT = 11;
    static final int TYPE_QUIT = 5;
    static final int TYPE_RECEIVE_CHALLENGE = 7;
    static final int TYPE_RECEIVE_MSG = 8;
    static final int TYPE_RECEIVE_TIMEOUT = 6;
    static final int TYPE_RESET_CONNECT = 3;
    static final int TYPE_SEND_MSG = 4;

    XMPushServiceJob(int i) {
        super(i);
    }

    public abstract String getDesc();

    public abstract void process();

    @Override
    public void run() {
        if (this.type != TYPE_SEND_MSG && this.type != TYPE_RECEIVE_MSG) {
            MyLog.w(LogTag.TAG_JOB, getDesc());
        }
        process();
    }
}
