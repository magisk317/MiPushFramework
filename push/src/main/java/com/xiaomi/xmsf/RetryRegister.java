package com.xiaomi.xmsf;

import static com.xiaomi.xmsf.push.control.PushControllerUtils.pushRegistered;

import static top.trumeet.common.Constants.APP_ID;
import static top.trumeet.common.Constants.APP_KEY;

import android.content.Context;

import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.mipush.sdk.MiPushClient;
import com.xiaomi.xmsf.push.control.PushControllerUtils;

public class RetryRegister implements Runnable {

    final int tryRegisterCount;

    final  Context context;

    public RetryRegister(Context context, int i) {
        this.context = context;
        this.tryRegisterCount = i;
    }

    @Override
    public void run() {
        if (pushRegistered(this.context)) {
            MyLog.i("register successed, stop retry");
            return;
        }
        MiPushClient.registerPush(this.context, APP_ID, APP_KEY);
        int tryRegisterCount = this.tryRegisterCount + 1;
        if (tryRegisterCount <= 10) {
            MyLog.i("register not successed, register again, retryIndex: " + tryRegisterCount);
            PushControllerUtils.registerPush(this.context, tryRegisterCount);
            return;
        }
        MyLog.i("register not successed, but retry to many times, stop retry");
    }
}
